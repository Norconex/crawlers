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

    // NOTE: Hazelcast may serialize the MapStore factory when broadcasting
    // dynamic configuration across the cluster. Keep only compact-serializable
    // fields here (e.g., Strings) and reconstruct runtime-only objects as
    // needed.
    private String valueClassName;
    private transient HazelcastInstance hazelcastInstance;
    private String hazelcastInstanceName;

    private static final String TYPE_REGISTRY_MAP = "__cache_types";
    private static final String PROP_VALUE_CLASS_NAME = "value-class-name";
    private static final long TYPE_LOOKUP_TIMEOUT_MS = 2_000;
    private static final long TYPE_LOOKUP_POLL_MS = 50;

    @Override
    public void setValueClass(Class<?> valueClass) {
        this.valueClassName = valueClass == null ? null : valueClass.getName();
    }

    @Override
    public Class<?> getValueClass() {
        if (valueClassName == null) {
            return null;
        }
        try {
            return Class.forName(valueClassName);
        } catch (Exception e) {
            LOG.warn("Could not load valueClass '{}' from factory: {}",
                    valueClassName, e.getMessage());
            return null;
        }
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
        // Determine the value class for this specific map.
        // IMPORTANT: Hazelcast may reuse the same factory instance for
        // multiple maps (e.g., via the "default" map config). Therefore,
        // we must not rely on a single mutable `valueClass` field as the
        // authoritative type for all maps.
        final Class<?> vc;
        if (TYPE_REGISTRY_MAP.equals(mapName)) {
            vc = String.class;
        } else {
            vc = resolveValueClass(hz, mapName, properties);
        }

        LOG.debug("Creating typed MapStore for map '{}' and type {}",
                mapName, vc.getName());

        // Create an underlying string-based JDBC store and return a
        // MapStore that (de)serializes values to/from JSON strings.
        final var stringStore = new StringJdbcMapStore();

        return new TypedJdbcMapStore(stringStore, vc, hz,
                properties, mapName);
    }

    private Class<?> resolveValueClass(
            HazelcastInstance hz, String mapName, Properties properties) {
        // First, honor an explicit per-map type hint from MapStore config.
        // This is critical because Hazelcast can create MapStores from
        // partition-operation threads where remote calls are forbidden.
        if (properties != null) {
            var explicitType = properties.getProperty(PROP_VALUE_CLASS_NAME);
            if (explicitType != null && !explicitType.isBlank()) {
                try {
                    var cls = Class.forName(explicitType.trim());
                    LOG.debug(
                            "Loaded valueClass {} for map '{}' from MapStore property '{}'",
                            cls.getName(), mapName, PROP_VALUE_CLASS_NAME);
                    return cls;
                } catch (Exception e) {
                    LOG.warn(
                            "Invalid '{}' property value '{}' for map '{}'; falling back: {}",
                            PROP_VALUE_CLASS_NAME, explicitType, mapName,
                            e.getMessage());
                }
            }
        }

        // Prefer the type registry since it's per-map and durable.
        String typeName = null;
        try {
            var typeRegistry = hz.getMap(TYPE_REGISTRY_MAP);
            var deadline = System.currentTimeMillis() + TYPE_LOOKUP_TIMEOUT_MS;
            while (typeName == null && System.currentTimeMillis() <= deadline) {
                typeName = (String) typeRegistry.get(mapName);
                if (typeName != null) {
                    break;
                }
                try {
                    Thread.sleep(TYPE_LOOKUP_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not read type registry for map '{}': {}",
                    mapName, e.toString());
        }

        if (typeName != null) {
            try {
                var cls = Class.forName(typeName);
                LOG.debug(
                        "Loaded valueClass {} for map '{}' from type registry",
                        cls.getName(), mapName);
                return cls;
            } catch (Exception e) {
                LOG.warn(
                        "Invalid valueClass '{}' in type registry for map '{}'; falling back: {}",
                        typeName, mapName, e.getMessage());
            }
        }

        if (valueClassName != null) {
            try {
                var cls = Class.forName(valueClassName);
                LOG.debug(
                        "Using injected valueClass {} for map '{}' (type registry missing)",
                        cls.getName(), mapName);
                return cls;
            } catch (Exception e) {
                LOG.warn(
                        "Invalid injected valueClass '{}' for map '{}'; falling back: {}",
                        valueClassName, mapName, e.getMessage());
            }
        }

        LOG.warn(
                "valueClass not set and not found in type registry for map '{}'; defaulting to String",
                mapName);
        return String.class;
    }
}
