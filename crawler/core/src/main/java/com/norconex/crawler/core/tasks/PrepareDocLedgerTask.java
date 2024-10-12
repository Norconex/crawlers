/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks;

import java.util.Locale;

import com.norconex.commons.lang.PercentFormatter;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.grid.GridInitializedCrawlerTask;

import lombok.extern.slf4j.Slf4j;

/**
 * Prepare the doc processing ledger for a new or incremental crawl.
 * Takes care of caching processed documents on previously completed crawls
 * or simply logs the current progress if resuming from an incomplete
 * previous crawl.
 */
@Slf4j
public class PrepareDocLedgerTask extends GridInitializedCrawlerTask {

    private static final long serialVersionUID = 1L;

    public static final String KEY_INITIALIZING = "ledger.initializing";

    @Override
    protected void runWithInitializedCrawler(Crawler crawler, String arg) {
        var globalCache = crawler.getGrid().storage().getGlobalCache();

        if (Boolean.parseBoolean(globalCache.get(KEY_INITIALIZING))) {
            throw new IllegalStateException("Already initializing.");
        }
        globalCache.put(KEY_INITIALIZING, "true");
        try {
            prepareForCrawl(crawler);
        } finally {
            globalCache.put(KEY_INITIALIZING, "false");
        }
    }

    private void prepareForCrawl(Crawler crawler) {
        var grid = crawler.getGrid().storage();
        grid.getGlobalCache();
        var ledger = crawler.getDocProcessingLedger();
        var state = crawler.getState();

        if (state.isResuming()) {

            //            // Active -> Queued
            //            LOG.debug("Moving any {} active URLs back into queue.",
            //                    crawler.getId());
            //            active.forEach((k, v) -> {
            //                queue.save(k, v);
            //                return true;
            //            });
            //            active.clear();

            if (LOG.isInfoEnabled()) {
                //TODO use total count to track progress independently
                var processedCount = ledger.getProcessedCount();
                var totalCount = processedCount
                        + ledger.getQueueCount()
                        + ledger.getCachedCount();
                LOG.info("RESUMING \"{}\" at {} ({}/{}).",
                        crawler.getId(),
                        PercentFormatter.format(
                                processedCount, totalCount, 2, Locale.ENGLISH),
                        processedCount, totalCount);
            }
        } else {
            //            crawler.getDataStoreEngine();
            ledger.clearQueue();

            // Valid Processed -> Cached
            LOG.info("Caching any valid references from previous run.");

            ledger.cacheProcessed();

            if (LOG.isInfoEnabled()) {
                var cacheCount = ledger.getCachedCount();
                if (cacheCount > 0) {
                    LOG.info("STARTING an incremental crawl from previous {} "
                            + "valid references.", cacheCount);
                } else {
                    LOG.info("STARTING a fresh crawl.");
                }
            }
        }
    }
}
