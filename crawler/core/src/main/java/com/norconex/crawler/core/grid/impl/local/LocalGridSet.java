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
package com.norconex.crawler.core.grid.impl.local;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.crawler.core.grid.GridSet;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Getter;
import lombok.NonNull;

public class LocalGridSet<T> implements GridSet<T> {
    private final MVMap<String, Boolean> map;
    @Getter
    private final Class<? extends T> type;
    @Getter
    private String name;

    public LocalGridSet(
            @NonNull MVStore mvstore,
            @NonNull String name,
            @NonNull Class<? extends T> type) {
        this.type = type;
        this.name = name;
        map = mvstore.openMap(name);
    }

    @Override
    public boolean contains(Object key) {
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

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public synchronized boolean add(T object) {
        var newEntry = SerialUtil.toJsonString(object);
        return !Objects.equals(Boolean.TRUE, map.put(newEntry, Boolean.TRUE));
    }

    @Override
    public boolean forEach(Predicate<T> predicate) {
        for (String key : map.keySet()) {
            if (!predicate.test(toObject(key))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        for (Entry<String, Boolean> en : map.entrySet()) {
            if (!predicate.test(null, toObject(en.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private T toObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return SerialUtil.fromJson(json, type);
    }
}
