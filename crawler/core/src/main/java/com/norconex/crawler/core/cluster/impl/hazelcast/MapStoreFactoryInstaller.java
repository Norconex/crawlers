package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcMapStoreFactory;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper to install a {@link TypedJdbcMapStoreFactory} into a
 * Hazelcast {@link Config} for a given map name. Installation is
 * idempotent and safe to call multiple times.
 */
@Slf4j
public final class MapStoreFactoryInstaller {

    private MapStoreFactoryInstaller() {
    }

    // simple per-map lock objects to serialize install attempts
    private static final ConcurrentHashMap<String, Object> INSTALL_LOCKS =
            new ConcurrentHashMap<>();

    /**
     * Ensure the map-store factory is installed for the given map.
     * If a factory is already present and is a {@link TypedJdbcMapStoreFactory},
     * its valueClass will be updated. If the YAML configured a string-based
     * store by class name, it will be replaced by a factory implementation.
     *
     * This method serializes concurrent installation attempts per map name
     * within the same JVM to avoid redundant work.
     *
     * @return true if an installation or update was performed; false if no-op
     */
    public static boolean installTypedFactoryIfNeeded(
            Config cfg, String mapName, Class<?> valueType) {
        Object lock = INSTALL_LOCKS.computeIfAbsent(mapName, k -> new Object());
        synchronized (lock) {
            MapConfig mapConfig = cfg.getMapConfig(mapName);
            if (mapConfig == null) {
                return false;
            }
            MapStoreConfig msc = mapConfig.getMapStoreConfig();
            if (msc == null || !msc.isEnabled()) {
                return false;
            }

            Object factoryImpl = msc.getFactoryImplementation();
            if (factoryImpl instanceof TypedJdbcMapStoreFactory) {
                ((TypedJdbcMapStoreFactory) factoryImpl)
                        .setValueClass(valueType);
                LOG.debug(
                        "Updated existing TypedJdbcMapStoreFactory for map {}",
                        mapName);
                return true;
            }

            // If an implementation is already present and is not our factory,
            // we do not override it.
            if (factoryImpl != null) {
                LOG.debug(
                        "Map {} already has a custom factory; skipping install",
                        mapName);
                return false;
            }

            // If YAML specified a string-based store class name, replace it
            String className = msc.getClassName();
            if (className != null && className.endsWith("StringJdbcMapStore")) {
                var factory = new TypedJdbcMapStoreFactory(valueType);
                msc.setFactoryImplementation(factory);
                msc.setClassName(null);
                LOG.debug("Installed TypedJdbcMapStoreFactory for map {}",
                        mapName);
                return true;
            }

            // If YAML specified a factory-class-name pointing to our
            // TypedJdbcMapStoreFactory, replace it with an instantiated
            // factory that already has the valueClass set.
            String factoryClassName = msc.getFactoryClassName();
            if (factoryClassName != null
                    && factoryClassName.equals(
                            TypedJdbcMapStoreFactory.class.getName())) {
                var factory = new TypedJdbcMapStoreFactory(valueType);
                msc.setFactoryImplementation(factory);
                msc.setFactoryClassName(null);
                LOG.debug(
                        "Installed TypedJdbcMapStoreFactory (impl) for map {}",
                        mapName);
                return true;
            }

            LOG.debug(
                    "No suitable map-store class-name to replace for map {}; no-op",
                    mapName);
            return false;
        }
    }
}
