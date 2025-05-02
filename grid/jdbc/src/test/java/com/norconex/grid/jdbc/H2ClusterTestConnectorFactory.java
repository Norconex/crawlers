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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.cluster.ClusterConnectorFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class H2ClusterTestConnectorFactory
        implements ClusterConnectorFactory {

    @Override
    public GridConnector create(String gridName, String nodeName) {
        var tempDir =
                FileUtils.getTempDirectory().toPath().resolve("gridName");
        try {
            Files.createDirectories(tempDir);
            var connStr = ("jdbc:h2:file:" + StringUtils.removeStart(
                    tempDir.toUri().toURL() + gridName, "file:/"));
            //+ ";TRACE_LEVEL_FILE=4";//;LOCK_TIMEOUT=10000;LOCK_MODE=3";
            LOG.info("Connecting to a grid backed by H2: {}", connStr);
            return Configurable.configure(new JdbcGridConnector(), cfg -> {
                cfg.getDatasource().add("jdbcUrl", connStr);
                cfg.setGridName(gridName);
                cfg.setNodeName(nodeName);
            });
        } catch (MalformedURLException e) {
            fail("Count not create JDBC connection.", e);
            return null;
        } catch (IOException e) {
            fail("Could not create temporary directory", e);
            return null;
        } finally {
            if (tempDir != null) {
                try {
                    FileUtils.deleteDirectory(tempDir.toFile());
                } catch (IOException e) {
                    LOG.error("Could not delete temporary directory.");
                }
            }
        }
    }
}
