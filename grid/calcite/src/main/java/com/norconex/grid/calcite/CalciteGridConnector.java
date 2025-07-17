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
package com.norconex.grid.calcite;

import static java.util.Optional.ofNullable;

import javax.sql.DataSource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.calcite.conn.FallbackConnectionProvider;
import com.norconex.grid.calcite.conn.JdbcConnectionProvider;
import com.norconex.grid.calcite.db.Db;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnectionContext;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.impl.CoreGrid;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Apache Calcite implementation of Grid storage, acting as a bridge between
 * various storage systems (JDBC, NoSQL, etc). Only Calcite adapters
 * with descent DDL support are supported.
 * </p>
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class CalciteGridConnector
        implements GridConnector,
        Configurable<CalciteGridConnectorConfig> {

    @Getter
    private final CalciteGridConnectorConfig configuration =
            new CalciteGridConnectorConfig();

    @JsonIgnore
    private DataSource dataSource;

    @Override
    public Grid connect(GridConnectionContext ctx) {
        try {
            String type =
                    ofNullable(configuration.getAdapterType()).orElse("jdbc");
            var sqlConnProvider = type.equalsIgnoreCase("jdbc")
                    ? new JdbcConnectionProvider(configuration, ctx)
                    : new FallbackConnectionProvider(configuration, ctx);
            String namespace = ofNullable(configuration.getGridName())
                    .orElse(ctx.getGridName());
            var storage = new CalciteGridStorage(new Db(
                    sqlConnProvider,
                    configuration.getMultiTenancyMode(),
                    namespace));
            var grid = new CoreGrid(configuration, storage);
            storage.init();
            LOG.info("Connected to JDBC-backed Grid.");
            return grid;
        } catch (Exception e) {
            throw new GridException("Could not connect to (JDBC) database.", e);
        }
    }

    @Override
    public void shutdownGrid(GridConnectionContext ctx) {
        try (var grid = connect(ctx)) {
            grid.stop();
        }
    }
}
