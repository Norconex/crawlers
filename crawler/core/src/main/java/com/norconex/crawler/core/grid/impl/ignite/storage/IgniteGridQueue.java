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
package com.norconex.crawler.core.grid.impl.ignite.storage;

import java.util.Optional;
import java.util.function.BiPredicate;

import org.apache.ignite.Ignite;
import org.apache.ignite.table.RecordView;
import org.apache.ignite.table.Table;
import org.apache.ignite.table.Tuple;

import com.norconex.crawler.core.grid.storage.GridQueue;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Getter;
import lombok.NonNull;

class IgniteGridQueue<T> implements GridQueue<T> {

    private final String name;
    // we use a set to ensure uniqueness on key.
    //    private final IgniteSet<String> idSet;
    //    private final IgniteQueue<QueueEntry<T>> queue;

    @Getter
    private final Class<? extends T> type;

    private final Table table;
    private final Ignite ignite;
    private final RecordView<Tuple> view;

    @NonNull
    public IgniteGridQueue(Ignite ignite, String name,
            Class<? extends T> type) {
        this.type = type;
        this.name = name;
        this.ignite = ignite;

        ignite.sql().execute(null, """
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                key STRING NOT NULL UNIQUE,
                value STRING NOT NULL
            );""".formatted(name));
        table = ignite.tables().table(name);
        view = table.recordView();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void clear() {
        //TODO shall we delete all with SQL instead?
        ignite.catalog().dropTable(name);
        //        idSet.clear();
        //        queue.clear();
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT key, value FROM %s ORDER BY id"
                        .formatted(name))) {
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
        try (var resultSet = ignite.sql().execute(
                null,
                "SELECT 1 FROM %s WHERE key = ? LIMIT 1"
                        .formatted(name),
                key)) {
            return resultSet.hasNext();
        }
    }

    @Override
    public long size() {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT count(*) FROM %s".formatted(name))) {
            return resultSet.next().longValue(0);
        }
    }

    @Override
    public boolean put(String key, T object) {
        var row = Tuple.create()
                .set("key", key)
                .set("value", SerialUtil.toJsonString(object));
        // add if non-existent only
        return view.insert(null, row);
    }

    @Override
    public Optional<T> poll() {
        return ignite.transactions().runInTransaction(tx -> {
            var resultSet = ignite.sql().execute(
                    tx,
                    "SELECT id, value FROM %s ORDER BY id LIMIT 1"
                            .formatted(name));
            T value = null;
            if (resultSet.hasNext()) {
                var row = resultSet.next();
                var id = row.longValue(0);
                value = SerialUtil.fromJson(row.stringValue(1), type);
                ignite.sql().execute(tx,
                        "DELETE FROM %s WHERE id = ?".formatted(name),
                        id);
                tx.commit();
            }
            return Optional.ofNullable(value);
        });
    }
}
