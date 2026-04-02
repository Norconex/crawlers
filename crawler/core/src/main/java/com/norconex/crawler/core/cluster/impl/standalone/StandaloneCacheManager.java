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
package com.norconex.crawler.core.cluster.impl.standalone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.CacheQueue;
import com.norconex.crawler.core.cluster.CacheSet;
import com.norconex.crawler.core.cluster.SerializedCache;
import com.norconex.crawler.core.cluster.SerializedCache.CacheType;
import com.norconex.crawler.core.cluster.SerializedCache.SerializedEntry;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.util.SerialUtil;

/**
 * In-memory {@link CacheManager} for standalone (non-clustered) mode.
 * Uses {@link InMemoryCacheMap} and {@link InMemoryCacheQueue} instances
 * backed by concurrent hash maps/queues. No persistence, no distributed
 * coordination.
 *
 * <p>Multiple calls with the same name return the <em>same</em> instance
 * so callers naturally share state.</p>
 */
public class StandaloneCacheManager implements CacheManager {

    private final Map<String, InMemoryCacheMap<?>> maps = new HashMap<>();
    private final Map<String, InMemoryCacheQueue<?>> queues = new HashMap<>();
    private final Map<String, InMemoryCacheSet> sets = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T> CacheMap<T> getCacheMap(String name, Class<T> valueType) {
        return (CacheMap<T>) maps.computeIfAbsent(
                name, InMemoryCacheMap::new);
    }

    @Override
    public CacheSet getCacheSet(String name) {
        return sets.computeIfAbsent(name, n -> new InMemoryCacheSet());
    }

    @Override
    public boolean cacheExists(String name) {
        var map = maps.get(name);
        return map != null && !map.isEmpty();
    }

    @Override
    public void exportCaches(Consumer<SerializedCache> c) {
        maps.forEach((name, map) -> {
            var serialCache = new SerializedCache();
            serialCache.setCacheName(name);
            serialCache.setCacheType(CacheType.MAP);
            serialCache.setPersistent(false);
            var entries = new java.util.ArrayList<SerializedEntry>();
            map.forEach((key, value) -> {
                if (value instanceof String strVal) {
                    entries.add(new SerializedEntry(key, strVal));
                } else {
                    entries.add(new SerializedEntry(
                            key, SerialUtil.toJsonString(value)));
                }
            });
            serialCache.setEntries(entries.iterator());
            c.accept(serialCache);
        });
    }

    @Override
    public void importCaches(List<SerializedCache> caches) {
        // No-op: standalone caches are ephemeral.
    }

    @Override
    public void clearCaches() {
        maps.values().forEach(InMemoryCacheMap::clear);
        queues.values().forEach(InMemoryCacheQueue::clear);
        sets.values().forEach(InMemoryCacheSet::clear);
    }

    @Override
    public CacheMap<String> getCrawlerCache() {
        return getCacheMap("crawler", String.class);
    }

    @Override
    public CacheMap<String> getCrawlSessionCache() {
        return getCacheMap("crawlSession", String.class);
    }

    @Override
    public CacheMap<String> getCrawlRunCache() {
        return getCacheMap("crawlRun", String.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> CacheQueue<T> getCacheQueue(String name, Class<T> valueType) {
        return (CacheQueue<T>) queues.computeIfAbsent(
                name, InMemoryCacheQueue::new);
    }

    @Override
    public CacheMap<String> getAdminCache() {
        return getCacheMap("admin", String.class);
    }

    @Override
    public CacheMap<StepRecord> getPipelineStepCache() {
        return getCacheMap("pipeCurrentStep", StepRecord.class);
    }

    @Override
    public CacheMap<StepRecord> getPipelineWorkerStatusCache() {
        return getCacheMap("pipeWorkerStatuses", StepRecord.class);
    }

    @Override
    public <T> void addCacheEntryChangeListener(
            CacheEntryChangeListener<T> listener, String cacheName) {
        // No-op: in-memory caches fire no distributed events.
    }

    @Override
    public <T> void removeCacheEntryChangeListener(
            CacheEntryChangeListener<T> listener, String cacheName) {
        // No-op
    }

    @Override
    public Object vendor() {
        return this;
    }
}
