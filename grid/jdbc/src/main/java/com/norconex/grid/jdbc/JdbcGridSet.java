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

import java.util.function.Predicate;

import com.norconex.grid.core.storage.GridSet;

import lombok.NonNull;

public class JdbcGridSet extends BaseJdbcGridStore<String> implements GridSet {

    @NonNull
    public JdbcGridSet(DbAdapter dbAdapter, String name) {
        super(dbAdapter, name, String.class);
        dbAdapter.createTableIfNotExists(
                getTableName(),
                """
                CREATE TABLE %s (
                  id %s,
                  PRIMARY KEY (id)
                );
                """.formatted(
                        getTableName(),
                        getDbAdapter().varcharType()));
    }

    /**
     * Loops through all elements of this set or until the predicate returns
     * <code>false</code>.
     * @param predicate the predicate applied to each items
     * @return <code>true</code> if the predicate returned <code>true</code>
     *   for all objects.
     */
    @Override
    public boolean forEach(Predicate<String> predicate) {
        return getDbAdapter().executeRead(
                "SELECT id FROM %s;".formatted(getTableName()),
                null,
                rs -> {
                    while (rs.next()) {
                        if (!predicate.test(rs.getString(1))) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    @Override
    public boolean contains(String key) {
        return getDbAdapter().contains(getTableName(), "id", key);
    }

    @Override
    public boolean add(String id) {
        return getDbAdapter().insertIfAbsent(getTableName(), id);
    }
}
