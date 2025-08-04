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
package com.norconex.crawler.core2.cmd.crawl.pipeline.bootstrap.ledger;

import java.util.Locale;

import com.norconex.commons.lang.PercentFormatter;
import com.norconex.crawler.core2.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core2.context.CrawlContext;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.session.LaunchMode;

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
 * under the key {@value #BOOTSTRAP_KEY}.
 * </p>
 */
@Slf4j
public final class DocLedgerBootstrapper implements CrawlBootstrapper {

    //NOTE: Runs after the DocProcessingLedger#init() method has been invoked.

    public static final String BOOTSTRAP_KEY = "ledger.bootstrap";

    @Override
    public void bootstrap(CrawlSession session) {
        var ctx = session.getCrawlContext();
        var globalCache =
                session.getCluster().getCacheManager().getGenericCache();

        if (Boolean.parseBoolean(
                globalCache.get(BOOTSTRAP_KEY).orElse("false"))) {
            throw new IllegalStateException("Already initializing.");
        }
        globalCache.put(BOOTSTRAP_KEY, "true");
        try {
            prepareForCrawl(ctx);
        } finally {
            globalCache.put(BOOTSTRAP_KEY, "false");
        }
    }

    private static void prepareForCrawl(CrawlContext crawlContext) {
        var ledger = crawlContext.getCrawlEntryLedger();
        if (crawlContext.getLaunchMode() == LaunchMode.RESUMED) {
            if (LOG.isInfoEnabled()) {
                //TODO use total count to track progress independently
                var processedCount = ledger.getProcessedCount();
                var cachedCount = ledger.getPreviousEntryCount();
                var totalCount = processedCount
                        + ledger.getQueueCount()
                        + cachedCount;
                LOG.info("RESUMING \"{}\" at {} ({}/{}).",
                        crawlContext.getId(),
                        PercentFormatter.format(
                                processedCount, totalCount, 2, Locale.ENGLISH),
                        processedCount, totalCount);
                LOG.debug("Resuming from:"
                        + "\n    Queued: " + ledger.getQueueCount()
                        + "\n Processed: " + processedCount
                        + "\n    Cached: "
                        + cachedCount);
            }
        } else {
            ledger.clearQueue();

            // Valid Processed -> Cached
            LOG.info("Caching any valid references from previous run.");

            ledger.archiveCurrentLedger();

            if (LOG.isInfoEnabled()) {
                var cacheCount = ledger.getPreviousEntryCount();
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
