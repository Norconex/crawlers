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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import org.h2.mvstore.MVMap;

import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.QueryFilter;
import com.norconex.crawler.core.util.SerialUtil;

/**
 * File-backed {@link CacheMap} implementation using H2 MVStore.
 * Values are stored as JSON strings in the underlying {@link MVMap}
 * for type safety and debuggability. All compound operations that
 * require atomicity are synchronized (safe for single-JVM use).
 *
 * <p>Queries via {@link QueryFilter} are evaluated using field-level
 * reflection, same approach as the in-memory implementation.</p>
 *
 * @param <T> the value type
 */
public class MVStoreCacheMap<T> implements CacheMap<T> {

    private final MVMap<String, String> map;
    private final Class<T> valueType;
    private final String name;
    private final boolean persistent;

    public MVStoreCacheMap(
            MVMap<String, String> map,
            Class<T> valueType,
            String name,
            boolean persistent) {
        this.map = map;
        this.valueType = valueType;
        this.name = name;
        this.persistent = persistent;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public long size() {
        return map.sizeAsLong();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public void put(String key, T value) {
        map.put(key, serialize(value));
    }

    @Override
    public void putAll(Map<String, T> entries) {
        entries.forEach((k, v) -> map.put(k, serialize(v)));
    }

    @Override
    public Optional<T> get(String key) {
        return Optional.ofNullable(deserialize(map.get(key)));
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    @Override
    public T getOrDefault(String key, T defaultValue) {
        var json = map.get(key);
        return json != null ? deserialize(json) : defaultValue;
    }

    @Override
    public synchronized T computeIfAbsent(
            String key,
            Function<String, ? extends T> mappingFunction) {
        var json = map.get(key);
        if (json != null) {
            return deserialize(json);
        }
        var newValue = mappingFunction.apply(key);
        if (newValue != null) {
            map.put(key, serialize(newValue));
        }
        return newValue;
    }

    @Override
    public synchronized Optional<T> computeIfPresent(
            String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        var json = map.get(key);
        if (json == null) {
            return Optional.empty();
        }
        var oldValue = deserialize(json);
        var newValue = remappingFunction.apply(key, oldValue);
        if (newValue != null) {
            map.put(key, serialize(newValue));
        } else {
            map.remove(key);
        }
        return Optional.ofNullable(newValue);
    }

    @Override
    public synchronized Optional<T> compute(
            String key,
            BiFunction<String, ? super T, ? extends T> remappingFunction) {
        var json = map.get(key);
        var oldValue = json != null ? deserialize(json) : null;
        var newValue = remappingFunction.apply(key, oldValue);
        if (newValue != null) {
            map.put(key, serialize(newValue));
        } else if (json != null) {
            map.remove(key);
        }
        return Optional.ofNullable(newValue);
    }

    @Override
    public synchronized T merge(
            String key, T value,
            BiFunction<? super T, ? super T, ? extends T> remappingFunction) {
        var json = map.get(key);
        var oldValue = json != null ? deserialize(json) : null;
        var newValue = oldValue == null
                ? value
                : remappingFunction.apply(oldValue, value);
        if (newValue != null) {
            map.put(key, serialize(newValue));
        } else {
            map.remove(key);
        }
        return newValue;
    }

    @Override
    public synchronized T putIfAbsent(String key, T value) {
        var json = map.get(key);
        if (json != null) {
            return deserialize(json);
        }
        map.put(key, serialize(value));
        return null;
    }

    @Override
    public synchronized boolean replace(String key, T oldValue, T newValue) {
        var json = map.get(key);
        if (json == null) {
            return false;
        }
        var current = deserialize(json);
        if (Objects.equals(current, oldValue)) {
            map.put(key, serialize(newValue));
            return true;
        }
        return false;
    }

    @Override
    public List<T> query(QueryFilter filter) {
        return filterStream(filter).toList();
    }

    @Override
    public Iterator<T> queryIterator(QueryFilter filter) {
        return query(filter).iterator();
    }

    @Override
    public long count(QueryFilter filter) {
        return filterStream(filter).count();
    }

    @Override
    public void delete(QueryFilter filter) {
        if (filter == null || filter.getFieldName() == null) {
            map.clear();
            return;
        }
        // Collect keys to remove first to avoid ConcurrentModification
        var keysToRemove = new ArrayList<String>();
        for (var entry : map.entrySet()) {
            var val = deserialize(entry.getValue());
            if (matches(val, filter)) {
                keysToRemove.add(entry.getKey());
            }
        }
        keysToRemove.forEach(map::remove);
    }

    @Override
    public void forEach(BiConsumer<String, ? super T> action) {
        for (var entry : map.entrySet()) {
            action.accept(entry.getKey(), deserialize(entry.getValue()));
        }
    }

    @Override
    public List<String> keys() {
        return new ArrayList<>(map.keySet());
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

    private Stream<T> filterStream(QueryFilter filter) {
        if (filter == null || filter.getFieldName() == null) {
            return map.values().stream().map(this::deserialize);
        }
        return map.values().stream()
                .map(this::deserialize)
                .filter(v -> matches(v, filter));
    }

    private boolean matches(T value, QueryFilter filter) {
        if (value == null || filter == null
                || filter.getFieldName() == null) {
            return true;
        }
        try {
            var field = findField(value.getClass(), filter.getFieldName());
            if (field == null) {
                return false;
            }
            field.setAccessible(true); //NOSONAR
            var fieldValue = field.get(value);
            return Objects.equals(
                    Objects.toString(fieldValue, null),
                    Objects.toString(filter.getFieldValue(), null));
        } catch (Exception e) {
            return false;
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        for (var c = clazz; c != null && c != Object.class;
                c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                // continue up the hierarchy
            }
        }
        return null;
    }
}
