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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.SerializationException;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.CacheQueue;
import com.norconex.crawler.core.cluster.CacheSet;
import com.norconex.crawler.core.cluster.ClusterException;
import com.norconex.crawler.core.cluster.SerializedCache;
import com.norconex.crawler.core.cluster.SerializedCache.SerializedEntry;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListenerAdapter;
import com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcMapStoreFactory;
import com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcQueueStoreFactory;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HazelcastCacheManager implements CacheManager, Closeable {

    private static final String TYPE_REGISTRY_MAP = "__cache_types";
    private static final Map<HazelcastInstance, AtomicInteger> REF_COUNTS =
            new ConcurrentHashMap<>();
    private static final int BATCH_SIZE = 1000;

    private final HazelcastInstance hazelcast;
    private final Map<CacheEntryChangeListener<?>,
            CacheEntryChangeListenerAdapter<?>> adapterMappings =
                    new ConcurrentHashMap<>();

    public HazelcastCacheManager(HazelcastInstance hazelcastInstance) {
        hazelcast = hazelcastInstance;
        REF_COUNTS.computeIfAbsent(hazelcast, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    @Override
    public <T> CacheMap<T> getCacheMap(String name, Class<T> valueType) {
        // Ensure the factory is configured with the correct value type
        var cfg = hazelcast.getConfig();
        var mapConfig = cfg.getMapConfig(name);
        var storeConfig = mapConfig.getMapStoreConfig();
        if (storeConfig != null) {
            var factory = storeConfig.getFactoryImplementation();
            if (factory == null) {
                factory = new TypedJdbcMapStoreFactory();
                storeConfig.setFactoryImplementation(factory);
            }
            if (factory instanceof LazyTypedStoreFactory) {
                ((LazyTypedStoreFactory) factory).setValueClass(valueType);
                ((LazyTypedStoreFactory) factory)
                        .setHazelcastInstance(hazelcast);
            }
        }

        // register cache type so import/export know how to convert
        registerCacheType(name, valueType);

        return new HazelcastMapAdapter<>(
                getHazelcastMap(name),
                hazelcast,
                valueType);
    }

    @Override
    public CacheSet getCacheSet(String name) {
        return new HazelcastSetAdapter(hazelcast.getSet(name));
    }

    // Adapter for queue operations (to be used in CrawlEntryLedger)
    @Override
    public <T> CacheQueue<T> getCacheQueue(String name, Class<T> valueType) {
        // Ensure the factory is configured with the correct value type
        var cfg = hazelcast.getConfig();
        var queueConfig = cfg.getQueueConfig(name);
        var storeConfig = queueConfig.getQueueStoreConfig();
        if (storeConfig != null) {
            var factory = storeConfig.getFactoryImplementation();
            if (factory == null) {
                factory = new TypedJdbcQueueStoreFactory();
                storeConfig.setFactoryImplementation(factory);
            }
            if (factory instanceof LazyTypedStoreFactory) {
                ((LazyTypedStoreFactory) factory).setValueClass(valueType);
                ((LazyTypedStoreFactory) factory)
                        .setHazelcastInstance(hazelcast);
            }
        }

        registerCacheType(name, valueType);

        // Use a Hazelcast IQueue so items are FIFO and distributable.
        // The queue will store Strings or JSON-serialized objects
        // depending on the valueType.
        return new HazelcastQueueAdapter<>(
                getHazelcastQueue(name),
                hazelcast,
                valueType);
    }

    @Override
    public CacheMap<String> getCrawlerCache() {
        return getCacheMap(CacheNames.CRAWLER, String.class);
    }

    @Override
    public CacheMap<String> getCrawlSessionCache() {
        return getCacheMap(CacheNames.CRAWL_SESSION, String.class);
    }

    @Override
    public CacheMap<String> getCrawlRunCache() {
        return getCacheMap(CacheNames.CRAWL_RUN, String.class);
    }

    @Override
    public boolean cacheExists(String name) {
        // In Hazelcast, maps are created on demand, so we check if
        // the map has any entries or has been accessed
        var distributedObjects = hazelcast.getDistributedObjects();
        return distributedObjects.stream()
                .filter(IMap.class::isInstance)
                .anyMatch(obj -> obj.getName().equals(name));
    }

    @Override
    public void close() {
        var counter = REF_COUNTS.get(hazelcast);
        if (counter == null) {
            LOG.debug("Shutting down Hazelcast instance (unknown ref count)");
            hazelcast.shutdown();
            return;
        }
        var remaining = counter.decrementAndGet();
        if (remaining <= 0) {
            REF_COUNTS.remove(hazelcast);
            LOG.debug("Shutting down Hazelcast instance (last reference)");
            hazelcast.shutdown();
        } else {
            LOG.debug("Hazelcast instance still in use by {} manager(s); "
                    + "skipping shutdown.", remaining);
        }
    }

    public HazelcastInstance vendor() {
        return hazelcast;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void exportCaches(Consumer<SerializedCache> c) {

        var cacheTypes = getCacheTypes();

        hazelcast.getDistributedObjects().stream()
                .filter(IMap.class::isInstance)
                .map(obj -> (IMap<String, ?>) obj)
                .filter(imap -> !TYPE_REGISTRY_MAP.equals(imap.getName()))
                .forEach(imap -> {
                    var serialCache = new SerializedCache();
                    serialCache.setPersistent(HazelcastUtil.isPersistent(
                            hazelcast, imap.getName()));
                    serialCache.setCacheName(imap.getName());
                    serialCache.setClassName(
                            cacheTypes.get(imap.getName()).orElse(null));

                    var rawIt = imap.iterator();

                    serialCache.setEntries(new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return rawIt.hasNext();
                        }

                        @Override
                        public SerializedEntry next() {
                            Entry<String, ?> e = rawIt.next();
                            var key = e.getKey() == null ? null : e.getKey();
                            Object val = e.getValue();
                            if (val == null) {
                                return new SerializedEntry(key, null);
                            }
                            if (val instanceof String strVal) {
                                return new SerializedEntry(key, strVal);
                            }
                            // serialize any non-String value to JSON
                            try {
                                var json = SerialUtil.toJsonString(val);
                                return new SerializedEntry(key, json);
                            } catch (SerializationException ex) {
                                LOG.debug("Could not serialize value for cache "
                                        + "'{}': {}", imap.getName(),
                                        ex.toString());
                                return new SerializedEntry(key, val.toString());
                            }
                        }
                    });

                    c.accept(serialCache);
                });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void importCaches(List<SerializedCache> caches) {

        for (var serialCache : caches) {
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

            // Ensure the type registry is populated for this cache so
            // subsequent import/export operations know the cache type.
            try {
                Class cls = targetClass;
                registerCacheType(serialCache.getCacheName(), cls);
            } catch (Exception e) {
                LOG.debug("Could not register cache type for '{}': {}",
                        serialCache.getCacheName(), e.toString());
            }

            // Ensure the factory is configured with the correct value type
            var cfg = hazelcast.getConfig();
            var mapConfig = cfg.getMapConfig(serialCache.getCacheName());
            var storeConfig = mapConfig.getMapStoreConfig();
            if (storeConfig != null) {
                var factory = storeConfig.getFactoryImplementation();
                if (factory == null) {
                    factory = new TypedJdbcMapStoreFactory();
                    storeConfig.setFactoryImplementation(factory);
                }
                if (factory instanceof LazyTypedStoreFactory) {
                    ((LazyTypedStoreFactory) factory)
                            .setValueClass(targetClass);
                    ((LazyTypedStoreFactory) factory)
                            .setHazelcastInstance(hazelcast);
                }
            }

            // Use the underlying Hazelcast IMap to avoid generic
            // incompatibilities of CacheMap.putAll(Map<String,T>).
            IMap<String, Object> imap = (IMap) getHazelcastMap(
                    serialCache.getCacheName());

            Map<String, Object> batch = new ListOrderedMap<>();

            for (SerializedEntry entry : serialCache) {
                var str = entry.getJson();
                Object val = null;
                if (str != null && targetClass != String.class) {
                    try {
                        val = SerialUtil.fromJson(str, (Class) targetClass);
                    } catch (Exception ex) {
                        LOG.debug("Could not deserialize entry for "
                                + "cache '{}': {}",
                                serialCache.getCacheName(), ex.toString());
                        val = str;
                    }
                } else {
                    val = str;
                }

                batch.put(entry.getKey(), val);
                if (batch.size() >= BATCH_SIZE) {
                    // write directly to Hazelcast map (no generics issue)
                    imap.putAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                imap.putAll(batch);
            }
        }
    }

    @Override
    public void clearCaches() {
        hazelcast.getDistributedObjects().stream()
                .filter(IMap.class::isInstance)
                .map(obj -> (IMap<?, ?>) obj)
                .forEach(IMap::clear);
    }

    //--- Hazelcast-specific custom caches ------------------------------------

    public CacheMap<String> getAdminCache() {
        return getCacheMap(CacheNames.ADMIN, String.class);
    }

    public CacheMap<StepRecord> getPipelineStepCache() {
        return getCacheMap(CacheNames.PIPE_CURRENT_STEP, StepRecord.class);
    }

    public CacheMap<StepRecord> getPipelineWorkerStatusCache() {
        return getCacheMap(CacheNames.PIPE_WORKER_STATUSES, StepRecord.class);
    }

    public <T> void addCacheEntryChangeListener(
            @NonNull CacheEntryChangeListener<T> listener, String cacheName) {
        var adapter = new CacheEntryChangeListenerAdapter<>(listener);
        IMap<String, T> map = getHazelcastMap(cacheName);
        var registrationId = map.addEntryListener(adapter, true);
        adapter.setRegistrationId(registrationId);
        adapterMappings.put(listener, adapter);
    }

    public <T> void removeCacheEntryChangeListener(
            @NonNull CacheEntryChangeListener<T> listener, String cacheName) {
        var adapter = adapterMappings.remove(listener);
        if (adapter != null && adapter.getRegistrationId() != null) {
            try {
                IMap<String, T> map = getHazelcastMap(cacheName);
                map.removeEntryListener(adapter.getRegistrationId());
            } catch (Exception e) {
                LOG.debug("Could not remove cache entry listener: {}",
                        e.toString());
            }
        }
    }

    //--- Private methods ------------------------------------------------------

    private CacheMap<String> getCacheTypes() {
        return getCacheMap(TYPE_REGISTRY_MAP, String.class);
    }

    private <T> void registerCacheType(String name, Class<T> valueType) {
        if (TYPE_REGISTRY_MAP.equals(name)) {
            return;
        }
        try {
            getCacheTypes().putIfAbsent(name, valueType.getName());
        } catch (Exception e) {
            LOG.debug("Could not register cache type for '{}': {}",
                    name, e.toString());
        }
    }

    private <T> IQueue<T> getHazelcastQueue(String queueName) {
        var lifecycle = hazelcast.getLifecycleService();
        if (!lifecycle.isRunning()) {
            throw new ClusterException(
                    "Hazelcast instance is not running; cannot access "
                            + "queue '%s'.".formatted(queueName));
        }
        return hazelcast.getQueue(queueName);
    }

    private <K, V> IMap<K, V> getHazelcastMap(String mapName) {
        var lifecycle = hazelcast.getLifecycleService();
        if (!lifecycle.isRunning()) {
            throw new ClusterException(
                    "Hazelcast instance is not running; cannot access map '%s'."
                            .formatted(mapName));
        }
        return hazelcast.getMap(mapName);
    }
}