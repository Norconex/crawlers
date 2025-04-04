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
package com.norconex.grid.core.mocks;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;

import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.util.SerialUtil;

import lombok.Getter;
import lombok.NonNull;

public class MockGridQueue<T> implements GridQueue<T> {

    private final ListOrderedMap<String, String> map =
            ListOrderedMap.listOrderedMap(new ConcurrentHashMap<>());

    @Getter
    private final Class<? extends T> type;
    @Getter
    private String name;

    public MockGridQueue(
            @NonNull String name,
            @NonNull Class<? extends T> type) {
        this.type = type;
        this.name = name;
    }

    @Override
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public long size() {
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

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
        if (map.containsKey(key)) {
            return false;
        }
        var newEntry = SerialUtil.toJsonString(object);
        return !Objects.equals(
                newEntry, map.put(key, SerialUtil.toJsonString(object)));
    }

    @Override
    public Optional<T> poll() {
        if (map.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(toObject(map.remove(0)));
    }

    private T toObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return SerialUtil.fromJson(json, type);
    }
}
