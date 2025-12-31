package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.util.Properties;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapStore;
import com.hazelcast.map.MapStoreFactory;
import com.norconex.crawler.core.cluster.impl.hazelcast.LazyTypedStoreFactory;

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
        implements MapStoreFactory<String, Object>, LazyTypedStoreFactory {

    private Class<?> valueClass;
    private HazelcastInstance hazelcastInstance;
    private String hazelcastInstanceName;

    @Override
    public void setValueClass(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    @Override
    public Class<?> getValueClass() {
        return valueClass;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hazelcastInstance = hz;
        this.hazelcastInstanceName = hz.getName();
    }

    @Override
    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    @Override
    public MapStore<String, Object> newMapStore(
            String mapName, Properties properties) {
        // Get HazelcastInstance - either from injection or from registry
        HazelcastInstance hz = hazelcastInstance;
        if (hz == null && hazelcastInstanceName != null) {
            hz = com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastCacheManager
                    .getHazelcastInstance(hazelcastInstanceName);
        }
        if (hz == null) {
            // Fallback: try to get any available instance
            var instances =
                    com.hazelcast.core.Hazelcast.getAllHazelcastInstances();
            if (!instances.isEmpty()) {
                hz = instances.iterator().next();
                LOG.debug("Using fallback HazelcastInstance for map '{}'",
                        mapName);
            }
        }

        // Defensive: fail fast if hazelcastInstance is not available
        if (hz == null) {
            throw new IllegalStateException(
                    "HazelcastInstance is not available for TypedJdbcMapStoreFactory "
                            +
                            "for map '" + mapName + "'. " +
                            "Ensure HazelcastCacheManager is properly initialized.");
        }
        // If valueClass was not set (for example when Hazelcast
        // instantiated the factory reflectively from config before we
        // had a chance to set it), default to String for the special
        // type-registry map and otherwise log and default to String to
        // avoid NPE.
        Class<?> vc = valueClass;
        if (vc == null) {
            if ("__cache_types".equals(mapName)) {
                LOG.debug("Defaulting valueClass to String for map '{}'",
                        mapName);
            } else {
                LOG.warn("valueClass not set on TypedJdbcMapStoreFactory "
                        + "for map '{}'; defaulting to String", mapName);
            }
            vc = String.class;
        }

        LOG.debug("Creating typed MapStore for map '{}' and type {}",
                mapName, vc.getName());

        // Create an underlying string-based JDBC store and return a
        // MapStore that (de)serializes values to/from JSON strings.
        final var stringStore = new StringJdbcMapStore();

        return new TypedJdbcMapStore(stringStore, vc, hz,
                properties, mapName);
    }
}
