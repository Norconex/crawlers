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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.ClusterException;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.CacheQueue;
import com.norconex.crawler.core.cluster.CacheSet;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListenerAdapter;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HazelcastCacheManager implements CacheManager, Closeable {

    private static final Map<HazelcastInstance, AtomicInteger> REF_COUNTS =
            new ConcurrentHashMap<>();

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
    public <T> CacheMap<T> getCache(String name, Class<T> valueType) {
        return new HazelcastMapAdapter<>(getHazelcastMap(name), hazelcast);
    }

    @Override
    public CacheSet getCacheSet(String name) {
        return new HazelcastSetAdapter(hazelcast.getSet(name));
    }

    @Override
    public CacheMap<String> getCrawlerCache() {
        return new HazelcastMapAdapter<>(
                getHazelcastMap(CacheNames.CRAWLER), hazelcast);
    }

    @Override
    public CacheMap<String> getCrawlSessionCache() {
        return new HazelcastMapAdapter<>(
                getHazelcastMap(CacheNames.CRAWL_SESSION), hazelcast);
    }

    @Override
    public CacheMap<String> getCrawlRunCache() {
        return new HazelcastMapAdapter<>(
                getHazelcastMap(CacheNames.CRAWL_RUN), hazelcast);
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
    public void forEach(BiConsumer<String, CacheMap<?>> c) {
        hazelcast.getDistributedObjects().stream()
                .filter(IMap.class::isInstance)
                .map(obj -> (IMap<String, ?>) obj)
                .forEach(map -> c.accept(
                        map.getName(),
                        new HazelcastMapAdapter<>(map, hazelcast)));
    }

    //--- Hazelcast-specific custom caches ------------------------------------

    public CacheMap<String> getAdminCache() {
        return getCache(CacheNames.ADMIN, String.class);
    }

    public CacheMap<StepRecord> getPipelineStepCache() {
        return getCache(CacheNames.PIPE_CURRENT_STEP, StepRecord.class);
    }

    public CacheMap<StepRecord> getPipelineWorkerStatusCache() {
        return getCache(CacheNames.PIPE_WORKER_STATUSES, StepRecord.class);
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

    // Adapter for queue operations (to be used in CrawlEntryLedger)
    @Override
    public <T> CacheQueue<T> getQueue(String name, Class<T> valueType) {
        return new HazelcastQueueAdapter<>(getHazelcastQueue(name));
    }

    //--- Private methods ------------------------------------------------------

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
