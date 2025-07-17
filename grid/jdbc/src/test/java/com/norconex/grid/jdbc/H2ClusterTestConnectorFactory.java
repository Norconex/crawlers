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
package com.norconex.grid.jdbc;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridConnectionContext;
import com.norconex.grid.core.cluster.ClusterConnectorFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class H2ClusterTestConnectorFactory
        implements ClusterConnectorFactory {

    // Shared based path for temp dirs that is deleted after a test class
    static Path tempBasePath;

    @Override
    public GridConnector create(GridConnectionContext ctx, String nodeName) {
        var tempDir = tempBasePath.resolve("junit-" + ctx.getGridName());
        try {
            Files.createDirectories(tempDir);
            var jdbcUrl = new StringBuilder();
            jdbcUrl.append("jdbc:h2:file:" + StringUtils.removeStart(
                    tempDir.toUri().toURL() + ctx.getGridName(), "file:/"));
            jdbcUrl.append(";LOCK_MODE=1");
            jdbcUrl.append(";WRITE_DELAY=0");
            jdbcUrl.append(";LOCK_TIMEOUT=10000");
            LOG.info("Connecting to a grid backed by H2: {}", jdbcUrl);
            return Configurable.configure(new JdbcGridConnector(), cfg -> {
                cfg.getDatasource().add("jdbcUrl", jdbcUrl.toString());
                cfg.setGridName(ctx.getGridName());
                cfg.setNodeName(nodeName);
                var ds = cfg.getDatasource();
                ds.add("leakDetectionThreshold", "5000");
                ds.add("maximumPoolSize", "10");
                ds.add("connectionTimeout", "30000");
                ds.add("autoCommit", true);

            });
        } catch (MalformedURLException e) {
            fail("Count not create JDBC connection.", e);
            return null;
        } catch (IOException e) {
            fail("Could not create temporary directory", e);
            return null;
        }
    }
}
