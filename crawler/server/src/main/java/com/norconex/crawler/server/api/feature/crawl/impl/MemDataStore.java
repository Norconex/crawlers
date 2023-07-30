/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.feature.crawl.impl;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.commons.lang.map.FifoMap;
import com.norconex.crawler.core.store.DataStore;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemDataStore<T> implements DataStore<T> {

    private final FifoMap<String, T> map;
    private String name;
    private int maxEntries;
    private boolean maxReached;

    protected MemDataStore(@NonNull String name, int maxEntries) {
        this.name = name;
        this.maxEntries = maxEntries;
        map = new FifoMap<>(maxEntries);
    }

    @Override
    public String getName() {
        return name;
    }
    String rename(String newName) {
        var oldName = name;
        name = newName;
        return oldName;
    }

    @Override
    public void save(String id, @NonNull T object) {
        map.put(id, object);
        if (map.size() == maxEntries && !maxReached) {
            LOG.info("""
                Max number of entries reached for memory store ({}).\s\
                Least recent entries will automatically be removed to\s\
                permit new ones.""");
            maxReached = true;
        }
    }

    @Override
    public Optional<T> find(String id) {
        return Optional.ofNullable(map.get(id));
    }

    @Override
    public Optional<T> findFirst() {
        return map
                .values()
                .stream()
                .findFirst();
    }

    @Override
    public boolean exists(String id) {
        return map.containsKey(id);
    }

    @Override
    public long count() {
        return map.size();
    }

    @Override
    public boolean delete(String id) {
        return map.remove(id) != null;
    }

    @Override
    public Optional<T> deleteFirst() {
        var id = map
                .keySet()
                .stream()
                .findFirst()
                .orElse(null);
        if (id != null) {
            var removed = map.remove(id);
            return Optional.ofNullable(removed);
        }
        return Optional.empty();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void close() {
        map.clear();
    }

    // returns true if was all read
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        for (Entry<String, T> en : map.entrySet()) {
            if (!predicate.test(en.getKey(), en.getValue())) {
                return false;
            }
        }
        return true;
    }
}
