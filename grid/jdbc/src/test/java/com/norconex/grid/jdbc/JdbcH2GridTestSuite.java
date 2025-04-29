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

import java.net.MalformedURLException;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridTestSuite;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class JdbcH2GridTestSuite extends GridTestSuite {

    @TempDir
    private Path tempDir;

    @Override
    protected GridConnector getGridConnector(String gridName) {
        try {
            var connStr = ("jdbc:h2:file:" + StringUtils.removeStart(
                    tempDir.toUri().toURL() + gridName, "file:/"));
            //+ ";TRACE_LEVEL_FILE=4";//;LOCK_TIMEOUT=10000;LOCK_MODE=3";
            LOG.info("Connecting to a grid backed by H2: {}", connStr);
            return Configurable.configure(new JdbcGridConnector(), cfg -> {
                cfg.getDatasource().add("jdbcUrl", connStr);
                cfg.setGridName(gridName);
            });
        } catch (MalformedURLException e) {
            fail("Count not create JDBC connection.", e);
            return null;
        }
    }
}
