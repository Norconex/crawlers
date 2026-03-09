/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;

@Timeout(30)
class ConfigUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveWorkDir_usesDefaultWhenNotSet() {
        var config = new CrawlConfig();
        config.setId("my-crawler");

        var workDir = ConfigUtil.resolveWorkDir(config);

        assertThat(workDir).isNotNull();
        assertThat(workDir.toString()).contains("my-crawler");
    }

    @Test
    void resolveWorkDir_usesExplicitWorkDir() {
        var config = new CrawlConfig();
        config.setId("my-crawler");
        config.setWorkDir(tempDir);

        var workDir = ConfigUtil.resolveWorkDir(config);

        assertThat(workDir.toAbsolutePath().toString())
                .startsWith(tempDir.toAbsolutePath().toString());
        assertThat(workDir.toString()).contains("my-crawler");
    }

    @Test
    void resolveTempDir_isSubdirOfWorkDir() {
        var config = new CrawlConfig();
        config.setId("crawler-1");
        config.setWorkDir(tempDir);

        var tempDirResolved = ConfigUtil.resolveTempDir(config);
        var workDir = ConfigUtil.resolveWorkDir(config);

        assertThat(tempDirResolved.toAbsolutePath().toString())
                .startsWith(workDir.toAbsolutePath().toString());
        assertThat(tempDirResolved.getFileName().toString()).isEqualTo("temp");
    }

    @Test
    void resolveWorkDir_sanitizesSpecialCharsInId() {
        var config = new CrawlConfig();
        config.setId("crawler with spaces/and:special");

        var workDir = ConfigUtil.resolveWorkDir(config);

        assertThat(workDir).isNotNull();
        // Path should not contain literal slashes in filename
        assertThat(workDir.getFileName().toString())
                .doesNotContain("/");
    }
}
