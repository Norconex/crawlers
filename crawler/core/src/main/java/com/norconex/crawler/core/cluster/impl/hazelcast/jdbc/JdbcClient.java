/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;

import com.hazelcast.config.DataConnectionConfig;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.ClusterException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class JdbcClient {

    @FunctionalInterface
    public interface SQLTask {
        void run(Connection conn) throws SQLException;
    }

    public static final String PROP_TABLE_NAME = "table-name";
    public static final String PROP_DATA_CONN_REF = "data-connection-ref";

    private final DataSource dataSource;

    public JdbcClient(HazelcastInstance hz, Properties storeProps) {
        dataSource = createOrGetDataSource(hz, storeProps);
    }

    /**
     * Ensures the table exists, using a cross-database approach:
     * <ol>
     *   <li>Try CREATE TABLE ... IF NOT EXISTS</li>
     *   <li>If that fails, try CREATE TABLE ...</li>
     *   <li>If that fails, wait and check if the table now exists</li>
     * </ol>
     * @param tableName Table name
     * @param columnDefs Table column definitions
     * @throws SQLException if table cannot be created or found
     */
    public synchronized void ensureTableExists(
            @NonNull String tableName,
            @NonNull List<String> columnDefs)
            throws SQLException {

        try (var conn = dataSource.getConnection()) {
            // Double-check after acquiring lock
            if (doTableExists(conn, tableName)) {
                LOG.debug("Table '{}' already exists.", tableName);
                return;
            }

            // 1. Try CREATE TABLE ... IF NOT EXISTS
            var sqlIfNotExists =
                    "CREATE TABLE IF NOT EXISTS \"%s\" (%s)".formatted(
                            tableName, String.join(", ", columnDefs));
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate(sqlIfNotExists);
                LOG.info("Created table (IF NOT EXISTS): {}", tableName);
                return;
            } catch (SQLException e) {
                LOG.debug("CREATE TABLE IF NOT EXISTS failed: {}",
                        e.getMessage());
            }

            // 2. Try plain CREATE TABLE
            var sqlPlain = "CREATE TABLE \"%s\" (%s)".formatted(
                    tableName, String.join(", ", columnDefs));
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate(sqlPlain);
                LOG.info("Created table: {}", tableName);
                return;
            } catch (SQLException e) {
                LOG.debug("Plain CREATE TABLE failed: {}", e.getMessage());
            }

            // 3. Final check - table might have been created by another node
            // between attempts
            if (doTableExists(conn, tableName)) {
                LOG.info("Table '{}' exists (created by another node).",
                        tableName);
                return;
            }
        }

        throw new SQLException("Could not create or find table: " + tableName);
    }

    public boolean tableExists(String tableName)
            throws SQLException {
        try (var conn = dataSource.getConnection()) {
            return doTableExists(conn, tableName);
        }
    }

    public Connection getConnection() throws SQLException {
        try {
            if (dataSource instanceof HikariDataSource hds) {
                LOG.debug(
                        "Getting connection from HikariDataSource: jdbcUrl='{}', driver='{}'",
                        hds.getJdbcUrl(), hds.getDriverClassName());
            } else {
                LOG.debug("Getting connection from DataSource of type: {}",
                        dataSource.getClass().getName());
            }
            return dataSource.getConnection();
        } catch (SQLException e) {
            try {
                if (dataSource instanceof HikariDataSource hds) {
                    LOG.error(
                            "Connection attempt failed for HikariDataSource: jdbcUrl='{}', driver='{}'",
                            hds.getJdbcUrl(), hds.getDriverClassName());
                } else {
                    LOG.error(
                            "Connection attempt failed for DataSource of type: {}",
                            dataSource.getClass().getName());
                }
            } catch (Exception ex) {
                LOG.debug("Could not extract DataSource details: {}",
                        ex.toString());
            }
            throw e;
        }
    }

    public void executeInTransaction(SQLTask task) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            task.run(conn);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new ClusterException("Transaction failed", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    //NOOP
                }
            }
        }
    }

    //--- Private methods ------------------------------------------------------

    private static DataSource createOrGetDataSource(
            HazelcastInstance hz, Properties storeProps) {

        var hzConf = hz.getConfig();
        var dbRef = storeProps.getProperty(PROP_DATA_CONN_REF);
        LOG.debug("Using Hazelcast data connection ref: {}", dbRef);
        DataConnectionConfig dbConf = null;
        if (StringUtils.isNotBlank(dbRef)) {
            dbConf = hzConf.getDataConnectionConfig(dbRef);
            if (dbConf == null) {
                throw new ClusterException("Store references a data connection "
                        + "that does not exist: " + dbRef);
            }
        }
        if (dbConf == null) {
            if (hzConf.getDataConnectionConfigs().size() != 1) {
                throw new ClusterException("data-connection-ref property must "
                        + "be specified or exactly one data-connection.");
            }
            dbConf = hz.getConfig().getDataConnectionConfigs().values()
                    .iterator().next();
        }
        dbRef = dbConf.getName();

        // Important: Hazelcast may populate properties with a key present but
        // a null value (e.g., driverClassName). Passing such Properties to
        // Hikari's reflective PropertyElf causes:
        // "Failed to load driver class null".
        // We therefore build the config ourselves and ignore null values.
        var dbProps = dbConf.getProperties();

        return (DataSource) hz.getUserContext().computeIfAbsent(dbRef, k -> {
            var hikariConfig = new HikariConfig(dbProps);

            LOG.debug("Creating Hikari data source for jdbcUrl='{}', "
                    + "driver='{}'",
                    hikariConfig.getJdbcUrl(),
                    hikariConfig.getDriverClassName());
            return new HikariDataSource(hikariConfig);
        });
    }

    private static boolean doTableExists(Connection conn, String tableName)
            throws SQLException {
        if (StringUtils.isBlank(tableName)) {
            return false;
        }

        var meta = conn.getMetaData();
        var normalizedTableName = tableName.trim();
        var schema = StringUtils.trimToNull(conn.getSchema());

        // Prefer current schema to avoid false positives from tables with
        // identical names in other schemas.
        if (schema != null) {
            if (tableExists(meta, schema, normalizedTableName)) {
                return true;
            }
            if (tableExists(meta, schema,
                    normalizedTableName.toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (tableExists(meta, schema,
                    normalizedTableName.toUpperCase(Locale.ROOT))) {
                return true;
            }
            return false;
        }

        return tableExists(meta, null, normalizedTableName)
                || tableExists(meta, null,
                        normalizedTableName.toLowerCase(Locale.ROOT))
                || tableExists(meta, null,
                        normalizedTableName.toUpperCase(Locale.ROOT));
    }

    private static boolean tableExists(DatabaseMetaData meta,
            String schemaPattern, String tableName)
            throws SQLException {
        if (StringUtils.isBlank(tableName)) {
            return false;
        }
        try (var rs = meta.getTables(
                null,
                schemaPattern,
                tableName,
                new String[] { "TABLE" })) {
            return rs.next();
        }
    }
}
