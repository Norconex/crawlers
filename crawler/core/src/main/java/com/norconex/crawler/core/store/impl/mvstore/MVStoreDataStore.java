/* Copyright 2020-2022 Norconex Inc.
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
import java.util.Optional;
import java.util.function.BiPredicate;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.crawler.core.store.DataStore;

import lombok.NonNull;

public class MVStoreDataStore<T> implements DataStore<T> {

    private final MVMap<String, T> map;
    private String name;

    MVStoreDataStore(MVStore mvstore, String name) {
        requireNonNull(mvstore, "'mvstore' must not be null.");
        this.name = requireNonNull(name, "'name' must not be null.");
        map = mvstore.openMap(name);
    }

    @Override
    public String getName() {
        return name;
    }
    String rename(String newName) {
        var oldName = name;
        map.store.renameMap(map, newName);
        name = newName;
        return oldName;
    }

    @Override
    public void save(String id, @NonNull T object) {
        // MVStore doc says values cannot be changed after stored...
        // but testings shows it is OK for us and faster.
        // If issues arise, re-introduce cloning.
        //    map.put(id, BeanUtil.clone(object))
        map.put(id, object);
    }

    @Override
    public Optional<T> find(String id) {
        return Optional.ofNullable(map.get(id));
    }

    @Override
    public Optional<T> findFirst() {
        var id = map.firstKey();
        if (id != null) {
            return Optional.ofNullable(map.get(id));
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
        //NOOP, Closed implicitly when engine is closed.
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

    MVMap<String, T> getMVMap() {
        return map;
    }
}
