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

import static java.util.Optional.ofNullable;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

import org.apache.commons.lang3.StringUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.util.SerialUtil;

import lombok.Getter;
import lombok.NonNull;

public class LocalGridQueue<T> implements GridQueue<T> {

    private final AtomicLong sequence = new AtomicLong(0);
    private final MVMap<Long, String> sequenceQueue;
    private final MVMap<String, String> map;
    @Getter
    private final Class<? extends T> type;
    @Getter
    private String name;

    public LocalGridQueue(
            @NonNull MVStore mvstore,
            @NonNull String name,
            @NonNull Class<? extends T> type) {
        this.type = type;
        this.name = name;
        sequenceQueue = mvstore.openMap(name + "__seq");
        sequence.set(ofNullable(sequenceQueue.lastKey()).orElse(0L));
        map = mvstore.openMap(name);
    }

    @Override
    public boolean contains(Object key) {
        return sequenceQueue.containsKey(key);
    }

    @Override
    public long size() {
        return sequenceQueue.sizeAsLong();
    }

    @Override
    public void clear() {
        sequenceQueue.clear();
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
        return sequenceQueue.isEmpty();
    }

    @Override
    public boolean put(String key, T object) {
        var seq = sequence.incrementAndGet();
        sequenceQueue.put(seq, key);
        var newEntry = SerialUtil.toJsonString(object);
        return !Objects.equals(newEntry, map.put(key, newEntry));
    }

    @Override
    public Optional<T> poll() {
        var seq = sequenceQueue.firstKey();
        if (seq != null) {
            var key = sequenceQueue.remove(seq);
            return Optional.ofNullable(toObject(map.remove(key)));
        }
        return Optional.empty();
    }

    private T toObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return SerialUtil.fromJson(json, type);
    }
}
