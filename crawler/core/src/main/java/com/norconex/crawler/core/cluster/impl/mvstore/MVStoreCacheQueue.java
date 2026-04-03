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
package com.norconex.crawler.core.cluster.impl.mvstore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.h2.mvstore.MVMap;

import com.norconex.crawler.core.cluster.CacheQueue;
import com.norconex.crawler.core.util.SerialUtil;

/**
 * File-backed FIFO {@link CacheQueue} implementation using H2 MVStore.
 * Uses an {@link MVMap}&lt;Long, String&gt; where the Long key is an
 * auto-incrementing sequence number and the String value is the
 * JSON-serialized item. MVMap's B+ tree ordering guarantees that
 * {@code firstKey()} always returns the oldest entry (FIFO head).
 *
 * @param <T> the element type
 */
public class MVStoreCacheQueue<T> implements CacheQueue<T> {

    private final MVMap<Long, String> map;
    private final Class<T> valueType;
    private final String name;
    private final AtomicLong tailSeq;

    public MVStoreCacheQueue(
            MVMap<Long, String> map,
            Class<T> valueType,
            String name) {
        this.map = map;
        this.valueType = valueType;
        this.name = name;
        // Initialize tail sequence from existing data (for resume support)
        this.tailSeq = new AtomicLong(
                map.isEmpty() ? 0 : map.lastKey());
    }

    @Override
    public void add(T item) {
        map.put(tailSeq.incrementAndGet(), serialize(item));
    }

    @Override
    public synchronized T poll() {
        if (map.isEmpty()) {
            return null;
        }
        var key = map.firstKey();
        var json = map.remove(key);
        return deserialize(json);
    }

    @Override
    public synchronized List<T> pollBatch(int batchSize) {
        var batch = new ArrayList<T>(batchSize);
        for (var i = 0; i < batchSize; i++) {
            if (map.isEmpty()) {
                break;
            }
            var key = map.firstKey();
            var json = map.remove(key);
            batch.add(deserialize(json));
        }
        return batch;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void clear() {
        map.clear();
        tailSeq.set(0);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    private String serialize(T value) {
        if (value instanceof String str) {
            return str;
        }
        return SerialUtil.toJsonString(value);
    }

    @SuppressWarnings("unchecked")
    private T deserialize(String json) {
        if (json == null) {
            return null;
        }
        if (valueType == String.class) {
            return (T) json;
        }
        return SerialUtil.fromJson(json, valueType);
    }
}
