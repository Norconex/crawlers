/* Copyright 2024-2026 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger;

import java.util.Locale;

import com.norconex.commons.lang.PercentFormatter;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core.ledger.ProcessingStatus;
import com.norconex.crawler.core.session.CrawlSession;

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
public final class CrawlEntryLedgerBootstrapper implements CrawlBootstrapper {

    //NOTE: Runs after the DocProcessingLedger#init() method has been invoked.

    public static final String BOOTSTRAP_KEY = "ledger.bootstrapped";

    @Override
    public void bootstrap(CrawlSession session) {
        // Use atomic operation to prevent race condition in clustered
        // environments where coordinator election may not be complete
        // during initial startup
        if (!session.getCrawlRunAttributes().setBooleanIfAbsent(
                BOOTSTRAP_KEY, true)) {
            LOG.info("Bootstrap already in progress or completed by "
                    + "coordinator, skipping.");
            return;
        }

        //        if (!session.setBooleanIfAbsent(BOOTSTRAP_KEY, true)) {
        //            LOG.info("Bootstrap already in progress or completed by "
        //                    + "coordinator, skipping.");
        //            return;
        //        }
        try {
            prepareForCrawl(session);
        } finally {
            session.getCrawlRunAttributes().setBoolean(BOOTSTRAP_KEY, false);
        }
    }

    private static void prepareForCrawl(CrawlSession session) {
        var crawlContext = session.getCrawlContext();
        var ledger = crawlContext.getCrawlEntryLedger();

        // Ensure the current ledger alias is set in session cache
        // before any operations (important for cluster synchronization)
        ledger.ensureCurrentLedgerAliasExists();

        LOG.info(
                "Bootstrap prepareForCrawl: isResumed={}, queueCount={}, processedCount={}",
                session.isResumed(), ledger.getQueueCount(),
                ledger.getProcessedCount());

        // Queue is persistent (JDBC queue store) and preserves FIFO order
        // automatically across restarts, but we need to restore any entries
        // that were in PROCESSING state when the crawler stopped
        if (session.isResumed()) {
            LOG.info("RESUMING crawl - queue currently has {} items",
                    ledger.getQueueCount());

            // Restore entries that were in QUEUED state. The Hazelcast
            // persistent queue may not have restored them properly due to
            // partition ownership changes across restarts.
            // Check if queue is empty but ledger has QUEUED entries.
            if (ledger.getQueueCount() == 0) {
                var ledgerQueuedCount = ledger.countByStatus(
                        ProcessingStatus.QUEUED);
                if (ledgerQueuedCount > 0) {
                    LOG.warn(
                            "Queue empty but ledger has {} QUEUED entries. "
                                    + "Restoring from ledger (likely Hazelcast partition "
                                    + "rebalancing issue).",
                            ledgerQueuedCount);
                    var restoredCount = ledger.requeueQueuedEntries();
                    LOG.info("Restored {} QUEUED entries to queue from ledger.",
                            restoredCount);
                }
            }

            // Restore entries that were in PROCESSING state (were pulled
            // from queue but not completed). These are added to the front
            // of the queue since they were already being processed.
            var requeuedCount = ledger.requeueProcessingEntries();
            if (requeuedCount > 0) {
                LOG.info(
                        "Re-queued {} entries that were PROCESSING from previous run.",
                        requeuedCount);
            }

            if (LOG.isInfoEnabled()) {
                var processedCount = ledger.getProcessedCount();
                var cachedCount = ledger.getBaselineCount();
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
            LOG.info(
                    "NOT resuming - queueCount={}, will check if should preserve queue",
                    ledger.getQueueCount());
            // Incremental + NEW usually means the previous run completed
            // successfully and we should start a new run lifecycle.
            // However, there are edge cases where resume detection can miss
            // an unfinished previous run, leaving queued items to continue.
            if (session.isIncremental()) {
                var queueCount = ledger.getQueueCount();
                var processedCount = ledger.getProcessedCount();
                var maxDocs = crawlContext.getCrawlConfig().getMaxDocuments();

                var looksLikeUnfinishedRun = queueCount > 0
                        && maxDocs > -1
                        && processedCount < maxDocs;

                if (looksLikeUnfinishedRun) {
                    LOG.warn("Incremental new run detected with {} queued "
                            + "item(s) and processedCount={} (< maxDocs={}). "
                            + "Treating as resume-detection fallback and "
                            + "preserving queue.",
                            queueCount, processedCount, maxDocs);

                    var requeuedCount = ledger.requeueProcessingEntries();
                    if (requeuedCount > 0) {
                        LOG.info("Re-queued {} entries that were PROCESSING "
                                + "from previous run.", requeuedCount);
                    }
                    LOG.info("Continuing from previous state: {} queued, {} "
                            + "processed", ledger.getQueueCount(),
                            ledger.getProcessedCount());
                    return;
                }

                if (queueCount > 0) {
                    LOG.warn("Incremental new run detected with {} queued "
                            + "item(s) and processedCount={} (maxDocs={}). "
                            + "Clearing queue and rotating ledger for a clean "
                            + "new run.",
                            queueCount, processedCount, maxDocs);
                }
                ledger.clearQueuedEntriesInLedger();
                LOG.info("Caching any valid references from previous run.");
                ledger.archiveCurrentLedger();

                if (LOG.isInfoEnabled()) {
                    var cacheCount = ledger.getBaselineCount();
                    if (cacheCount > 0) {
                        LOG.info("STARTING an incremental crawl from "
                                + "previous {} valid references.",
                                cacheCount);
                    } else {
                        LOG.info("STARTING a fresh crawl.");
                    }
                }
                return;
            }
            // Only clear the queue if it's truly empty or this is a fresh start.
            // If the queue already has items (e.g., from a previous run where
            // resume detection failed due to Hazelcast cache partition issues),
            // preserve them to avoid data loss.
            var queueCount = ledger.getQueueCount();
            if (queueCount > 0) {
                LOG.warn("""
                    Queue has {} items but resume was not detected. \
                    This may indicate a resume detection issue. \
                    Preserving queue to avoid data loss.""",
                        queueCount);
                // Treat this as a resume even though it wasn't properly detected

                //                // Restore both QUEUED and PROCESSING entries
                //                var queuedCount = ledger.requeueQueuedEntries();
                //                if (queuedCount > 0) {
                //                    LOG.info(
                //                            "Re-queued {} entries that were QUEUED from previous run.",
                //                            queuedCount);
                //                }

                var requeuedCount = ledger.requeueProcessingEntries();
                if (requeuedCount > 0) {
                    LOG.info("Re-queued {} entries that were PROCESSING from "
                            + "previous run.",
                            requeuedCount);
                }
                LOG.info("Continuing from previous state: {} queued, "
                        + "{} processed",
                        ledger.getQueueCount(),
                        ledger.getProcessedCount());
            } else {
                // Queue is empty, safe to clear ledger queued statuses and
                // start fresh without forcing a physical queue clear.
                ledger.clearQueuedEntriesInLedger();

                // Valid Processed -> Cached
                LOG.info("Caching any valid references from previous run.");

                ledger.archiveCurrentLedger();

                if (LOG.isInfoEnabled()) {
                    var cacheCount = ledger.getBaselineCount();
                    if (cacheCount > 0) {
                        LOG.info("STARTING an incremental crawl from "
                                + "previous {} valid references.",
                                cacheCount);
                    } else {
                        LOG.info("STARTING a fresh crawl.");
                    }
                }
            }
        }
    }
}
