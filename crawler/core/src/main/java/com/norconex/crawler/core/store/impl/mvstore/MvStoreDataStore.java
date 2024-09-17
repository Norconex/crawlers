/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.mvstore;

import static java.util.Objects.requireNonNull;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.impl.SerialUtil;

import lombok.NonNull;

public class MvStoreDataStore<T> implements DataStore<T> {

    private final MVMap<String, String> map;
    private String storeName;
    private final Class<? extends T> type;

    protected MvStoreDataStore(
            @NonNull MVStore mvstore,
            @NonNull String storeName,
            @NonNull Class<? extends T> type) {
        requireNonNull(mvstore, "'mvstore' must not be null.");
        this.storeName = requireNonNull(storeName, "'name' must not be null.");
        this.type = type;
        map = mvstore.openMap(storeName);
    }

    @Override
    public String getName() {
        return storeName;
    }

    String rename(String newName) {
        var oldName = storeName;
        map.store.renameMap(map, newName);
        storeName = newName;
        return oldName;
    }

    @Override
    public boolean save(String id, @NonNull T object) {
        var newEntry = SerialUtil.toJsonString(object);
        return !Objects.equals(newEntry, map.put(id, newEntry));
    }

    @Override
    public Optional<T> find(String id) {
        return toObject(map.get(id));
    }

    @Override
    public Optional<T> findFirst() {
        var id = map.firstKey();
        if (id != null) {
            return toObject(map.get(id));
        }
        return Optional.empty();
    }

    @Override
    public boolean exists(String id) {
        return map.containsKey(id);
    }

    @Override
    public long count() {
        return map.sizeAsLong();
    }

    @Override
    public boolean delete(String id) {
        return map.remove(id) != null;
    }

    @Override
    public Optional<T> deleteFirst() {
        var id = map.firstKey();
        if (id != null) {
            var removed = map.remove(id);
            return toObject(removed);
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
        //NOOP, Closed implicitly when engine is closed.
    }

    // returns true if was all read
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        for (Entry<String, String> en : map.entrySet()) {
            if (!predicate.test(
                    en.getKey(),
                    toObject(en.getValue()).orElse(null))) {
                return false;
            }
        }
        return true;
    }

    private Optional<T> toObject(String json) {
        if (StringUtils.isBlank(json)) {
            return Optional.empty();
        }
        return Optional.ofNullable(SerialUtil.fromJson(json, type));
    }

    MVMap<String, String> getMVMap() {
        return map;
    }
}
