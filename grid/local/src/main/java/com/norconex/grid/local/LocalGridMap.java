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
package com.norconex.grid.local;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.SerialUtil;
import com.norconex.grid.core.util.SerializableUnaryOperator;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalGridMap<T> implements GridMap<T> {

    private final MVMap<String, String> map;
    @Getter
    private final Class<? extends T> type;
    @Getter
    private String name;

    public LocalGridMap(
            @NonNull MVStore mvstore,
            @NonNull String name,
            @NonNull Class<? extends T> type) {
        this.type = type;
        this.name = name;
        map = mvstore.openMap(name);
    }

    @Override
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public long size() {
        return map.sizeAsLong();
    }

    @Override
    public void clear() {
        map.clear();
    }

    // returns true if all read
    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        for (Entry<String, String> en : map.entrySet()) {
            if (!predicate.test(en.getKey(), toObject(en.getValue()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean put(String key, T object) {
        var newEntry = SerialUtil.toJsonString(object);
        return !Objects.equals(newEntry, map.put(key, newEntry));
    }

    @Override
    public boolean update(String key, SerializableUnaryOperator<T> updater) {
        var originalValue = new MutableObject<>();
        var newValue = map.compute(key, (k, v) -> {
            originalValue.setValue(v);
            return SerialUtil.toJsonString(updater.apply(toObject(v)));
        });
        return !Objects.equals(originalValue.getValue(), newValue);
    }

    @Override
    public T get(String key) {
        return toObject(map.get(key));
    }

    @Override
    public boolean delete(String key) {
        return map.remove(key) != null;
    }

    private T toObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return SerialUtil.fromJson(json, type);
    }

}
