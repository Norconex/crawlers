/* Copyright 2024 Norconex Inc.
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

import static com.norconex.grid.core.util.SerialUtil.fromJson;

import java.util.Objects;
import java.util.function.BiPredicate;

import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.SerializableUnaryOperator;

import lombok.NonNull;

public class JdbcGridMap<T> extends BaseJdbcGridStore<T> implements GridMap<T> {

    @NonNull
    public JdbcGridMap(
            DbAdapter dbAdapter, String name, Class<? extends T> type) {
        super(dbAdapter, name, type);
        dbAdapter.createTableIfNotExists(
                getTableName(),
                """
                CREATE TABLE %s (
                  id %s NOT NULL,
                  json %s,
                  PRIMARY KEY (id)
                );
                """.formatted(
                        getTableName(),
                        getDbAdapter().varcharType(),
                        getDbAdapter().textType()));
    }

    @Override
    public boolean put(String key, T object) {
        return getDbAdapter().upsert(getTableName(), key, object);
    }

    @Override
    public boolean update(String key, SerializableUnaryOperator<T> updater) {
        return getDbAdapter().runInTransactionAndReturn(conn -> {
            // Insert dummy row first so we can lock it.
            // This is required to avoid concurrency issues by some DBs.
            getDbAdapter().insertIfAbsent(getTableName(), key, "LOCK_HACK");

            // Lock the row
            T existingValue = null;
            try (var lockStmt = conn.prepareStatement(
                    "SELECT json FROM %s WHERE id = ? FOR UPDATE;"
                            .formatted(getTableName()))) {
                lockStmt.setString(1, DbAdapter.rightSizeId(key));
                try (var rs = lockStmt.executeQuery()) {
                    if (rs.next()) {
                        var json = rs.getString("json");
                        if (!"\"LOCK_HACK\"".equals(json)) {
                            existingValue = fromJson(json, getType());
                        }
                    }
                }
            }

            var newValue = updater.apply(existingValue);
            put(key, newValue);
            return !Objects.equals(existingValue, newValue);
        });
    }

    @Override
    public T get(String key) {
        return getDbAdapter().<T>executeRead(
                "SELECT json FROM %s WHERE id = ?;"
                        .formatted(getTableName()),
                stmt -> stmt.setString(1, DbAdapter.rightSizeId(key)),
                rs -> rs.next()
                        ? fromJson(rs.getString(1), getType())
                        : null);
    }

    @Override
    public boolean delete(String key) {
        return getDbAdapter().executeWrite(
                "DELETE FROM %s WHERE id = ?".formatted(getTableName()),
                stmt -> stmt.setString(1, DbAdapter.rightSizeId(key))) > 0;
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return getDbAdapter().executeRead(
                "SELECT id, json FROM %s".formatted(getTableName()),
                stmt -> {},
                rs -> {
                    while (rs.next()) {
                        if (!predicate.test(
                                rs.getString(1),
                                fromJson(rs.getString(2), getType()))) {
                            return false;
                        }
                    }
                    return true;
                });
    }
}
