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

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;

//TODO DELETE?
public class PostgresConnectionInspector {

    private final DataSource dataSource;
    private final String dbName;

    public PostgresConnectionInspector(DataSource dataSource, String jdbcUrl) {
        this.dataSource = dataSource;
        dbName = extractDbName(jdbcUrl);
    }

    private String extractDbName(String jdbcUrl) {
        var afterSlash = StringUtils.substringAfterLast(jdbcUrl, "/");
        return StringUtils.substringBefore(afterSlash, "?");
    }

    public void printOpenConnections() {
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement();
                var result = stmt.executeQuery(
                        "SELECT pid, usename, state, query, backend_start " +
                                "FROM pg_stat_activity WHERE datname = '"
                                + dbName + "'")) {

            System.err.println("Active connections for DB: " + dbName);
            while (result.next()) {
                System.err.println("%s | %s | %s | %s | %s".formatted(
                        result.getString(1),
                        result.getString(2),
                        result.getString(3),
                        result.getString(4),
                        result.getString(5)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
