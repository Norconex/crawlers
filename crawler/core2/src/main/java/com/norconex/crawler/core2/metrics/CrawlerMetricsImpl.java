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
package com.norconex.crawler.core2.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanCacheManager;
import com.norconex.crawler.core2.ledger.CrawlEntryLedger;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerMetricsImpl implements CrawlerMetrics {

    // Scheduled batch flush interval in seconds
    //TODO make configurable?
    private static final long BATCH_INTERVAL = TimeUnit.SECONDS.toMillis(1);

    //MAYBE: have it configured to decide what to capture? Cons: may conflict
    // with listeners expectations.
    private CrawlEntryLedger ledger;
    private final ConcurrentHashMap<String, Long> eventCountsLocalBatch =
            new ConcurrentHashMap<>();
    private Cache<Long> eventCountsStore;
    private ScheduledExecutorService scheduler;
    private boolean closed;
    private boolean closedAndFlushed;

    private final Lock flushLock = new ReentrantLock();

    private final MetricsCache cache = new MetricsCache();

    private CrawlSession crawlSession;

    public CrawlerMetricsImpl() {
        LOG.info("[CrawlerMetricsImpl] Created instance: {}",
                System.identityHashCode(this));
    }

    @Override
    public void init(CrawlSession crawlSession) {
        cache.clear();
        var ctx = crawlSession.getCrawlContext();
        ledger = ctx.getCrawlEntryLedger();
        eventCountsStore = crawlSession.getCluster().getCacheManager().getCache(
                "CrawlerMetrics.eventCounts", Long.class);
        ctx.getEventManager().addListener(
                event -> batchIncrementCounter(event.getName(), 1L));
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::flushBatch,
                BATCH_INTERVAL, BATCH_INTERVAL, TimeUnit.MILLISECONDS);
        this.crawlSession = crawlSession;
    }

    public void batchIncrementCounter(String eventName, long incrementBy) {
        eventCountsLocalBatch.merge(eventName, incrementBy, Long::sum);
    }

    private static void atomicIncrement(Cache<Long> store, String key,
            long increment) {
        try {
            // Use a simple get-then-put pattern with retries for atomicity
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
                    // For new entries, putIfAbsent is atomic
                    var result = store.putIfAbsent(key, newValue);
                    updated = (result == null);
                } else {
                    // For existing entries, replace is atomic
                    updated = store.replace(key, currentLongValue, newValue);
                }

                attempts++;
            }
        } catch (Exception e) {
            LOG.error("Error updating event count cache for key: " + key, e);
            throw e;
        }
    }

    private void flushBatch() {
        if (closedAndFlushed) {
            LOG.debug("CrawlerMetrics already flushed and closed.");
            return;
        }
        if (eventCountsStore != null) {
            try {
                ((InfinispanCacheManager) crawlSession.getCluster()
                        .getCacheManager()).vendor();
            } catch (Exception e) {
                LOG.warn("Could not get cache manager: {}", e.getMessage());
            }
        }
        if (flushLock.tryLock()) {
            try {
                eventCountsLocalBatch.forEach((eventName, increment) -> {
                    try {
                        atomicIncrement(eventCountsStore, eventName, increment);
                        eventCountsLocalBatch.put(eventName, 0L);
                    } catch (Exception e) {
                        LOG.error("Error updating event count cache for event: "
                                + eventName, e);
                    }
                });
            } finally {
                flushLock.unlock();
            }
        } else {
            LOG.warn("Could not acquire lock to flush batch, "
                    + "skipping this interval.");
        }
    }

    @Override
    public long getProcessedCount() {
        if (!isClosed()) {
            cache.processedCount.set(ledger.getProcessedCount());
        }
        return cache.processedCount.get();
    }

    @Override
    public long getQueuedCount() {
        if (!isClosed()) {
            cache.queuedCount.set(ledger.getQueueCount());
        }
        return cache.queuedCount.get();
    }

    @Override
    public long getCachedCount() {
        if (!isClosed()) {
            cache.cachedCount.set(ledger.getPreviousEntryCount());
        }
        return cache.cachedCount.get();
    }

    @Override
    public Map<String, Long> getEventCounts() {
        if (!isClosed()) {
            try {
                eventCountsStore.forEach(cache.eventCounts::put);
            } catch (Exception e) {
                LOG.warn("CrawlerMetrics: Could not sync event counts from "
                        + "cluster (cluster may be shutting down): {}",
                        e.getMessage());
            }
        } else {
            LOG.info("CrawlerMetrics: Cluster is closed, returning last known "
                    + "event counts (not syncing).");
        }
        return cache.eventCounts;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void flush() {
        flushBatch();
    }

    @Override
    public void close() {
        LOG.info("Closing CrawlerMetrics...");
        if (closed) {
            LOG.info("CrawlerMetrics already closed.");
            return;
        }
        closed = true;
        LOG.info("Flusing CrawlerMetrics data...");
        flushBatch();
        closedAndFlushed = true;
        LOG.info("Shutting down CrawlerMetrics scheduler...");
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("CrawlerMetrics closed.");
    }

    /**
     * Caching of metrics so they can be referenced after close/shutdown.
     */
    static class MetricsCache {
        private final ConcurrentHashMap<String, Long> eventCounts =
                new ConcurrentHashMap<>();
        private final AtomicLong processedCount = new AtomicLong();
        private final AtomicLong queuedCount = new AtomicLong();
        private final AtomicLong cachedCount = new AtomicLong();

        void clear() {
            eventCounts.clear();
            processedCount.set(0);
            queuedCount.set(0);
            cachedCount.set(0);
        }
    }
}
