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

import com.norconex.grid.core.storage.GridStore;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
abstract class BaseJdbcGridStore<T> implements GridStore<T> {

    private final String name;
    private final String tableName; // same as name but SQL-escaped
    private final Class<? extends T> type;
    private final DbAdapter dbAdapter;

    @NonNull
    protected BaseJdbcGridStore(
            DbAdapter dbAdapter, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        this.dbAdapter = dbAdapter;
        tableName = dbAdapter.esc(name);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final void clear() {
        dbAdapter.executeWrite("DELETE FROM %s".formatted(tableName), null);
    }

    @Override
    public final boolean isEmpty() {
        return dbAdapter.isEmpty(tableName);
    }

    @Override
    public boolean contains(String key) {
        return dbAdapter.contains(tableName, "id", key);
    }

    @Override
    public final long size() {
        return dbAdapter.executeRead(
                "SELECT count(*) FROM %s".formatted(tableName),
                null,
                rs -> rs.next() ? rs.getLong(1) : 0);
    }
}
