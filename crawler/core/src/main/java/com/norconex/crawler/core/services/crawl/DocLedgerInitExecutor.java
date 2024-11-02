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
package com.norconex.crawler.core.services.crawl;

import java.util.Locale;

import com.norconex.commons.lang.PercentFormatter;
import com.norconex.crawler.core.CrawlerContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Prepare the doc processing ledger for a new or incremental crawl.
 * Takes care of caching processed documents on previously completed crawls
 * or simply logs the current progress if resuming from an incomplete
 * previous crawl.
 */
@Slf4j
final class DocLedgerInitExecutor {

    public static final String KEY_INITIALIZING = "ledger.initializing";

    private DocLedgerInitExecutor() {
    }

    public static void execute(CrawlerContext crawlerContext) {
        var globalCache = crawlerContext.getGrid().storage().getGlobalCache();

        if (Boolean.parseBoolean(globalCache.get(KEY_INITIALIZING))) {
            throw new IllegalStateException("Already initializing.");
        }
        globalCache.put(KEY_INITIALIZING, "true");
        try {
            prepareForCrawl(crawlerContext);
        } finally {
            globalCache.put(KEY_INITIALIZING, "false");
        }
    }

    private static void prepareForCrawl(CrawlerContext crawlerContext) {
        var storage = crawlerContext.getGrid().storage();
        storage.getGlobalCache();
        var ledger = crawlerContext.getDocProcessingLedger();
        var state = crawlerContext.getState();

        var isResuming = !ledger.isQueueEmpty();
        state.setResuming(isResuming);
        if (isResuming) {

            if (LOG.isInfoEnabled()) {
                //TODO use total count to track progress independently
                var processedCount = ledger.getProcessedCount();
                var totalCount = processedCount
                        + ledger.getQueueCount()
                        + ledger.getCachedCount();
                LOG.info("RESUMING \"{}\" at {} ({}/{}).",
                        crawlerContext.getId(),
                        PercentFormatter.format(
                                processedCount, totalCount, 2, Locale.ENGLISH),
                        processedCount, totalCount);
            }
        } else {
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
