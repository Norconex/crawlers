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
package com.norconex.crawler.core.test;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.TreeBag;

import com.norconex.crawler.core.cluster.impl.hazelcast.CacheNames;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Builder
@Getter
public class CrawlTestResult {

    @Data
    public static class StatusCounts {
        private final long queued;
        private final long untracked;
        private final long processing;
        private final long processed;
    }

    @NonNull
    private final List<String> eventNames;

    @NonNull
    private final List<String> logLines;

    @NonNull
    private final Map<String, Map<String, String>> caches;

    public Bag<String> getEventNameBag() {
        var bag = new TreeBag<String>();
        getEventNames().forEach(bag::add);
        return bag;
    }

    public String getLog() {
        return String.join("\n", logLines);
    }

    public Map<String, String> getCache(String name) {
        return caches.get(name);
    }

    public StatusCounts getLedgerStatusCounts() {
        var sessionMap = caches.get(CacheNames.CRAWL_SESSION);
        return new StatusCounts(
                Long.parseLong(sessionMap.get("status-counter-QUEUED")),
                Long.parseLong(sessionMap.get("status-counter-UNTRACKED")),
                Long.parseLong(sessionMap.get("status-counter-PROCESSING")),
                Long.parseLong(sessionMap.get("status-counter-PROCESSED")));
    }
}
