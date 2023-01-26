/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.monitor;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDocRecordService;

import lombok.NonNull;

public class CrawlerMonitor implements CrawlerMonitorMXBean {

    //Maybe have it configured to decide what to capture?
    private final CrawlDocRecordService service;
    private final Map<String, AtomicLong> eventCounts =
            new ConcurrentHashMap<>();

    public CrawlerMonitor(@NonNull Crawler crawler) {
        service = Objects.requireNonNull(crawler.getDocRecordService(),
                "'crawler#getDocRecordService() must not be null.");
        var eventManager =
                Objects.requireNonNull(crawler.getEventManager(),
                        "'crawler#getEventManager() must not be null.");
        eventManager.addListener(e -> eventCounts.computeIfAbsent(
                e.getName(), k -> new AtomicLong()).incrementAndGet());
    }

    @Override
    public long getProcessedCount() {
        return service.getProcessedCount();
    }
    @Override
    public long getQueuedCount() {
        return service.getQueueCount();
    }
    @Override
    public long getActiveCount() {
        return service.getActiveCount();
    }

    @Override
    public Map<String, Long> getEventCounts() {
        Map<String, Long> map = new TreeMap<>();
        eventCounts.forEach((event, count) ->
            map.put(event, count.longValue()));
        return map;
    }
}
