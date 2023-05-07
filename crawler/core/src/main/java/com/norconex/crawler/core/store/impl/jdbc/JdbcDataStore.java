/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.jdbc;

import static java.lang.System.currentTimeMillis;

import java.io.IOException;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreException;
import com.norconex.crawler.core.store.impl.SerialUtil;

import lombok.NonNull;

public class JdbcDataStore<T> implements DataStore<T> {

    private static final PreparedStatementConsumer NO_ARGS = stmt -> {};

    private final JdbcDataStoreEngine engine;
    private String tableName;
    private String storeName;
    private final Class<? extends T> type;
    private final TableAdapter adapter;

    JdbcDataStore(
            @NonNull JdbcDataStoreEngine engine,
            @NonNull String storeName,
            @NonNull Class<? extends T> type) {
        this.engine = engine;
        this.type = type;
        adapter = engine.getTableAdapter();
        this.storeName = storeName;
        tableName = engine.tableName(storeName);
        if (!engine.tableExist(tableName)) {
            createTable();
        }
    }

    @Override
    public String getName() {
        return storeName;
    }
    String tableName() {
        return tableName;
    }

    @Override
    public void save(String id, T object) {
        executeWrite("""
                MERGE INTO <table> AS t
                USING (
                  SELECT
                    CAST(? AS %s) AS id,
                    CAST(? AS %s) AS modified,
                    CAST(? AS %s) AS json
                  FROM DUAL
                ) AS s
                  ON t.id = s.id
                WHEN NOT MATCHED THEN
                  INSERT (id, modified, json)
                  VALUES (s.id, s.modified, s.json)
                WHEN MATCHED THEN
                  UPDATE SET
                    t.modified = s.modified,
                    t.json = s.json
                """.formatted(
                        adapter.idType(),
                        adapter.modifiedType(),
                        adapter.jsonType()),
                stmt -> {
                    stmt.setString(1, adapter.serializableId(id));
                    stmt.setTimestamp(2, new Timestamp(currentTimeMillis()));
                    stmt.setClob(3, SerialUtil.toJsonReader(object));
        });
    }

    @Override
    public Optional<T> find(String id) {
        return executeRead(
                "SELECT id, json FROM <table> WHERE id = ?",
                stmt -> stmt.setString(1, adapter.serializableId(id)),
                this::firstObject);
    }


    @Override
    public Optional<T> findFirst() {
        return executeRead(
                "SELECT id, json FROM <table> ORDER BY modified",
                NO_ARGS,
                this::firstObject);
    }

    @Override
    public boolean exists(String id) {
        return executeRead(
                "SELECT 1 FROM <table> WHERE id = ?",
                stmt -> stmt.setString(1, adapter.serializableId(id)),
                ResultSet::next);
    }

    @Override
    public long count() {
        return executeRead(
                "SELECT count(*) FROM <table>",
                NO_ARGS,
                rs -> {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                });
    }

    @Override
    public boolean delete(String id) {
        return executeWrite(
                "DELETE FROM <table> WHERE id = ?",
                stmt -> stmt.setString(1, adapter.serializableId(id))) > 0;
    }

    @Override
    public Optional<T> deleteFirst() {
        Record<T> rec = executeRead(
                "SELECT id, json FROM <table> ORDER BY modified",
                NO_ARGS,
                this::firstRecord);
        if (!rec.isEmpty()) {
            delete(rec.id);
        }
        return rec.object;
    }

    @Override
    public void clear() {
        executeWrite("DELETE FROM <table>", NO_ARGS);
    }

    @Override
    public void close() {
        //NOOP: Closed implicitly when datasource is closed.
    }

    // returns true if was all read
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return executeRead(
                "SELECT id, json FROM <table>",
                NO_ARGS,
                rs -> {
                    while (rs.next()) {
                        var rec = toRecord(rs);
                        if (!predicate.test(rec.id, rec.object.get())) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    @Override
    public boolean isEmpty() {
        return executeRead(
                "SELECT * FROM <table>", NO_ARGS, rs -> !rs.next());
    }

    private void createTable() {
        try (var conn = engine.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                        CREATE TABLE %s (
                          id %s NOT NULL,
                          modified %s,
                          json %s,
                          PRIMARY KEY (id)
                        )
                        """.formatted(
                                tableName,
                                adapter.idType(),
                                adapter.modifiedType(),
                                adapter.jsonType()));
                stmt.executeUpdate(
                        "CREATE INDEX %s_modified_index ON %s(modified)"
                                .formatted(tableName, tableName));
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
            }
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not create table '" + tableName + "'.", e);
        }
    }

    boolean rename(String newStoreName) {
        var newTableName = engine.tableName(newStoreName);
        var targetExists = engine.tableExist(newTableName);
        if (targetExists) {
            executeWrite("DROP TABLE " + newTableName, NO_ARGS);
        }
        executeWrite("ALTER TABLE <table> RENAME TO " + newTableName, NO_ARGS);
        storeName = newStoreName;
        tableName = newTableName;
        return targetExists;
    }

    private Optional<T> firstObject(ResultSet rs) {
        try {
            if (rs.first()) {
                return toObject(rs.getClob(2).getCharacterStream());
            }
            return Optional.empty();
        } catch (IOException | SQLException e) {
            throw new DataStoreException(
                    "Could not get object from table '" + tableName + "'.", e);
        }
    }
    private Record<T> firstRecord(ResultSet rs) {
        try {
            if (rs.first()) {
                return toRecord(rs);
            }
            return new Record<>();
        } catch (IOException | SQLException e) {
            throw new DataStoreException(
                    "Could not get record from table '" + tableName + "'.", e);
        }
    }
    private Record<T> toRecord(ResultSet rs) throws IOException, SQLException {
        var rec = new Record<T>();
        rec.id = rs.getString(1);
        rec.object = toObject(rs.getClob(2).getCharacterStream());
        return rec;
    }
    private Optional<T> toObject(Reader reader) throws IOException {
        try (var r = reader) {
            return Optional.ofNullable(SerialUtil.fromJson(r, type));
        }
    }

    Class<?> getType() {
        return type;
    }

    private <R> R executeRead(
            String sql,
            PreparedStatementConsumer psc,
            ResultSetFunction<R> rsc) {
        try (var conn = engine.getConnection()) {
            try (var stmt = conn.prepareStatement(
                    sql.replace("<table>", tableName))) {
                psc.accept(stmt);
                try (var rs = stmt.executeQuery()) {
                    return rsc.accept(rs);
                }
            }
        } catch (SQLException | IOException e) {
            throw new DataStoreException(
                    "Could not read from table '" + tableName + "'.", e);
        }
    }
    private int executeWrite(String sql, PreparedStatementConsumer c) {
        try (var conn = engine.getConnection()) {
            try (var stmt = conn.prepareStatement(
                    sql.replace("<table>", tableName))) {
                c.accept(stmt);
                var val = stmt.executeUpdate();
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
                return val;
            }
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not write to table '" + tableName + "'.", e);
        }
    }

    @FunctionalInterface
    interface PreparedStatementConsumer {
        void accept(PreparedStatement stmt) throws SQLException;
    }
    @FunctionalInterface
    interface ResultSetFunction<R> {
        R accept(ResultSet rs) throws SQLException, IOException;
    }

    private static class Record<T> {
        private String id;
        private Optional<T> object = Optional.empty();
        private boolean isEmpty() {
            return id == null;
        }
    }
}
