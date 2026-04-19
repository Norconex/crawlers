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

    private static final String PROP_VALUE_CLASS_NAME = "value-class-name";

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
        // When Hazelcast creates this factory from the class name (rather than
        // a wired implementation), hazelcastInstanceName is not set yet.
        // Fall back to the name injected into store properties before startup.
        var effectiveInstanceName = hazelcastInstanceName != null
                ? hazelcastInstanceName
                : properties.getProperty("hz-instance-name");
        HazelcastInstance hz =
                com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastUtil
                        .resolveStoreInstance(
                                hazelcastInstance,
                                effectiveInstanceName,
                                mapName,
                                "map");

        // Determine the value class for this specific map.
        // IMPORTANT: Hazelcast may reuse the same factory instance for
        // multiple maps (e.g., via the "default" map config). Therefore,
        // we must not rely on a single mutable `valueClass` field as the
        // authoritative type for all maps.
        final Class<?> vc = resolveValueClass(mapName, properties);

        LOG.debug("Creating typed MapStore for map '{}' and type {}",
                mapName, vc.getName());

        // Create an underlying string-based JDBC store and return a
        // MapStore that (de)serializes values to/from JSON strings.
        final var stringStore = new StringJdbcMapStore();

        return new TypedJdbcMapStore(stringStore, vc, hz,
                properties, mapName);
    }

    private Class<?> resolveValueClass(String mapName, Properties properties) {
        // The value-class-name property is set programmatically in
        // HazelcastCluster.applyCacheTypes() before Hazelcast starts,
        // using the concrete type declared in CrawlDriver.cacheTypes().
        // The YAML provides a sensible base-type default for any map whose
        // type was not explicitly overridden.
        if (properties != null) {
            var explicitType = properties.getProperty(PROP_VALUE_CLASS_NAME);
            if (explicitType != null && !explicitType.isBlank()) {
                try {
                    var cls = Class.forName(explicitType.trim());
                    LOG.debug(
                            "Loaded valueClass {} for map '{}' from "
                                    + "MapStore property '{}'",
                            cls.getName(), mapName, PROP_VALUE_CLASS_NAME);
                    return cls;
                } catch (Exception e) {
                    LOG.warn(
                            "Invalid '{}' property '{}' for map '{}'; "
                                    + "falling back to String: {}",
                            PROP_VALUE_CLASS_NAME, explicitType, mapName,
                            e.getMessage());
                }
            }
        }
        if (valueClassName != null) {
            try {
                var cls = Class.forName(valueClassName);
                LOG.debug(
                        "Using injected valueClass {} for map '{}'",
                        cls.getName(), mapName);
                return cls;
            } catch (Exception e) {
                LOG.warn(
                        "Invalid injected valueClass '{}' for map '{}'; "
                                + "falling back to String: {}",
                        valueClassName, mapName, e.getMessage());
            }
        }
        LOG.warn("valueClass not configured for map '{}'; defaulting to "
                + "String", mapName);
        return String.class;
    }
}
