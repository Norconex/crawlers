/* Copyright 2021-2025 Norconex Inc.
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
package com.norconex.crawler.core.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerMetricsImpl implements CrawlerMetrics {

    private static final String EVENT_COUNTS_CACHE = "crawlEventCounts";
    //    private static final String PROCESSED_TOTAL_CACHE =
    //            "crawlProcessedTotal";
    //    private static final String PROCESSED_TOTAL_KEY = "TOTAL";

    private CrawlEntryLedger ledger;
    private CacheMap<Long> eventCountsStore;
    //    private CacheMap<Long> processedTotalStore;
    private boolean closed;

    private final MetricsMemCache memCache = new MetricsMemCache();

    public CrawlerMetricsImpl() {
        LOG.info("[CrawlerMetricsImpl] Created instance: {}",
                System.identityHashCode(this));
    }

    @Override
    public void init(CrawlSession crawlSession) {
        memCache.clear();
        var ctx = crawlSession.getCrawlContext();
        ledger = ctx.getCrawlEntryLedger();
        var cacheManager = crawlSession.getCluster().getCacheManager();
        eventCountsStore = cacheManager.getCache(
                EVENT_COUNTS_CACHE, Long.class);
        //        processedTotalStore = cacheManager.getCache(
        //                PROCESSED_TOTAL_CACHE, Long.class);

        //        // Prime processed total from persistent store if present
        //        if (processedTotalStore != null) {
        //            try {
        //                var stored = processedTotalStore.get(PROCESSED_TOTAL_KEY);
        //                if (stored.isPresent()) {
        //                    var val = stored.get();
        //                    memCache.processedTotal.set(val);
        //                }
        //            } catch (Exception e) {
        //                LOG.warn("Could not sync processed total from cluster: {}",
        //                        e.getMessage());
        //            }
        //        }

        ctx.getEventManager().addListener(event -> {
            incrementCounter(event.getName(), 1L);
            //            if (CrawlerEvent.DOCUMENT_PROCESSED.equals(event.getName())) {
            //                incrementProcessedTotal(1L);
            //            }
        });
    }

    //--- Event counts ------------------------------------------------------

    public void incrementCounter(String eventName, long incrementBy) {
        if (incrementBy == 0) {
            return;
        }
        if (eventCountsStore == null) {
            LOG.warn("Event counts store is not initialized yet. "
                    + "Increment will only be reflected in memory.");
        } else {
            try {
                atomicIncrement(eventCountsStore, eventName, incrementBy);
            } catch (Exception e) {
                LOG.error("Error updating event count cache for event: "
                        + eventName, e);
            }
        }
        memCache.eventCounts.merge(eventName, incrementBy, Long::sum);
    }

    //    private void incrementProcessedTotal(long incrementBy) {
    //        if (incrementBy == 0) {
    //            return;
    //        }
    //        if (processedTotalStore == null) {
    //            LOG.warn("Processed-total store is not initialized yet. "
    //                    + "Increment will only be reflected in memory.");
    //        } else {
    //            try {
    //                atomicIncrement(
    //                        processedTotalStore,
    //                        PROCESSED_TOTAL_KEY,
    //                        incrementBy);
    //            } catch (Exception e) {
    //                LOG.error("Error updating processed total cache.", e);
    //            }
    //        }
    //        memCache.processedTotal.addAndGet(incrementBy);
    //    }

    @Override
    public Map<String, Long> getEventCounts() {
        if (!isClosed()) {
            try {
                eventCountsStore.forEach(memCache.eventCounts::put);
            } catch (Exception e) {
                LOG.warn("CrawlerMetrics: Could not sync event counts from "
                        + "cluster (cluster may be shutting down): {}",
                        e.getMessage());
            }
        } else {
            LOG.info("CrawlerMetrics: Cluster is closed, returning last known "
                    + "event counts (not syncing).");
        }
        return memCache.eventCounts;
    }

    //--- Document state counts --------------------------------------------

    @Override
    public long getProcessingCount() {
        if (!isClosed()) {
            memCache.processingCount.set(ledger.getProcessingCount());
        }
        return memCache.processingCount.get();
    }

    @Override
    public long getProcessedCount() {
        //        if (!isClosed()) {
        //            if (processedTotalStore != null) {
        //                try {
        //                    var stored =
        //                            processedTotalStore.get(PROCESSED_TOTAL_KEY);
        //                    if (stored.isPresent()) {
        //                        var val = stored.get();
        //                        memCache.processedTotal.set(val);
        //                    }
        //                } catch (Exception e) {
        //                    LOG.warn("Could not sync processed total from cluster: {}",
        //                            e.getMessage());
        //                }
        //            }
        //        }
        if (!isClosed()) {
            memCache.processedCount.set(ledger.getProcessedCount());
        }
        return memCache.processedCount.get();
    }

    @Override
    public long getQueuedCount() {
        if (!isClosed()) {
            memCache.queuedCount.set(ledger.getQueueCount());
        }
        return memCache.queuedCount.get();
    }

    @Override
    public long getBaselineCount() {
        if (!isClosed()) {
            memCache.baselineCount.set(ledger.getBaselineCount());
        }
        return memCache.baselineCount.get();
    }

    public boolean isClosed() {
        return closed;
    }

    private static void atomicIncrement(
            CacheMap<Long> store, String key, long increment) {
        var updated = false;
        var attempts = 0;
        while (!updated && attempts < 3) {
            var currentValue = store.get(key);
            Long currentLongValue = 0L;
            if (!currentValue.isEmpty()) {
                Object val = currentValue.get();
                if (val instanceof Long longVal) {
                    currentLongValue = longVal;
                } else if (val instanceof Integer intVal) {
                    currentLongValue = intVal.longValue();
                } else {
                    throw new ClassCastException(
                            "Unsupported type in eventCountsStore: "
                                    + val.getClass());
                }
            }
            Long newValue = currentLongValue + increment;

            if (currentValue.isEmpty()) {
                var result = store.putIfAbsent(key, newValue);
                updated = (result == null);
            } else {
                updated = store.replace(key, currentLongValue, newValue);
            }

            attempts++;
        }
    }

    @Override
    public void flush() {
        // No-op for now: metrics are written directly to the cache
        // on each increment and ledger counts are queried on demand.
        // This method is kept for API compatibility and future hooks.
    }

    @Override
    public void close() {
        LOG.info("Closing CrawlerMetrics...");
        if (closed) {
            LOG.info("CrawlerMetrics already closed.");
            return;
        }

        // Best-effort final sync from cluster caches into memory so
        // we can still report accurate metrics after shutdown.
        try {
            if (eventCountsStore != null) {
                eventCountsStore.forEach(memCache.eventCounts::put);
            }
        } catch (Exception e) {
            LOG.warn("Could not sync event counts on close: {}",
                    e.getMessage());
        }

        //        try {
        //            if (processedTotalStore != null) {
        //                var stored =
        //                        processedTotalStore.get(PROCESSED_TOTAL_KEY);
        //                stored.ifPresent(memCache.processedTotal::set);
        //            }
        //        } catch (Exception e) {
        //            LOG.warn("Could not sync processed total on close: {}",
        //                    e.getMessage());
        //        }

        try {
            if (ledger != null) {
                memCache.queuedCount.set(ledger.getQueueCount());
                memCache.processingCount.set(ledger.getProcessingCount());
                memCache.processedCount.set(ledger.getProcessedCount());
                memCache.baselineCount.set(ledger.getBaselineCount());
            }
        } catch (Exception e) {
            LOG.warn("Could not sync state counts on close: {}",
                    e.getMessage());
        }

        closed = true;
        LOG.info("CrawlerMetrics closed.");
    }

    /**
     * Caching of metrics so they can be referenced after close/shutdown.
     */
    static class MetricsMemCache {
        private final ConcurrentHashMap<String, Long> eventCounts =
                new ConcurrentHashMap<>();
        private final AtomicLong queuedCount = new AtomicLong();
        private final AtomicLong processingCount = new AtomicLong();
        private final AtomicLong processedCount = new AtomicLong();
        private final AtomicLong baselineCount = new AtomicLong();
        //        private final AtomicLong processedTotal = new AtomicLong();

        void clear() {
            eventCounts.clear();
            processingCount.set(0);
            processedCount.set(0);
            queuedCount.set(0);
            baselineCount.set(0);
            //          processedTotal.set(0);
        }
    }
}
