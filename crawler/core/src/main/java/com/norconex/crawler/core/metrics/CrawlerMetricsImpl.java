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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerMetricsImpl implements CrawlerMetrics {

    private static final String EVENT_COUNTS_CACHE = "crawlEventCounts";
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
        eventCountsStore = crawlSession.getCluster().getCacheManager().getCache(
                EVENT_COUNTS_CACHE, Long.class);
        ctx.getEventManager().addListener(
                event -> batchIncrementCounter(event.getName(), 1L));
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::flushBatch,
                BATCH_INTERVAL, BATCH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void batchIncrementCounter(String eventName, long incrementBy) {
        eventCountsLocalBatch.merge(eventName, incrementBy, Long::sum);
    }

    private void doFlushBatch(boolean blocking) {
        if (closedAndFlushed) {
            LOG.debug("CrawlerMetrics already flushed and closed.");
            return;
        }
        if (blocking) {
            flushLock.lock();
        } else if (!flushLock.tryLock()) {
            LOG.warn("Could not acquire lock to flush batch, skipping this "
                    + "interval.");
            return;
        }
        try {
            eventCountsLocalBatch.forEach((eventName, increment) -> {
                if (increment == null || increment == 0L) {
                    return;
                }
                try {
                    // atomicIncrement is a private static helper defined
                    // below in this class; call it directly.
                    atomicIncrement(eventCountsStore, eventName, increment);
                    eventCountsLocalBatch.put(eventName, 0L);
                    memCache.eventCounts.merge(
                            eventName, increment, Long::sum);
                } catch (Exception e) {
                    LOG.error("Error updating event count cache for event: "
                            + eventName, e);
                }
            });
        } finally {
            flushLock.unlock();
        }
    }

    private void flushBatch() {
        doFlushBatch(false);
    }

    public void flushBlocking() {
        doFlushBatch(true);
    }

    @Override
    public long getProcessingCount() {
        if (!isClosed()) {
            memCache.processingCount.set(ledger.getProcessingCount());
        }
        return memCache.processingCount.get();
    }

    @Override
    public long getProcessedCount() {
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

    public boolean isClosed() {
        return closed;
    }

    private static void atomicIncrement(
            Cache<Long> store, String key, long increment) {
        try {
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
        } catch (Exception e) {
            LOG.error("Error updating event count cache for key: " + key, e);
            throw e;
        }
    }

    @Override
    public void flush() {
        if (this instanceof CrawlerMetricsImpl) {
            flushBlocking();
        } else {
            flushBatch();
        }
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
        flushBlocking();
        closedAndFlushed = true;
        LOG.info("Shutting down CrawlerMetrics scheduler...");
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
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
    static class MetricsMemCache {
        private final ConcurrentHashMap<String, Long> eventCounts =
                new ConcurrentHashMap<>();
        private final AtomicLong queuedCount = new AtomicLong();
        private final AtomicLong processingCount = new AtomicLong();
        private final AtomicLong processedCount = new AtomicLong();
        private final AtomicLong baselineCount = new AtomicLong();

        void clear() {
            eventCounts.clear();
            processingCount.set(0);
            processedCount.set(0);
            queuedCount.set(0);
            baselineCount.set(0);
        }
    }
}
