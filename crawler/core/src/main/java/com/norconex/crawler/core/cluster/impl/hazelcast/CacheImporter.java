package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.SerializedCache;
import com.norconex.crawler.core.cluster.SerializedCache.CacheType;
import com.norconex.crawler.core.cluster.SerializedCache.SerializedEntry;
import com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcMapStoreFactory;
import com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcQueueStoreFactory;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class CacheImporter {

    static void importCaches(HazelcastCacheManager manager,
            List<SerializedCache> caches) {
        var hazelcast = manager.getHazelcastInstance();

        for (var serialCache : caches) {
            processCache(manager, serialCache, hazelcast);
        }
    }

    private static void processCache(HazelcastCacheManager manager,
            SerializedCache serialCache, HazelcastInstance hazelcast) {
        Class<?> targetClass = determineTargetClass(serialCache);

        if (CacheType.QUEUE.equals(serialCache.getCacheType())) {
            configureQueueStore(hazelcast, serialCache.getCacheName(),
                    targetClass);
            populateQueue(manager, serialCache, targetClass);
        } else {
            configureMapStore(hazelcast, serialCache.getCacheName(),
                    targetClass);
            populateMap(manager, serialCache, targetClass);
        }
    }

    private static Class<?> determineTargetClass(SerializedCache serialCache) {
        Class<?> targetClass = String.class;
        if (serialCache.getClassName() != null) {
            try {
                targetClass = Class.forName(serialCache.getClassName());
            } catch (ClassNotFoundException e) {
                LOG.debug("Could not load class {} for cache {}; "
                        + "defaulting to String",
                        serialCache.getClassName(),
                        serialCache.getCacheName());
                targetClass = String.class;
            }
        }
        return targetClass;
    }

    private static void configureQueueStore(HazelcastInstance hazelcast,
            String cacheName, Class<?> targetClass) {
        var cfg = hazelcast.getConfig();
        var queueConfig = cfg.getQueueConfig(cacheName);
        var storeConfig = queueConfig.getQueueStoreConfig();
        if (storeConfig != null) {
            var factory = storeConfig.getFactoryImplementation();
            if (factory == null) {
                factory = new TypedJdbcQueueStoreFactory();
                storeConfig.setFactoryImplementation(factory);
            }
            if (factory instanceof LazyTypedStoreFactory lazy) {
                lazy.setValueClass(targetClass);
                lazy.setHazelcastInstance(hazelcast);
            }
        }
    }

    private static void configureMapStore(HazelcastInstance hazelcast,
            String cacheName, Class<?> targetClass) {
        var cfg = hazelcast.getConfig();
        var mapConfig = cfg.getMapConfig(cacheName);
        var storeConfig = mapConfig.getMapStoreConfig();
        if (storeConfig != null) {
            var factory = storeConfig.getFactoryImplementation();
            if (factory == null) {
                factory = new TypedJdbcMapStoreFactory();
                storeConfig.setFactoryImplementation(factory);
            }
            if (factory instanceof LazyTypedStoreFactory lazy) {
                lazy.setValueClass(targetClass);
                lazy.setHazelcastInstance(hazelcast);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void populateQueue(HazelcastCacheManager manager,
            SerializedCache serialCache, Class<?> targetClass) {
        var queue = manager.getCacheQueue(
                serialCache.getCacheName(), (Class<Object>) targetClass);

        for (SerializedEntry entry : serialCache) {
            var val = deserializeValue(entry.getJson(), targetClass,
                    serialCache.getCacheName());
            queue.add(val);
        }
    }

    private static void populateMap(HazelcastCacheManager manager,
            SerializedCache serialCache, Class<?> targetClass) {
        IMap<String, Object> imap = manager.getHazelcastMap(
                serialCache.getCacheName());

        Map<String, Object> batch = new ListOrderedMap<>();

        for (SerializedEntry entry : serialCache) {
            var val = deserializeValue(entry.getJson(), targetClass,
                    serialCache.getCacheName());
            // Hazelcast IMap does not permit null values; skip null entries.
            if (val == null) {
                LOG.debug("Skipping null value for key '{}' in cache '{}'",
                        entry.getKey(), serialCache.getCacheName());
                continue;
            }
            batch.put(entry.getKey(), val);
            if (batch.size() >= HazelcastCacheManager.BATCH_SIZE) {
                imap.putAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            imap.putAll(batch);
        }
    }

    private static Object deserializeValue(String str, Class<?> targetClass,
            String cacheName) {
        Object val = null;
        if (str != null && targetClass != String.class) {
            try {
                val = SerialUtil.fromJson(str, targetClass);
            } catch (Exception ex) {
                LOG.debug("Could not deserialize entry for cache '{}': {}",
                        cacheName, ex.toString());
                val = str;
            }
        } else {
            val = str;
        }
        return val;
    }
}
