/* Copyright 2024-2025 Norconex Inc.
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

import static com.norconex.commons.lang.text.StringUtil.ifNotBlank;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.impl.CoreGrid;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * JDBC implementation of Grid storage.
 * </p>
 * <h3>Database JDBC driver</h3>
 * <p>
 * To use this grid storage implementation, you need its JDBC database driver
 * on the classpath. Either add it to your project class path manually or
 * specify its location via configuration.
 * </p>
 * <h3>Database datasource configuration</h3>
 * <p>
 * This JDBC data store engine uses
 * <a href="https://github.com/brettwooldridge/HikariCP">Hikari</a> as the JDBC
 * datasource implementation, which provides efficient connection-pooling.
 * Refer to
 * <a href="https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby">
 * Hikari's documentation</a> for all configuration options.  The Hikari options
 * are passed as-is, via <code>datasource</code> properties as shown below.
 * </p>
 * <h3>Data types</h3>
 * <p>
 * This class only use a few data types to store its data in a generic way.
 * It will try to detect what data type to use for your database. If you
 * get errors related to field data types not being supported, you have
 * the option to explicitly define them.
 * </p>
 */
@EqualsAndHashCode
@ToString
@Slf4j
@RequiredArgsConstructor
public class JdbcGridConnector
        implements GridConnector,
        Configurable<JdbcGridConnectorConfig> {

    @Getter
    private final JdbcGridConnectorConfig configuration =
            new JdbcGridConnectorConfig();

    @Override
    public Grid connect(Path workDir) {
        // create data source
        var dataSource = new HikariDataSource(
                new HikariConfig(configuration.getDatasource().toProperties()));
        try {
            var storage = new JdbcGridStorage(resolveDbAdapter(dataSource));
            var grid = new CoreGrid(configuration, storage);
            storage.init(grid);
            return grid;
        } catch (Exception e) {
            throw new GridException("Could not connect to (JDBC) database.", e);
        }
    }

    private DbAdapter resolveDbAdapter(HikariDataSource dataSource) {
        var adapter = DbAdapter.create(StringUtils.firstNonBlank(
                dataSource.getJdbcUrl(), dataSource.getDriverClassName()),
                dataSource);
        ifNotBlank(configuration.getVarcharType(), adapter::varcharType);
        ifNotBlank(configuration.getTextType(), adapter::textType);
        ifNotBlank(configuration.getBigIntType(), adapter::bigIntType);
        return adapter;
    }

    @Override
    public void requestStop(Path workDir) {
        try (var grid = connect(workDir)) {
            grid.stop();
        }
    }
}
