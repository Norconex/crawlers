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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.CacheNames;
import com.norconex.crawler.core.cluster.CacheQueue;
import com.norconex.crawler.core.cluster.CacheSet;
import com.norconex.crawler.core.cluster.ClusterException;
import com.norconex.crawler.core.cluster.SerializedCache;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListenerAdapter;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HazelcastCacheManager implements CacheManager, Closeable {

    static final String TYPE_REGISTRY_MAP = "__cache_types";
    private static final Map<HazelcastInstance, AtomicInteger> REF_COUNTS =
            new ConcurrentHashMap<>();
    static final int BATCH_SIZE = 1000;

    final HazelcastInstance hazelcast;
    private final Map<CacheEntryChangeListener<?>,
            CacheEntryChangeListenerAdapter<?>> adapterMappings =
                    new ConcurrentHashMap<>();

    public HazelcastCacheManager(HazelcastInstance hazelcastInstance) {
        hazelcast = hazelcastInstance;
        REF_COUNTS.computeIfAbsent(hazelcast, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Returns the {@link HazelcastInstance} with the given name using
     * Hazelcast's own instance registry. Returns {@code null} if no
     * instance with that name is currently running on this JVM.
     */
    public static HazelcastInstance getHazelcastInstance(String name) {
        return Hazelcast.getHazelcastInstanceByName(name);
    }

    @Override
    public <T> CacheMap<T> getCacheMap(String name, Class<T> valueType) {
        // Register cache type so typed stores can resolve it from
        // the durable type registry before any data is loaded.
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
        // Same rationale as maps: record the type up-front so JDBC queue
        // stores (de)serialize consistently across nodes.
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
                .filter(HazelcastUtil::isSupportedCacheType)
                .anyMatch(obj -> obj.getName().equals(name));
    }

    @Override
    public void close() {
        LOG.info("HazelcastCacheManager.close() called for cleanup.");
        var counter = REF_COUNTS.get(hazelcast);
        if (counter == null) {
            LOG.info("Shutting down Hazelcast instance (unknown ref count)");
            hazelcast.shutdown();
            LOG.info("Hazelcast instance shutdown complete.");
            return;
        }
        var remaining = counter.decrementAndGet();
        if (remaining <= 0) {
            REF_COUNTS.remove(hazelcast);
            LOG.info("Shutting down Hazelcast instance (last reference)");
            hazelcast.shutdown();
            LOG.info("Hazelcast instance shutdown complete.");
        } else {
            LOG.info(
                    "Hazelcast instance still in use by {} manager(s); skipping shutdown.",
                    remaining);
        }
        LOG.info("HazelcastCacheManager.close() cleanup complete.");
    }

    public HazelcastInstance vendor() {
        return hazelcast;
    }

    HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    @Override
    public void exportCaches(Consumer<SerializedCache> c) {
        CacheExporter.export(this, c);
    }

    @Override
    public void importCaches(List<SerializedCache> caches) {
        CacheImporter.importCaches(this, caches);
    }

    @Override
    public void clearCaches() {
        hazelcast.getDistributedObjects().stream()
                .filter(HazelcastUtil::isSupportedCacheType)
                .forEach(obj -> {
                    if (obj instanceof IMap map) {
                        map.clear();
                    } else if (obj instanceof IQueue queue) {
                        queue.clear();
                    }
                });
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

    CacheMap<String> getCacheTypes() {
        return getCacheMap(TYPE_REGISTRY_MAP, String.class);
    }

    <T> void registerCacheType(String name, Class<T> valueType) {
        if (TYPE_REGISTRY_MAP.equals(name)) {
            return;
        }
        try {
            // Only register cache types for maps that have an active
            // MapStore configured. Ephemeral maps (eph-*) are explicitly
            // disabled in the YAML and should not trigger DB-backed
            // initialization of the type registry map.
            var cfg = hazelcast.getConfig();
            var mapConfig = cfg.getMapConfig(name);
            if (mapConfig == null) {
                LOG.debug("No map config for '{}'; skipping type registration",
                        name);
                return;
            }
            var storeConfig = mapConfig.getMapStoreConfig();
            if (storeConfig == null || !storeConfig.isEnabled()) {
                LOG.debug("Map '{}' has no active MapStore; skipping type "
                        + "registration", name);
                return;
            }
            getCacheTypes().putIfAbsent(name, valueType.getName());
        } catch (Exception e) {
            LOG.debug("Could not register cache type for '{}': {}",
                    name, e.toString());
        }
    }

    <T> IQueue<T> getHazelcastQueue(String queueName) {
        var lifecycle = hazelcast.getLifecycleService();
        if (!lifecycle.isRunning()) {
            throw new ClusterException(
                    "Hazelcast instance is not running; cannot access "
                            + "queue '%s'.".formatted(queueName));
        }
        return hazelcast.getQueue(queueName);
    }

    <T> IMap<String, T> getHazelcastMap(String mapName) {
        var lifecycle = hazelcast.getLifecycleService();
        if (!lifecycle.isRunning()) {
            throw new ClusterException(
                    "Hazelcast instance is not running; cannot access map '%s'."
                            .formatted(mapName));
        }
        return hazelcast.getMap(mapName);
    }
}
