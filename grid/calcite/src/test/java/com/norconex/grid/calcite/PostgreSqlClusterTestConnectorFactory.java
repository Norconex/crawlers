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

import java.sql.DriverManager;
import java.sql.SQLException;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.base.Objects;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.grid.core.GridConnectionContext;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.cluster.ClusterConnectorFactory;
import com.norconex.grid.core.impl.CoreClusterTestProtocols;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgreSqlClusterTestConnectorFactory
        implements ClusterConnectorFactory {

    // Set by test class
    static PostgreSQLContainer<?> postgres;

    // Quick hack to make sure we do not recreate the same DB. only works in
    // test.
    private static String lastCreatedDb;

    @Override
    public GridConnector create(GridConnectionContext ctx, String nodeName) {

        synchronized (this) {
            if (!Objects.equal(lastCreatedDb, ctx.getGridName())) {
                try (var conn = DriverManager.getConnection(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword());
                        var stmt = conn.createStatement()) {
                    stmt.execute("CREATE DATABASE " + ctx.getGridName());
                    lastCreatedDb = ctx.getGridName();
                } catch (SQLException e) {
                    if (!e.getMessage().contains("already exists")) {
                        throw new GridException(e);
                    }
                    LOG.debug("Database already exists: " + ctx.getGridName());
                }
            }
        }

        var jdbcUrl = postgres.getJdbcUrl()
                .replace("/test", "/" + ctx.getGridName());
        return Configurable.configure(new CalciteGridConnector(), cfg -> {
            cfg.setAdapterType("jdbc");
            cfg.setAdapterProperties(MapUtil.toMap(
                    "jdbcUrl", jdbcUrl,
                    "username", postgres.getUsername(),
                    "password", postgres.getPassword(),
                    "datasource.leakDetectionThreshold", "5000",
                    "datasource.maximumPoolSize", "10",
                    "datasource.connectionTimeout", "30000"));
            cfg.setGridName(ctx.getGridName());
            cfg.setNodeName(nodeName);
            cfg.setMultiTenancyMode(MultiTenancyMode.DATABASE);
            cfg.setProtocols(CoreClusterTestProtocols.createProtocols());
        });
    }
}
