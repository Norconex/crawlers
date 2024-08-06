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

import static org.apache.commons.lang3.StringUtils.stripEnd;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.commons.lang.text.StringUtil;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreException;
import com.norconex.crawler.core.store.impl.SerialUtil;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcDataStore<T> implements DataStore<T> {

    private static final PreparedStatementConsumer NO_ARGS = stmt -> {};

    private static final class Sqls {
        private String clear;
        private String count;
        private String createTable;
        private String delete;
        private String deleteFirst;
        private String exists;
        private String find;
        private String findFirst;
        private String forEach;
        private String isEmpty;
        private String save;
    }

    @Builder
    static class StoreSettings<T> {
        private final JdbcDataStoreEngine engine;
        private String storeName;
        private String tableName;
        private final Class<? extends T> type;
        private final String createTableSqlTemplate;
        private final String upsertSqlTemplate;
    }

    private final StoreSettings<T> settings;
    private final Sqls sqls = new Sqls();

    JdbcDataStore(StoreSettings<T> settings) {
        this.settings = settings;
        prepareSqls();
        if (!settings.engine.tableExist(settings.tableName)) {
            createTable();
        }
    }

    @Override
    public String getName() {
        return settings.storeName;
    }
    String tableName() {
        return settings.tableName;
    }

    @Override
    public boolean save(String id, T object) {
        return executeWrite(
            sqls.save,
            stmt -> {
                stmt.setString(1, serializableId(id));
                stmt.setObject(2, SerialUtil.toJsonString(object));
            }) > 0;
    }

    @Override
    public Optional<T> find(String id) {
        return executeRead(
                sqls.find,
                stmt -> stmt.setString(1, serializableId(id)),
                this::firstObject);
    }


    @Override
    public Optional<T> findFirst() {
        return executeRead(
                sqls.findFirst,
                NO_ARGS,
                this::firstObject);
    }

    @Override
    public boolean exists(String id) {
        return executeRead(
                sqls.exists,
                stmt -> stmt.setString(1, serializableId(id)),
                ResultSet::next);
    }

    @Override
    public long count() {
        return executeRead(
                sqls.count,
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
                sqls.delete,
                stmt -> stmt.setString(1, serializableId(id))) > 0;
    }

    @Override
    public Optional<T> deleteFirst() {
        Record<T> rec = executeRead(
                sqls.deleteFirst,
                NO_ARGS,
                this::firstRecord);
        if (!rec.isEmpty()) {
            delete(rec.id);
        }
        return rec.object;
    }

    @Override
    public void clear() {
        executeWrite(sqls.clear, NO_ARGS);
    }

    @Override
    public void close() {
        //NOOP: Closed implicitly when datasource is closed.
    }

    // returns true if was all read
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return executeRead(
                sqls.forEach,
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
        return executeRead(sqls.isEmpty, NO_ARGS, rs -> !rs.next());
    }

    private void createTable() {
        //NOTE: some DB vendors do not have ways to create indices
        // in the same statement as the table creation. In such case they
        // maybe combined into two statements. We split on ; and execute
        // the multiple statements.
        var statements = stripEnd(sqls.createTable.trim(), ";").split(";");
        try (var conn = settings.engine.getConnection()) {
            try (var stmt = conn.createStatement()) {
                for (String statement : statements) {
                    stmt.executeUpdate(statement);
                }
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
                LOG.info("Table created: " + settings.tableName);
            }
        } catch (SQLException e) {
            throw new DataStoreException(
                    tabled("Could not create table '<table>' with SQL: "
                            + sqls.createTable), e);
        }
    }

    boolean rename(String newStoreName) {
        var newTableName = settings.engine.toTableName(newStoreName);
        var targetExists = settings.engine.tableExist(newTableName);
        if (targetExists) {
            executeWrite("DROP TABLE " + newTableName, NO_ARGS);
        }
        executeWrite("ALTER TABLE %s RENAME TO %s".formatted(
                settings.tableName, newTableName), NO_ARGS);
        settings.storeName = newStoreName;
        settings.tableName = newTableName;
        prepareSqls();
        return targetExists;
    }

    //--- Inner classes/interfaces ---------------------------------------------

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

    //--- Private methods ------------------------------------------------------

    private void prepareSqls() {
        sqls.clear = tabled("DELETE FROM <table>");
        sqls.count = tabled("SELECT count(*) FROM <table>");
        sqls.createTable = tabled(settings.createTableSqlTemplate);
        sqls.delete = tabled("DELETE FROM <table> WHERE id = ?");
        sqls.deleteFirst = tabled("SELECT id, json FROM <table> ORDER BY seq");
        sqls.exists = tabled("SELECT 1 FROM <table> WHERE id = ?");
        sqls.find = tabled("SELECT id, json FROM <table> WHERE id = ?");
        sqls.findFirst = tabled("SELECT id, json FROM <table> ORDER BY seq");
        sqls.forEach = tabled("SELECT id, json FROM <table>");
        sqls.isEmpty = tabled("SELECT * FROM <table>");
        sqls.save = tabled(settings.upsertSqlTemplate);
    }

    private String tabled(String sql) {
        return sql.replace("<table>", settings.tableName);
    }

    private String serializableId(String id) {
        try {
            return StringUtil.truncateBytesWithHash(
                    id, StandardCharsets.UTF_8, JdbcDialect.ID_MAX_LENGTH);
        } catch (CharacterCodingException e) {
            throw new DataStoreException("Could not truncate ID: " + id, e);
        }
    }

    private Optional<T> firstObject(ResultSet rs) {
        try {
            if (rs.next()) {
                return toTypedObject(rs.getObject(2));
            }
            return Optional.empty();
        } catch (IOException | SQLException e) {
            throw new DataStoreException(
                    "Could not get object from table '<table>'."
                    .formatted(settings.tableName), e);
        }
    }
    private Record<T> firstRecord(ResultSet rs) {
        try {
            if (rs.next()) {
                return toRecord(rs);
            }
            return new Record<>();
        } catch (IOException | SQLException e) {
            throw new DataStoreException(
                    "Could not get record from table '<table>'."
                    .formatted(settings.tableName), e);
        }
    }
    private Record<T> toRecord(ResultSet rs) throws IOException, SQLException {
        var rec = new Record<T>();
        rec.id = rs.getString(1);
        rec.object = toTypedObject(rs.getObject(2));
        return rec;
    }
    private Optional<T> toTypedObject(Object rsObject)
            throws IOException, SQLException {
        if (rsObject == null) {
            return Optional.empty();
        }
        if (rsObject instanceof String str) {
            return Optional.ofNullable(SerialUtil.fromJson(str, settings.type));
        }
        // Else, we assume CLOB
        var reader = ((Clob) rsObject).getCharacterStream();
        try (var r = reader) {
            return Optional.ofNullable(SerialUtil.fromJson(r, settings.type));
        }
    }

    Class<?> getType() {
        return settings.type;
    }

    private <R> R executeRead(
            String sql,
            PreparedStatementConsumer psc,
            ResultSetFunction<R> rsc) {
        try (var conn = settings.engine.getConnection()) {
            try (var stmt = conn.prepareStatement(sql)) {
                psc.accept(stmt);
                try (var rs = stmt.executeQuery()) {
                    return rsc.accept(rs);
                }
            }
        } catch (SQLException | IOException e) {
            throw new DataStoreException(
                    "Could not read from table '%s' with SQL:\n%s"
                    .formatted(settings.tableName, sql), e);
        }
    }
    private int executeWrite(String sql, PreparedStatementConsumer c) {
        try (var conn = settings.engine.getConnection()) {
            try (var stmt = conn.prepareStatement(sql)) {
                c.accept(stmt);
                var val = stmt.executeUpdate();
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
                return val;
            }
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not write to table '%s' with SQL:\n%s"
                    .formatted(settings.tableName, sql), e);
        }
    }
}
