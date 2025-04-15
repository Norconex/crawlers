/* Copyright 2024-2025 Norconex Inc.
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

import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.util.SerialUtil;

import lombok.NonNull;

public class JdbcGridQueue<T> extends BaseJdbcGridStore<T>
        implements GridQueue<T> {

    @NonNull
    public JdbcGridQueue(
            DbAdapter dbAdapter, String name, Class<? extends T> type) {
        super(dbAdapter, name, type);
        dbAdapter.createTableIfNotExists(
                getTableName(),
                """
                CREATE TABLE %s (
                  id %s NOT NULL,
                  json %s,
                  created_at %s,
                  PRIMARY KEY (id)
                );
                CREATE INDEX idx_created_at ON %s (created_at);
                """.formatted(
                        getTableName(),
                        getDbAdapter().varcharType(),
                        getDbAdapter().textType(),
                        getDbAdapter().bigIntType(),
                        getTableName()));
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return getDbAdapter().executeRead(
                "SELECT id, json FROM %s ORDER BY created_at ASC;"
                        .formatted(getTableName()),
                null,
                rs -> {
                    while (rs.next()) {
                        if (!predicate.test(
                                rs.getString(1),
                                SerialUtil.fromJson(
                                        rs.getString(2), getType()))) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    @Override
    public boolean put(String key, T object) {
        return getDbAdapter().insertIfAbsent(getTableName(), key, object);
    }

    @Override
    public Optional<T> poll() {
        return ofNullable(getDbAdapter().poll(getTableName(), getType()));
    }
}
