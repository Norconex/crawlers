/* Copyright 2021-2024 Norconex Inc.
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

import java.io.Closeable;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.DocProcessingLedger;
import com.norconex.grid.core.storage.GridMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerMetrics implements CrawlerMetricsMXBean, Closeable {

    // Scheduled batch flush interval in seconds
    //TODO make configurable?
    private static final long BATCH_INTERVAL = TimeUnit.SECONDS.toMillis(1);

    //MAYBE: have it configured to decide what to capture? Cons: may conflict
    // with listeners expectations.
    private DocProcessingLedger ledger;
    private final ConcurrentHashMap<String, Long> eventCountsBatch =
            new ConcurrentHashMap<>();
    private GridMap<Long> eventCountsCache;
    private ScheduledExecutorService scheduler;
    private boolean closed;
    private final Lock flushLock = new ReentrantLock();

    public void init(CrawlerContext crawlerContext) {
        ledger = crawlerContext.getDocProcessingLedger();
        eventCountsCache = crawlerContext.getGrid().storage().getMap(
                "CrawlerMetrics.eventCounts", Long.class);
        crawlerContext.getEventManager().addListener(
                event -> batchIncrementCounter(event.getName(), 1L));
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::flushBatch,
                BATCH_INTERVAL, BATCH_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void batchIncrementCounter(String eventName, long incrementBy) {
        eventCountsBatch.merge(eventName, incrementBy, Long::sum);
    }

    private void flushBatch() {
        if (flushLock.tryLock()) {
            try {
                eventCountsBatch.forEach((eventName, increment) -> {
                    try {
                        eventCountsCache.update(eventName,
                                count -> (ofNullable(count).orElse(0L)
                                        + increment));
                        eventCountsBatch.put(eventName, 0L);
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
        return ledger.getProcessedCount();
    }

    @Override
    public long getQueuedCount() {
        return ledger.getQueueCount();
    }

    @Override
    public long getCachedCount() {
        return ledger.getCachedCount();
    }

    @Override
    public Map<String, Long> getEventCounts() {
        Map<String, Long> map = new TreeMap<>();
        eventCountsCache.forEach((event, count) -> {
            map.put(event, count);
            return true;
        });
        return map;
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
}
