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
package com.norconex.grid.calcite;

import com.norconex.grid.calcite.db.Db;
import com.norconex.grid.core.storage.GridStore;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
abstract class BaseCalciteGridStore<T> implements GridStore<T> {

    private final String name;
    private final String tableName; // same as name but SQL-escaped
    private final Class<? extends T> type;
    private final Db dbHelper;

    @NonNull
    protected BaseCalciteGridStore(
            Db dbHelper, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        this.dbHelper = dbHelper;
        tableName = dbHelper.toSafeName(name);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final void clear() {
        dbHelper.deleteAllRows(tableName);
    }

    @Override
    public final boolean isEmpty() {
        return dbHelper.isEmpty(tableName);
    }

    @Override
    public boolean contains(String key) {
        return dbHelper.contains(tableName, "id", key);
    }

    @Override
    public final long size() {
        return dbHelper.count(tableName);
    }
}
