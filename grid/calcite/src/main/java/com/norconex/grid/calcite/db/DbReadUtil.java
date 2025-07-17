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

import static com.norconex.grid.core.util.SerialUtil.fromJson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;

import com.norconex.grid.core.util.SerialUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Methods for reading data from database only (not affecting it).
 */
@Slf4j
class DbReadUtil {

    private DbReadUtil() {
    }

    static boolean isEmpty(Db db, String tableName) {
        if (db.isRowTenancy()) {
            return executeRead(
                    db,
                    "SELECT 1 FROM ? WHERE namespace = ? LIMIT 1;",
                    ps -> {
                        ps.setString(1, tableName);
                        ps.setString(2, db.getNamespace());
                    }, rs -> !rs.next());
        }
        return executeRead(db, "SELECT 1 FROM ? LIMIT 1;",
                ps -> ps.setString(1, tableName), rs -> !rs.next());
    }

    static boolean tableExists(Db db, String tableName) {
        return db.runInTransactionAndReturn(conn -> {
            var metaData = conn.getMetaData();
            var catalog = conn.getCatalog();
            var schema = getSchema(db);
            try (var rs = metaData.getTables(catalog, schema,
                    tableName, new String[] { "TABLE" })) {
                return rs.next();
            }
        });
    }

    static long count(Db db, String tableName) {
        if (db.isRowTenancy()) {
            return executeRead(
                    db,
                    "SELECT count(*) FROM ? WHERE namespace = ?;",
                    ps -> {
                        ps.setString(1, tableName);
                        ps.setString(2, db.getNamespace());
                    },
                    rs -> rs.next() ? rs.getLong(1) : 0);
        }
        return executeRead(
                db,
                "SELECT count(*) FROM ?;",
                ps -> ps.setString(1, tableName),
                rs -> rs.next() ? rs.getLong(1) : 0);
    }

    static boolean contains(Db db, String tableName, String fieldName,
            String value) {
        if (db.isRowTenancy()) {
            return executeRead(
                    db,
                    "SELECT 1 FROM %s WHERE %s = ? AND namespace = ? LIMIT 1;"
                            .formatted(tableName, fieldName),
                    ps -> {
                        ps.setString(1, Db.rightSizeId(value));
                        ps.setString(2, db.getNamespace());
                    },
                    ResultSet::next);
        }
        return executeRead(db,
                "SELECT 1 FROM %s WHERE %s = ? LIMIT 1;"
                        .formatted(tableName, fieldName),
                ps -> ps.setString(1, Db.rightSizeId(value)),
                ResultSet::next);
    }

    static <T> T getById(Db db, String tableName, String key,
            Class<T> type) {
        return executeRead(
                db,
                (db.isRowTenancy()
                        ? "SELECT json FROM %s WHERE id = ? AND namespace = ?;"
                        : "SELECT json FROM %s WHERE id = ?;")
                                .formatted(tableName),
                ps -> {
                    ps.setString(1, Db.rightSizeId(key));
                    if (db.isRowTenancy()) {
                        ps.setString(2, db.getNamespace());
                    }
                },
                rs -> rs.next()
                        ? fromJson(rs.getString(1), type)
                        : null);
    }

    static boolean exists(Db db, String tableName, String id) {
        return db.runInTransactionAndReturn(conn -> {
            try (var checkStmt = conn.prepareStatement(
                    (db.isRowTenancy()
                            ? "SELECT 1 FROM %s WHERE id = ? AND namespace = ?"
                            : "SELECT 1 FROM %s WHERE id = ?")
                                    .formatted(tableName))) {
                checkStmt.setString(1, Db.rightSizeId(id));
                if (db.isRowTenancy()) {
                    checkStmt.setString(2, db.getNamespace());
                }
                var rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return true;
                }
            }
            return false;
        });
    }

    static <R> R executeRead(
            Db db,
            String sql,
            FailableConsumer<PreparedStatement, SQLException> psc,
            FailableFunction<ResultSet, R, SQLException> rsf) {
        return db.runInTransactionAndReturn(conn -> {
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

    static <T> T poll(Db db, String tableName, Class<T> type) {

        var sql = (db.isRowTenancy()
                ? """
                    WITH first_row AS (
                        SELECT id
                        FROM %1$s
                        WHERE namespace = ?
                        ORDER BY created_at ASC
                        FETCH FIRST 1 ROW ONLY
                    )
                    DELETE FROM %1$s
                    WHERE id = (SELECT id FROM first_row)
                    AND namespace = ?
                    """
                : """
                    DELETE FROM %1$s
                    WHERE id = (
                        SELECT id
                        FROM %1$s
                        ORDER BY created_at ASC
                        FETCH FIRST 1 ROW ONLY
                    )
                    """.formatted(tableName));
        return executeRead(db, sql, null,
                rs -> rs.next()
                        ? SerialUtil.fromJson(rs.getString("json"), type)
                        : null);
    }

    //--- Private methods ------------------------------------------------------

    private static String getSchema(Db db) {
        return db.runInTransactionAndReturn(Connection::getSchema);
    }
}
