/* Copyright 2026 Norconex Inc.
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

package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.SerializationException;

import com.hazelcast.collection.QueueStore;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.NonNull;

/**
 * Typed wrapper around StringJdbcQueueStore that serializes objects to
 * JSON strings when storing and deserializes JSON strings when loading.
 * The underlying JDBC store persists strings; this wrapper converts the
 * typed values to/from strings.
 * @param <T> queue object type
 */
public class TypedJdbcQueueStore<T> implements QueueStore<T> {

    private final StringJdbcQueueStore delegate;
    private final Class<T> valueType;

    // Enforce HazelcastInstance usage only
    public TypedJdbcQueueStore(
            @NonNull HazelcastInstance hz,
            @NonNull String name,
            Properties props,
            Class<T> valueType) {
        delegate = new StringJdbcQueueStore(hz, name, props);
        this.valueType = valueType;
    }

    @Override
    public void store(Long key, T value) {
        if (value == null) {
            delegate.store(key, null);
            return;
        }
        String toStore;
        if (value instanceof String || valueType == String.class) {
            toStore = (String) value;
        } else {
            try {
                toStore = SerialUtil.toJsonString(value);
            } catch (SerializationException e) {
                // fallback to toString
                toStore = value.toString();
            }
        }
        delegate.store(key, toStore);
    }

    @Override
    public void storeAll(Map<Long, T> map) {
        Map<Long, String> batch = new LinkedHashMap<>();
        for (Map.Entry<Long, T> e : map.entrySet()) {
            var v = e.getValue();
            if (v == null) {
                batch.put(e.getKey(), null);
            } else if (v instanceof String || valueType == String.class) {
                batch.put(e.getKey(), (String) v);
            } else {
                try {
                    batch.put(e.getKey(), SerialUtil.toJsonString(v));
                } catch (SerializationException ex) {
                    batch.put(e.getKey(), v.toString());
                }
            }
        }
        delegate.storeAll(batch);
    }

    @Override
    public void delete(Long key) {
        delegate.delete(key);
    }

    @Override
    public void deleteAll(Collection<Long> keys) {
        delegate.deleteAll(keys);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T load(Long key) {
        var s = delegate.load(key);
        if (s == null) {
            return null;
        }
        if (valueType == String.class) {
            return (T) s;
        }
        try {
            return SerialUtil.fromJson(s, valueType);
        } catch (Exception e) {
            return (T) s;
        }
    }

    @Override
    public Map<Long, T> loadAll(Collection<Long> keys) {
        var raw = delegate.loadAll(keys);
        Map<Long, T> converted = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : raw.entrySet()) {
            var s = e.getValue();
            if (s == null) {
                converted.put(e.getKey(), null);
            } else if (valueType == String.class) {
                @SuppressWarnings("unchecked")
                var cast = (T) s;
                converted.put(e.getKey(), cast);
            } else {
                try {
                    converted.put(e.getKey(),
                            SerialUtil.fromJson(s, valueType));
                } catch (Exception ex) {
                    @SuppressWarnings("unchecked")
                    var cast = (T) s;
                    converted.put(e.getKey(), cast);
                }
            }
        }
        return converted;
    }

    @Override
    public Set<Long> loadAllKeys() {
        var keys = delegate.loadAllKeys();
        // ensure ordering is maintained
        return new TreeSet<>(keys);
    }
}
