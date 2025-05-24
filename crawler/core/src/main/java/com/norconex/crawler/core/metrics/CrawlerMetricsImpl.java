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

import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.norconex.crawler.core.doc.CrawlDocLedger;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.storage.GridMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerMetricsImpl implements CrawlerMetrics {

    // Scheduled batch flush interval in seconds
    //TODO make configurable?
    private static final long BATCH_INTERVAL = TimeUnit.SECONDS.toMillis(1);

    //MAYBE: have it configured to decide what to capture? Cons: may conflict
    // with listeners expectations.
    private CrawlDocLedger ledger;
    private final ConcurrentHashMap<String, Long> eventCountsLocalBatch =
            new ConcurrentHashMap<>();
    private GridMap<Long> eventCountsStore;
    private ScheduledExecutorService scheduler;
    private boolean closed;

    private final Lock flushLock = new ReentrantLock();

    private final MetricsCache cache = new MetricsCache();

    @Override
    public void init(CrawlContext crawlContext) {
        cache.clear();
        ledger = crawlContext.getDocLedger();
        eventCountsStore = crawlContext.getGrid().getStorage().getMap(
                "CrawlerMetrics.eventCounts", Long.class);
        crawlContext.getEventManager().addListener(
                event -> batchIncrementCounter(event.getName(), 1L));
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::flushBatch,
                BATCH_INTERVAL, BATCH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void batchIncrementCounter(String eventName, long incrementBy) {
        eventCountsLocalBatch.merge(eventName, incrementBy, Long::sum);
    }

    private void flushBatch() {
        if (flushLock.tryLock()) {
            try {
                eventCountsLocalBatch.forEach((eventName, increment) -> {
                    try {
                        eventCountsStore.update(eventName,
                                count -> (ofNullable(count).orElse(0L)
                                        + increment));
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
            cache.cachedCount.set(ledger.getCachedCount());
        }
        return cache.cachedCount.get();
    }

    @Override
    public Map<String, Long> getEventCounts() {
        if (!isClosed()) {
            eventCountsStore.forEach((event, count) -> {
                cache.eventCounts.put(event, count);
                return true;
            });
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
        if (closed) {
            return;
        }
        closed = true;
        flushBatch();
        if (scheduler != null) {
            scheduler.shutdown();
        }
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
