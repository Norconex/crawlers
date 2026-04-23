/* Copyright 2026 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.core.cmd.stop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.admin.ClusterAdminServer;
import com.norconex.crawler.core.util.ConfigUtil;

class StopCommandTest {

    @TempDir
    private Path tempDir;

    @Test
    void resolveNodeUrls_fallsBackToConfiguredPortScanWhenPortFileMissing()
            throws Exception {
        var config = crawlConfig(28100);

        var urls = resolveNodeUrls(config);

        assertThat(urls).hasSize(100);
        assertThat(urls.get(0)).isEqualTo("http://localhost:28100");
        assertThat(urls.get(99)).isEqualTo("http://localhost:28199");
    }

    @Test
    void resolveNodeUrls_prioritizesPortFileBeforeScanWithoutDuplicates()
            throws Exception {
        var config = crawlConfig(28100);
        var workDir = ConfigUtil.resolveWorkDir(config);
        Files.createDirectories(workDir);
        Files.writeString(workDir.resolve(ClusterAdminServer.ADMIN_PORT_FILE),
                "28102");

        var urls = resolveNodeUrls(config);

        assertThat(urls.get(0)).isEqualTo("http://localhost:28102");
        assertThat(urls).contains("http://localhost:28100");
        assertThat(urls).contains("http://localhost:28199");
        assertThat(urls.stream().filter("http://localhost:28102"::equals))
                .hasSize(1);
        assertThat(urls).hasSize(100);
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveNodeUrls(CrawlConfig config) throws Exception {
        var command = new StopCommand(config, new String[0]);
        Method method = StopCommand.class.getDeclaredMethod("resolveNodeUrls");
        method.setAccessible(true);
        return (List<String>) method.invoke(command);
    }

    private CrawlConfig crawlConfig(int adminPort) {
        var config = new CrawlConfig();
        config.setId("stop-command-test");
        config.setWorkDir(tempDir);
        config.getClusterConfig().setAdminPort(adminPort);
        return config;
    }
}
