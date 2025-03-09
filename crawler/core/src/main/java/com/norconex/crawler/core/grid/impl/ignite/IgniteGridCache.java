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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.Objects;
import java.util.function.BiPredicate;

import org.apache.ignite.Ignite;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Table;

import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.util.SerialUtil;
import com.norconex.crawler.core.util.SerializableUnaryOperator;

import lombok.Getter;
import lombok.NonNull;

//TODO rename GridCache to GridMap (or GridKeyValue)
public class IgniteGridCache<T> implements GridCache<T> {

    private final String name;
    @Getter
    private final Class<? extends T> type;
    private final Ignite ignite;

    //    @Getter(value = AccessLevel.PACKAGE)
    private final Table table;
    private final KeyValueView<String, String> view;

    @NonNull
    public IgniteGridCache(
            Ignite ignite, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        this.ignite = ignite;

        ignite.sql().execute(null, """
            CREATE TABLE IF NOT EXISTS %s (
                key STRING PRIMARY KEY,
                value STRING NOT NULL
            );""".formatted(name));

        table = ignite.tables().table(name);
        view = table.keyValueView(String.class, String.class);

        //        table = ignite.catalog().createTable(TableDefinition
        //                .builder(internalName)
        //                .ifNotExists()
        //                .record(KeyValueRecord.class)
        //                .build());

        //        cache = ignite
        //                .tables()
        //                .table(internalName)
        //                .keyValueView(String.class, type);

        //        var cfg = new CacheConfiguration<String, T>();
        //        cfg.setName(name + IgniteGridStorage.Suffix.CACHE);
        //        cfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        //        cfg.setWriteSynchronizationMode(
        //                CacheWriteSynchronizationMode.FULL_SYNC);
        //        cfg.setReadFromBackup(false);

        // Until we have better... for size()
        //        cfg.setStatisticsEnabled(true);

        //        cache = ignite.getOrCreateCache(cfg);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean put(String key, T object) {
        return !Objects.equals(
                object,
                view.getAndPut(null, key, SerialUtil.toJsonString(object)));
    }

    @Override
    public boolean update(String key, SerializableUnaryOperator<T> updater) {
        return ignite.transactions().runInTransaction(tx -> {
            var existingValue = SerialUtil.fromJson(view.get(tx, key), type);
            var newValue = updater.apply(existingValue);
            view.put(tx, key, SerialUtil.toJsonString(newValue));
            return !Objects.equals(existingValue, newValue);
        });
    }

    @Override
    public T get(String key) {
        return SerialUtil.fromJson(view.get(null, key), type);
    }

    @Override
    public boolean delete(String key) {
        return view.remove(null, key);
    }

    @Override
    public void clear() {
        //TODO shall we delete all with SQL instead?
        ignite.catalog().dropTable(name);
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT * FROM %s".formatted(name))) {
            while (resultSet.hasNext()) {
                var row = resultSet.next();
                if (!predicate.test(row.stringValue(0),
                        SerialUtil.fromJson(row.stringValue(1), type))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT 1 FROM %s LIMIT 1".formatted(name))) {
            return !resultSet.hasNext();
        }
    }

    @Override
    public boolean contains(Object key) {
        return view.contains(null, (String) key);
    }

    @Override
    public long size() {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT count(*) FROM %s".formatted(name))) {
            return resultSet.next().longValue(0);
        }
    }
}
