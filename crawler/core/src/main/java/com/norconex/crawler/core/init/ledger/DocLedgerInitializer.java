/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.init.ledger;

import java.util.Locale;
import java.util.function.Predicate;

import com.norconex.commons.lang.PercentFormatter;
import com.norconex.crawler.core.CrawlerContext;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Prepare the doc processing ledger for a new or incremental crawl.
 * Takes care of caching processed documents on previously completed crawls
 * or simply logs the current progress if resuming from an incomplete
 * previous crawl.
 * </p>
 * <p>
 * Stores in the global cache whether it is currently initializing
 * under the key {@value #KEY_INITIALIZING}.
 * </p>
 */
@Slf4j
public final class DocLedgerInitializer implements Predicate<CrawlerContext> {

    //NOTE: Runs after the DocProcessingLedger#init() method has been invoked.

    public static final String KEY_INITIALIZING = "ledger.initializing";

    @Override
    public boolean test(CrawlerContext crawlerContext) {
        var globalCache =
                crawlerContext.getGrid().storage().getSessionAttributes();

        if (Boolean.parseBoolean(globalCache.get(KEY_INITIALIZING))) {
            throw new IllegalStateException("Already initializing.");
        }
        globalCache.put(KEY_INITIALIZING, "true");
        try {
            prepareForCrawl(crawlerContext);
        } finally {
            globalCache.put(KEY_INITIALIZING, "false");
        }
        return true;
    }

    private static void prepareForCrawl(CrawlerContext crawlerContext) {
        var ledger = crawlerContext.getDocProcessingLedger();

        long maxProcessedDocs =
                crawlerContext.getConfiguration().getMaxDocuments();
        if (crawlerContext.isResuming()) {
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
                LOG.debug("Resuming from:"
                        + "\n    Queued: " + ledger.getQueueCount()
                        + "\n Processed: " + processedCount
                        + "\n    Cached: " + ledger.getCachedCount());
            }

            if (maxProcessedDocs > -1) {
                maxProcessedDocs += ledger.getProcessedCount();
                LOG.info("""
                    An additional maximum of {} processed documents is
                    added to this resumed session, for a maximum total of {}.
                    """, crawlerContext.getConfiguration().getMaxDocuments(),
                        maxProcessedDocs);
            }
        } else {
            ledger.clearQueue(); //TODO <-- why? it gets here only if queue is empty to begin with

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
        crawlerContext.maxProcessedDocs(maxProcessedDocs);
    }
}
