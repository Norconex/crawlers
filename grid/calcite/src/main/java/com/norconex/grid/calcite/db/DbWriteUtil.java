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
package com.norconex.grid.calcite.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.util.SerialUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Methods affecting the database or its data.
 */
@Slf4j
class DbWriteUtil {

    private DbWriteUtil() {
    }

    static void dropTableIfExists(Db db, String tableName) {
        try {
            if (db.isRowTenancy()) {
                deleteAllRows(db, tableName);
                LOG.info("Not dropping table in a row-based multi-tenancy "
                        + "setup. Insted, deleted all rows for {} in table: {}",
                        db.getNamespace(), tableName);
            } else {
                executeWrite(db, "DROP TABLE IF EXISTS ?;",
                        ps -> ps.setString(1, tableName));
                LOG.info("Table deleted: {}", tableName);
            }
        } catch (GridException e) {
            // Catch exception and check if already deleted
            Sleeper.sleepMillis(100);
            if (DbReadUtil.tableExists(db, tableName)) {
                throw e;
            }
            LOG.info("Table already deleted: %s".formatted(tableName));
        }
    }

    static void createTableIfNotExists(
            Db db, String createSQL, String tableName) {
        // Do NOT run DDL operations in a transaction. Not all DB will like it.
        try (var conn = db.getConnection()) {
            if (!DbReadUtil.tableExists(db, tableName)) {
                LOG.info("Creating table: {}", tableName);
                doCreateTable(db, conn, createSQL, tableName);
            } else {
                LOG.info("Table found: {}", tableName);
            }
        } catch (SQLException e) {
            throw new GridException("Could not create table " + tableName);
        }
    }

    static void deleteAllRows(Db db, String tableName) {
        if (db.isRowTenancy()) {
            executeWrite(db, "DELETE FROM ? WHERE namespace = ?;",
                    ps -> {
                        ps.setString(1, tableName);
                        ps.setString(2, db.getNamespace());
                    });
        } else {
            executeWrite(db, "DELETE FROM ?;",
                    ps -> ps.setString(1, tableName));
        }
    }

    static boolean deleteById(Db db, String tableName, String id) {
        if (db.isRowTenancy()) {
            return executeWrite(db,
                    "DELETE FROM %s WHERE id = ? AND namespace = ?;"
                            .formatted(tableName),
                    ps -> {
                        ps.setString(1, Db.rightSizeId(id));
                        ps.setString(2, db.getNamespace());
                    }) > 0;
        }
        return executeWrite(db,
                "DELETE FROM %s WHERE id = ?;".formatted(tableName),
                ps -> ps.setString(1, Db.rightSizeId(id))) > 0;
    }

    static boolean insertIfAbsent(Db db, String tableName, String id,
            Object value) {
        String sql = (db.isRowTenancy()
                ? """
                    MERGE INTO %s AS target
                    USING (VALUES (?, ?, ?)) AS source (id, json, namespace)
                    ON target.id = source.id
                    AND target.namespace = source.namespace
                    WHEN NOT MATCHED THEN
                        INSERT (id, json, namespace)
                        VALUES (source.id, source.json, source.namespace);
                    """
                : """
                    MERGE INTO %s AS target
                    USING (VALUES (?, ?)) AS source (id, json)
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id, json) VALUES (source.id, source.json);
                    """).formatted(tableName);

        try {
            return executeWrite(db, sql, stmt -> {
                stmt.setString(1, Db.rightSizeId(id));
                stmt.setString(2, SerialUtil.toJsonString(value));
                if (db.isRowTenancy()) {
                    stmt.setString(3, db.getNamespace());
                }
            }) > 0;
        } catch (GridException e) {
            // it could be a concurrency exception so check if exist
            // already
            Sleeper.sleepMillis(100);
            if ((e.getCause() instanceof SQLException)
                    && DbReadUtil.exists(db, tableName,
                            Db.rightSizeId(id))) {
                return false; // not inserted by us
            }
            throw e;
        }
    }

    static boolean insertIfAbsent(Db db, String tableName, String id) {
        String sql = (db.isRowTenancy()
                ? """
                        MERGE INTO %s AS target
                        USING (VALUES (?, ?)) AS source (id, namespace)
                        ON target.id = source.id
                        AND target.namespace = source.namespace
                        WHEN NOT MATCHED THEN
                            INSERT (id, namespace)
                            VALUES (source.id, source.namespace);
                        """
                : """
                        MERGE INTO %s AS target
                        USING (VALUES (?)) AS source (id)
                        ON target.id = source.id
                        WHEN NOT MATCHED THEN
                            INSERT (id) VALUES (source.id);
                        """).formatted(tableName);

        try {
            return executeWrite(db, sql, stmt -> {
                stmt.setString(1, Db.rightSizeId(id));
                if (db.isRowTenancy()) {
                    stmt.setString(2, db.getNamespace());
                }
            }) > 0;
        } catch (GridException e) {
            // it could be a concurrency exception so check if exist
            // already
            Sleeper.sleepMillis(100);
            if ((e.getCause() instanceof SQLException)
                    && DbReadUtil.exists(
                            db, tableName, Db.rightSizeId(id))) {
                return false; // not inserted by us
            }
            throw e;
        }
    }

    static boolean upsert(Db db, String tableName, String id,
            Object value) {
        try {
            return doUpsertMerge(db, tableName, id, value);
        } catch (Exception e) {
            // Fallback: try UPDATE, then INSERT if no row was updated
            try {
                return doUpsertFallback(db, tableName, id, value);
            } catch (Exception e2) {
                // if the row already exist with the same values, it could mean
                // an new record was concurrently added. We check if so, and
                // return false if that's the case (no change).
                if (Boolean.TRUE.equals(DbReadUtil.executeRead(
                        db,
                        "SELECT 1 FROM %s WHERE id = ? AND json = ?;"
                                .formatted(tableName),
                        ps -> {
                            ps.setString(1, Db.rightSizeId(id));
                            ps.setString(2, SerialUtil.toJsonString(value));
                        },
                        ResultSet::next

                ))) {
                    return false;
                }
                throw e;
            }
        }
    }

    static int executeWrite(
            Db db,
            String sql,
            FailableConsumer<PreparedStatement, SQLException> c) {
        return db.runInTransactionAndReturn(conn -> {
            try (var stmt = conn.prepareStatement(sql)) {
                if (c != null) {
                    c.accept(stmt);
                }
                return stmt.executeUpdate();
            }
        });
    }

    //--- Private methods ------------------------------------------------------

    private static void doCreateTable(
            Db db, Connection conn, String createSQL, String tableName)
            throws SQLException {
        try (var statement = conn.createStatement()) {
            statement.execute(createSQL);
            LOG.info("Table created successfully: {}", tableName);
        } catch (SQLException e) {
            if (!DbReadUtil.tableExists(db, tableName)) {
                throw e;
            }
            LOG.info("Table already exist: {}", tableName);
        }
    }

    private static boolean doUpsertMerge(Db db, String tableName,
            String id,
            Object value) {
        if (db.isRowTenancy()) {
            String sql = """
                MERGE INTO %s AS target
                USING (SELECT ? AS id, ? AS json, ? AS namespace) AS source
                ON target.id = source.id AND target.namespace = source.namespace
                WHEN MATCHED THEN
                UPDATE SET json = source.json
                WHEN NOT MATCHED THEN
                INSERT (id, json, namespace)
                VALUES (source.id, source.json, source.namespace);
                """.formatted(tableName);
            return executeWrite(db, sql, ps -> {
                ps.setString(1, Db.rightSizeId(id));
                ps.setString(2, SerialUtil.toJsonString(value));
                ps.setString(3, db.getNamespace());
            }) > 0;
        }
        String sql = """
                MERGE INTO %s AS target
                USING (SELECT ? AS id, ? AS json) AS source
                ON target.id = source.id
                WHEN MATCHED THEN
                UPDATE SET json = source.json
                WHEN NOT MATCHED THEN
                INSERT (id, json) VALUES (source.id, source.json);
                """.formatted(tableName);
        return executeWrite(db, sql, ps -> {
            ps.setString(1, Db.rightSizeId(id));
            ps.setString(2, SerialUtil.toJsonString(value));
        }) > 0;
    }

    private static boolean doUpsertFallback(Db db,
            String tableName, String id, Object value) {
        var json = SerialUtil.toJsonString(value);
        return db.runInTransactionAndReturn(conn -> {
            var changed = false;
            var updateSQL = (db.isRowTenancy()
                    ? "UPDATE %s SET json = ? "
                            + "WHERE id = ? AND namespace = ? AND json <> ?"
                    : "UPDATE %s SET json = ? WHERE id = ? AND json <> ?")
                            .formatted(tableName);
            try (var updateStmt = conn.prepareStatement(updateSQL)) {
                updateStmt.setString(1, json);
                updateStmt.setString(2, Db.rightSizeId(id));
                if (db.isRowTenancy()) {
                    updateStmt.setString(3, json);
                }
                var rowsUpdated = updateStmt.executeUpdate();
                // If no rows updated, insert instead
                if (rowsUpdated > 0) {
                    changed = true;
                } else if ((rowsUpdated == 0)
                        && !DbReadUtil.exists(db, tableName, id)) {
                    // Row does not exist, perform an INSERT
                    var insertSQL = (db.isRowTenancy()
                            ? "INSERT INTO %s (id, json, namespace) "
                                    + "VALUES (?, ?, ?)"
                            : "INSERT INTO %s (id, json) VALUES (?, ?)")
                                    .formatted(tableName);
                    try (var insertStmt = conn.prepareStatement(insertSQL)) {
                        insertStmt.setString(1, Db.rightSizeId(id));
                        insertStmt.setString(2, json);
                        if (db.isRowTenancy()) {
                            insertStmt.setString(3, db.getNamespace());
                        }
                        insertStmt.executeUpdate();
                    }
                    changed = true;
                }
            }
            return changed;
        });
    }

}
