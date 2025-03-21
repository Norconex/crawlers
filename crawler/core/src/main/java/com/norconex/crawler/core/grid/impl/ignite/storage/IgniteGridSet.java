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

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.ignite.Ignite;
import org.apache.ignite.table.RecordView;
import org.apache.ignite.table.Table;
import org.apache.ignite.table.Tuple;

import com.norconex.crawler.core.grid.storage.GridSet;
import com.norconex.crawler.core.grid.storage.GridStore;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.StandardException;

class IgniteGridSet<T> implements GridSet<T> {

    private final String name;
    @Getter
    private final Class<? extends T> type;
    private final Ignite ignite;
    private final Table table;
    private final RecordView<Tuple> view;

    @NonNull
    public IgniteGridSet(Ignite ignite, String name, Class<? extends T> type) {
        this.type = type;
        this.name = name;
        this.ignite = ignite;

        ignite.sql().execute(null, """
                CREATE TABLE IF NOT EXISTS %s (
                    value STRING PRIMARY KEY
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
    }

    /**
     * Loops through all elements of this set or until the predicate returns
     * <code>false</code>.
     * @param predicate the predicate applied to each items
     * @return <code>true</code> if the predicate returned <code>true</code>
     *   for all objects.
     */
    @Override
    public boolean forEach(Predicate<T> predicate) {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT value FROM %s".formatted(name))) {
            while (resultSet.hasNext()) {
                var row = resultSet.next();
                if (!predicate.test(
                        SerialUtil.fromJson(row.stringValue(0), type))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Key is always <code>null</code>. This method exists to conforms to
     * {@link GridStore}. Use {@link #forEach(Predicate)} for a more
     * appropriate method.
     */
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT value FROM %s".formatted(name))) {
            while (resultSet.hasNext()) {
                var row = resultSet.next();
                if (!predicate.test(null,
                        SerialUtil.fromJson(row.stringValue(0), type))) {
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
    public boolean contains(Object value) {
        return view.get(null,
                Tuple.create().set("value",
                        SerialUtil.toJsonString(value))) != null;

        //        try (var resultSet = ignite.sql().execute(
        //                null,
        //                "SELECT value FROM %s WHERE key = ? LIMIT 1"
        //                        .formatted(internalName),
        //                SerialUtil.toJsonString(value))) {
        //            return resultSet.hasNext();
        //        }
    }

    @Override
    public long size() {
        try (var resultSet = ignite.sql().execute(
                null, "SELECT count(*) FROM %s".formatted(name))) {
            return resultSet.next().longValue(0);
        }
    }

    @Override
    public boolean add(T value) {
        return view.insert(null,
                Tuple.create().set("value", SerialUtil.toJsonString(value)));
    }

    @StandardException
    static class BreakException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
