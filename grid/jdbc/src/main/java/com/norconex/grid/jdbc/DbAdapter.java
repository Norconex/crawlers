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

import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.util.SerialUtil;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Utility wrapper to help with minimal database data type compatibility
 * without having to rely on ORM software.
 * </p>
 * @author Pascal Essiembre
 */
@Slf4j
@Data
@Accessors(fluent = true)
final class DbAdapter {

    private static final int ID_MAX_LENGTH = 2048;

    enum DbType {
        DERBY,
        DB2,
        H2,
        MARIADB,
        MYSQL,
        ORACLE,
        OTHER,
        POSTGRESQL,
        SQLSERVER,
        SYBASE,
    }

    @Getter(value = AccessLevel.NONE)
    private final TransactionManager transactionManager;

    private DbType dbType;
    private String varcharType;
    private String bigIntType;
    private String textType;
    private UnaryOperator<String> escaper;

    private DbAdapter(DataSource dataSource) {
        transactionManager = new TransactionManager(dataSource);
    }

    static DbAdapter create(
            String jdbcUrlOrDataSource, DataSource dataSource) {
        var dbType = detectDbType(jdbcUrlOrDataSource);
        var adapter = new DbAdapter(dataSource)
                .dbType(dbType)
                .varcharType("VARCHAR(%s)".formatted(ID_MAX_LENGTH))
                .bigIntType("BIGINT")
                .textType("TEXT")
                .escaper(v -> esc(v, "\""));
        return switch (dbType) {
            case DERBY, DB2, H2:
                yield adapter
                        .textType("CLOB");
            case MYSQL:
                yield adapter
                        .textType("LONGTEXT")
                        .escaper(v -> esc(v, "`"));
            case ORACLE:
                yield adapter
                        .varcharType("VARCHAR2(%s)".formatted(ID_MAX_LENGTH))
                        .bigIntType("NUMBER(38)")
                        .textType("CLOB");
            case SQLSERVER:
                yield adapter
                        .textType("NTEXT")
                        .escaper(v -> esc(v, "[", "]"));
            case SYBASE:
                yield adapter
                        .escaper(v -> esc(v, "[", "]"));
            default:
                yield adapter; // Others use default
        };
    }

    String esc(@NonNull String tableOrColName) {
        return escaper.apply(tableOrColName);
    }

    boolean isEmpty(String tableName) {
        var sql = switch (dbType) {
            case DB2, DERBY, ORACLE, SQLSERVER, SYBASE:
                yield "SELECT 1 FROM %s FETCH FIRST 1 ROW ONLY;";
            default:
                yield "SELECT 1 FROM %s LIMIT 1;";
        };
        sql = sql.formatted(tableName);
        return executeRead(sql, null, rs -> !rs.next());
    }

    boolean contains(String tableName, String fieldName, String value) {
        var sql = switch (dbType) {
            case DB2, DERBY, ORACLE, SQLSERVER, SYBASE:
                yield "SELECT 1 FROM %s WHERE %s = ? FETCH FIRST 1 ROW ONLY;";
            default:
                yield "SELECT 1 FROM %s WHERE %s = ? LIMIT 1;";
        };
        sql = sql.formatted(tableName, fieldName);
        return executeRead(
                sql,
                stmt -> stmt.setString(1, rightSizeId(value)),
                ResultSet::next);
    }

    boolean insertIfAbsent(
            String tableName,
            String id,
            Object value) {
        var sql = switch (dbType) {
            case DB2:
                yield """
                    MERGE INTO %s AS target
                    USING (VALUES (?, ?)) AS source (id, json)
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id, json) VALUES (source.id, source.json);
                    """;
            case H2:
                yield """
                    MERGE INTO %1$s
                    USING (VALUES (?, ?)) AS source(id, json)
                    ON %1$s.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id, json) VALUES (source.id, source.json);
                    """;
            case MYSQL, MARIADB:
                yield """
                    INSERT IGNORE INTO %s (id, json)
                    VALUES (?, ?);
                    """;
            case ORACLE:
                yield """
                    MERGE INTO %s t
                    USING (SELECT ? AS id, ? AS json FROM dual) s
                    ON (t.id = s.id)
                    WHEN NOT MATCHED THEN
                        INSERT (id, json) VALUES (s.id, s.json);
                    """;
            case POSTGRESQL:
                yield """
                    INSERT INTO %s (id, json)
                    VALUES (?, ?)
                    ON CONFLICT (id) DO NOTHING;
                    """;
            case SQLSERVER:
                yield """
                    MERGE INTO %s AS target
                    USING (SELECT ? AS id, ? AS json) AS source
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id, json) VALUES (source.id, source.json);
                    """;
            default:
                yield null;
        };

        var thirdArg = sql == null ? rightSizeId(id) : null;
        if (sql == null) {
            sql = """
                INSERT INTO %1$s (id, json)
                SELECT ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM %1$s WHERE id = ?
                );
                """.formatted(tableName);
        }

        sql = sql.formatted(tableName);
        try {
            return executeWrite(sql, stmt -> {
                stmt.setString(1, rightSizeId(id));
                stmt.setClob(2, SerialUtil.toJsonReader(value));
                if (thirdArg != null) {
                    stmt.setString(3, thirdArg);
                }
            }) > 0;
        } catch (GridException e) {
            // it could be a concurrency exception so check if exist
            // already
            Sleeper.sleepMillis(100);
            if ((e.getCause() instanceof SQLException)
                    && exists(tableName, rightSizeId(id))) {
                return false; // not inserted by us
            }
            throw e;
        }
    }

    boolean insertIfAbsent(
            String tableName,
            String id) {
        var sql = switch (dbType) {
            case DB2:
                yield """
                    MERGE INTO %s AS target
                    USING (VALUES (?)) AS source (id)
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id) VALUES (source.id);
                    """;
            case H2:
                yield """
                    MERGE INTO %1$s
                    USING (VALUES (?)) AS source(id)
                    ON %1$s.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id) VALUES (source.id);
                    """;
            case MYSQL, MARIADB:
                yield """
                    INSERT IGNORE INTO %s (id)
                    VALUES (?);
                    """;
            case ORACLE:
                yield """
                    MERGE INTO %s t
                    USING (SELECT ? AS id FROM dual) s
                    ON (t.id = s.id)
                    WHEN NOT MATCHED THEN
                        INSERT (id) VALUES (s.id);
                    """;
            case POSTGRESQL:
                yield """
                    INSERT INTO %s (id)
                    VALUES (?)
                    ON CONFLICT (id) DO NOTHING;
                    """;
            case SQLSERVER:
                yield """
                    MERGE INTO %s AS target
                    USING (SELECT ? AS id) AS source
                    ON target.id = source.id
                    WHEN NOT MATCHED THEN
                        INSERT (id) VALUES (source.id);
                    """;
            default:
                yield null;
        };

        var extraArg = sql == null ? rightSizeId(id) : null;
        if (sql == null) {
            sql = """
                INSERT INTO %1$s (id)
                SELECT ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM %1$s WHERE id = ?
                );
                """.formatted(tableName);
        }

        sql = sql.formatted(tableName);
        try {
            return executeWrite(sql, stmt -> {
                stmt.setString(1, rightSizeId(id));
                if (extraArg != null) {
                    stmt.setString(2, extraArg);
                }
            }) > 0;
        } catch (GridException e) {
            // it could be a concurrency exception so check if exist
            // already
            Sleeper.sleepMillis(100);
            if ((e.getCause() instanceof SQLException)
                    && exists(tableName, rightSizeId(id))) {
                return false; // not inserted by us
            }
            throw e;
        }
    }

    boolean upsert(
            String tableName,
            String id,
            Object value) {
        var sql = switch (dbType) {
            case H2:
                yield """
                    MERGE INTO %s AS target
                    USING (VALUES(?, ?)) AS source(id, json)
                    ON target.id = source.id
                    WHEN MATCHED AND target.json <> source.json THEN
                      UPDATE SET json = source.json
                    WHEN NOT MATCHED THEN
                      INSERT (id, json) VALUES (source.id, source.json)
                    """;
            case POSTGRESQL:
                yield """
                    INSERT INTO %s (id, json)
                    VALUES (?, ?)
                    ON CONFLICT (id) DO
                    UPDATE SET json = EXCLUDED.json;
                    """;
            case MYSQL, MARIADB:
                yield """
                    INSERT INTO %s (id, json)
                    VALUES (?, ?)
                    ON DUPLICATE KEY
                    UPDATE json = VALUES(json);
                    """;
            case ORACLE, SQLSERVER, DB2, SYBASE:
                yield """
                    MERGE INTO %s AS target
                    USING (SELECT ? AS id, ? AS json) AS source
                    ON target.id = source.id
                    WHEN MATCHED THEN
                      UPDATE SET target.json = source.json
                    WHEN NOT MATCHED THEN
                      INSERT (id, json)
                      VALUES (source.id, source.json);
                    """;
            default:
                yield null; // Use separate UPDATE + INSERT logic
        };

        if (sql == null) {
            // do it in two sqls...
            return insertUpdate(tableName, id, value);
        }
        sql = sql.formatted(tableName);

        try {
            return executeWrite(sql, stmt -> {
                stmt.setString(1, rightSizeId(id));
                stmt.setClob(2,
                        new StringReader(SerialUtil.toJsonString(value)));
            }) > 0;
        } catch (GridException e) {
            // if the row already exist with the same values, it could mean
            // an new record was concurrently added. We check if so, and
            // return false if that's the case (no change).
            if (Boolean.TRUE.equals(executeRead(
                    "SELECT 1 FROM %s WHERE id = ? AND json = ?;"
                            .formatted(tableName),
                    stmt -> {
                        stmt.setString(1, rightSizeId(id));
                        stmt.setString(2, SerialUtil.toJsonString(value));
                    },
                    ResultSet::next

            ))) {
                return false;
            }
            throw e;
        }
    }

    <T> T poll(String tableName, Class<T> type) {
        var sql = switch (dbType) {
            case POSTGRESQL:
                yield """
                    DELETE FROM %1$s
                    WHERE id = (
                      SELECT id
                      FROM %1$s
                      ORDER BY created_at ASC
                      LIMIT 1
                    )
                    RETURNING *
                    """;
            case SQLSERVER:
                yield """
                    DELETE FROM %1$s
                    OUTPUT DELETED.*
                    WHERE id = (
                      SELECT id
                      FROM %1$s
                      ORDER BY created_at ASC
                      LIMIT 1
                    );
                    """;
            default:
                yield null; // Use separate SELECT + DELETE logic
        };
        if (sql != null) {
            sql = sql.formatted(tableName);
            return executeRead(sql, null,
                    rs -> rs.next()
                            ? SerialUtil.fromJson(rs.getString("value"), type)
                            : null);
        }
        return pollFallback(tableName, type);
    }

    /**
     * Creates a table only if it doesn't exist.
     */
    void createTableIfNotExists(String tableName, String createSQL) {
        transactionManager.runInTransaction(conn -> {
            if (!tableExists(tableName)) {
                LOG.info("Creating table: {}", tableName);
                try (var statement = conn.createStatement()) {
                    statement.execute(createSQL);
                    LOG.info("Table created successfully: {}", tableName);
                } catch (SQLException e) {
                    if (!tableExists(tableName)) {
                        throw e;
                    }
                    LOG.info("Table already exist: {}", tableName);
                }
            } else {
                LOG.info("Table found: {}", tableName);
            }
            return null;
        });
    }

    void dropTableIfExists(String tableName) {
        var sql = switch (dbType) {
            case H2, MARIADB, MYSQL, POSTGRESQL:
                yield "DROP TABLE IF EXISTS %s;";
            default:
                yield "DROP TABLE %s;";
        };
        sql = sql.formatted(tableName);
        try {
            executeWrite(sql, null);
        } catch (GridException e) {
            // Catch exception and check if already deleted
            Sleeper.sleepMillis(100);
            if (tableExists(tableName)) {
                throw e;
            }
            LOG.info("Table already deleted: %s".formatted(tableName));
        }
    }

    int executeWrite(
            String sql, FailableConsumer<PreparedStatement, SQLException> c) {
        return transactionManager.runInTransaction(conn -> {
            try (var stmt = conn.prepareStatement(sql)) {
                if (c != null) {
                    c.accept(stmt);
                }
                return stmt.executeUpdate();
            }
        });
    }

    <R> R executeRead(
            String sql,
            FailableConsumer<PreparedStatement, SQLException> psc,
            FailableFunction<ResultSet, R, SQLException> rsf) {
        return transactionManager.runInTransaction(conn -> {
            try (var stmt = conn.prepareStatement(sql)) {
                if (psc != null) {
                    psc.accept(stmt);
                }
                try (var rs = stmt.executeQuery()) {
                    return rsf.apply(rs);
                }
            }
        });
    }

    <R> R runInTransactionAndReturn(
            FailableFunction<Connection, R, SQLException> function) {
        return transactionManager.runInTransaction(function);
    }

    void runInTransaction(
            FailableConsumer<Connection, SQLException> consumer) {
        transactionManager.runInTransaction(conn -> {
            consumer.accept(conn);
            return null;
        });
    }

    static String rightSizeId(String id) {
        try {
            return StringUtil.truncateBytesWithHash(
                    id, StandardCharsets.UTF_8, ID_MAX_LENGTH);
        } catch (CharacterCodingException e) {
            throw new GridException("Could not truncate ID: " + id, e);
        }
    }

    boolean tableExists(String tableName) {
        return transactionManager.runInTransaction(conn -> {
            var metaData = conn.getMetaData();
            var catalog = conn.getCatalog();
            var schema = getSchema();
            try (var rs = metaData.getTables(catalog, schema,
                    unesc(tableName), new String[] { "TABLE" })) {
                return rs.next();
            }
        });
    }

    //--- Private methods ------------------------------------------------------

    private <T> T pollFallback(String tableName, Class<T> type) {

        var selectSQL = (switch (dbType) {
            case DB2, DERBY, ORACLE, SQLSERVER, SYBASE:
                yield """
                    SELECT id, json
                    FROM %s
                    ORDER BY created_at ASC
                    FETCH FIRST 1 ROW ONLY;
                    """;
            default:
                yield """
                    SELECT id, json
                    FROM %s
                    ORDER BY created_at ASC
                    LIMIT 1 FOR UPDATE;
                    """;
        }).formatted(tableName);
        var delSQL = "DELETE FROM %s WHERE id = ?".formatted(tableName);

        return transactionManager.runInTransaction(conn -> {
            try (var selStmt = conn.prepareStatement(selectSQL);
                    var rs = selStmt.executeQuery()) {
                if (rs.next()) {
                    var id = rs.getString("id");
                    var value = SerialUtil.fromJson(
                            rs.getString("json"), type);
                    try (var delStmt =
                            conn.prepareStatement(delSQL)) {
                        delStmt.setString(1, id);
                        delStmt.executeUpdate();
                    }
                    return value;
                }
            }
            return null;
        });

    }

    private boolean insertUpdate(String tableName, String id, Object value) {
        var json = SerialUtil.toJsonString(value);
        return transactionManager.runInTransaction(conn -> {
            var changed = false;
            var updateSQL =
                    "UPDATE %s SET json = ? WHERE id = ? AND json <> ?"
                            .formatted(tableName);
            try (var updateStmt = conn.prepareStatement(updateSQL)) {
                updateStmt.setString(1, json);
                updateStmt.setString(2, rightSizeId(id));
                updateStmt.setString(3, json);
                var rowsUpdated = updateStmt.executeUpdate();
                // If no rows updated, insert instead
                if (rowsUpdated > 0) {
                    changed = true;
                } else if ((rowsUpdated == 0) && !exists(tableName, id)) {
                    // Row does not exist, perform an INSERT
                    var insertSQL =
                            "INSERT INTO %s (id, json) VALUES (?, ?)"
                                    .formatted(tableName);
                    try (var insertStmt =
                            conn.prepareStatement(insertSQL)) {
                        insertStmt.setString(1, rightSizeId(id));
                        insertStmt.setString(2, json);
                        insertStmt.executeUpdate();
                    }
                    changed = true;
                }
            }
            return changed;
        });
    }

    private boolean exists(String tableName, String id) {
        return transactionManager.runInTransaction(conn -> {
            try (var checkStmt = conn.prepareStatement(
                    "SELECT 1 FROM %s WHERE id = ?".formatted(tableName))) {
                checkStmt.setString(1, rightSizeId(id));
                var rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return true;
                }
            }
            return false;
        });
    }

    private String getSchema() {
        return transactionManager.runInTransaction(Connection::getSchema);
    }

    private static String esc(String value, String beforeAndAfter) {
        return esc(value, beforeAndAfter, beforeAndAfter);
    }

    private static String esc(String value, String before, String after) {
        var val = value;
        val = StringUtils.prependIfMissing(val, before);
        return StringUtils.appendIfMissing(val, after);
    }

    private static String unesc(String value) {
        if (value.matches("^\\W.*\\W$")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static DbType detectDbType(String jdbcUrlOrDataSource) {
        if (jdbcUrlOrDataSource == null) {
            return DbType.OTHER;
        }
        var upper = jdbcUrlOrDataSource.toUpperCase();
        return Stream.of(DbType.values())
                .filter(type -> upper.contains(type.name()))
                .findFirst()
                .orElse(DbType.OTHER);
    }
}
