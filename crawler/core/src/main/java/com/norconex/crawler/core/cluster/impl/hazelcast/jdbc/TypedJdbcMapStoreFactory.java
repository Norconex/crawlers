package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStoreFactory;
import com.hazelcast.map.MapStore;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory that produces MapStore instances which serialize/deserialize
 * values to/from JSON strings and delegate persistence to
 * {@link StringJdbcMapStore}.
 *
 * Install this factory programmatically (for example from
 * {@code HazelcastCacheManager#getCache(...)}) before the map is
 * created so the factory can be used by Hazelcast to create a typed
 * MapStore that keeps concrete objects in memory while persisting
 * string JSON to the backing store.
 */
@Slf4j
public class TypedJdbcMapStoreFactory
        implements MapStoreFactory<String, Object> {

    private volatile Class<?> valueClass;

    public TypedJdbcMapStoreFactory() {
    }

    public TypedJdbcMapStoreFactory(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    public void setValueClass(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    public Class<?> getValueClass() {
        return valueClass;
    }

    @Override
    public MapStore<String, Object> newMapStore(
            String mapName, Properties properties) {
        Objects.requireNonNull(valueClass,
                "valueClass must be set on TypedJdbcMapStoreFactory"
                        + " before creating a MapStore for: " + mapName);
        LOG.debug("Creating typed MapStore for map '{}' and type {}",
                mapName, valueClass.getName());

        // Create an underlying string-based JDBC store and return a
        // MapStore that (de)serializes values to/from JSON strings.
        final StringJdbcMapStore stringStore = new StringJdbcMapStore();
        final Class<?> vc = valueClass;

        return new DelegatingMapStore(stringStore, vc);
    }

    // Private nested class implementing both MapStore and lifecycle
    private static final class DelegatingMapStore
            implements MapStore<String, Object>, MapLoaderLifecycleSupport {

        private final StringJdbcMapStore stringStore;
        private final Class<?> valueClass;

        DelegatingMapStore(StringJdbcMapStore stringStore,
                Class<?> valueClass) {
            this.stringStore = stringStore;
            this.valueClass = valueClass;
        }

        @Override
        public void init(HazelcastInstance hzInstance, Properties storeProps,
                String storeName) {
            stringStore.init(hzInstance, storeProps, storeName);
        }

        @Override
        public void destroy() {
            stringStore.destroy();
        }

        @Override
        public void store(String key, Object value) {
            if (value == null) {
                stringStore.delete(key);
                return;
            }
            if (valueClass == String.class || value instanceof String) {
                stringStore.store(key, Objects.toString(value, null));
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
                } else if (valueClass == String.class || v instanceof String) {
                    batch.put(k, Objects.toString(v, null));
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
            String json = stringStore.load(key);
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
            Map<String, String> stringMap = stringStore.loadAll(keys);
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
}
