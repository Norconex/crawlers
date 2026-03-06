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
package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapStore;
import com.norconex.crawler.core.util.SerialUtil;

// Private nested class implementing both MapStore and lifecycle
final class TypedJdbcMapStore
        implements MapStore<String, Object> {

    private final StringJdbcMapStore stringStore;
    private final Class<?> valueClass;

    TypedJdbcMapStore(
            StringJdbcMapStore stringStore,
            Class<?> valueClass,
            HazelcastInstance hz,
            Properties props, String name) {
        this.stringStore = stringStore;
        this.valueClass = valueClass;
        stringStore.init(hz, props, name);
    }

    @Override
    public void store(String key, Object value) {
        if (value == null) {
            stringStore.delete(key);
            return;
        }
        if (value instanceof String str) {
            stringStore.store(key, str);
            return;
        }
        stringStore.store(key, SerialUtil.toJsonString(value));
    }

    @Override
    public void storeAll(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        Map<String, String> batch = new HashMap<>();
        for (var e : map.entrySet()) {
            var k = e.getKey();
            var v = e.getValue();
            if (v == null) {
                batch.put(k, null);
            } else if (v instanceof String str) {
                batch.put(k, str);
            } else {
                batch.put(k, SerialUtil.toJsonString(v));
            }
        }
        Map<String, String> toStore = batch.entrySet().stream()
                .filter(ent -> ent.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue));
        if (!toStore.isEmpty()) {
            stringStore.storeAll(toStore);
        }
        batch.entrySet().stream()
                .filter(ent -> ent.getValue() == null)
                .map(Map.Entry::getKey)
                .forEach(stringStore::delete);
    }

    @Override
    public void delete(String key) {
        stringStore.delete(key);
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        stringStore.deleteAll(keys);
    }

    @Override
    public Object load(String key) {
        var json = stringStore.load(key);
        if (json == null) {
            return null;
        }
        if (valueClass == String.class) {
            return json;
        }
        return SerialUtil.fromJson(json, valueClass);
    }

    @Override
    public Map<String, Object> loadAll(Collection<String> keys) {
        var stringMap = stringStore.loadAll(keys);
        Map<String, Object> out = new HashMap<>();
        for (var e : stringMap.entrySet()) {
            if (valueClass == String.class) {
                out.put(e.getKey(), e.getValue());
            } else {
                out.put(e.getKey(),
                        SerialUtil.fromJson(e.getValue(), valueClass));
            }
        }
        return out;
    }

    @Override
    public Iterable<String> loadAllKeys() {
        return stringStore.loadAllKeys();
    }
}
