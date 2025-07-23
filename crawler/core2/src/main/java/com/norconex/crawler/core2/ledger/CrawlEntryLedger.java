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
package com.norconex.crawler.core2.ledger;

import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.Counter;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.session.LaunchMode;

import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Tracks document state and any other meta information required
 * for document processing. Acts as a facade over a few processing
 * state-specific data stores. Includes persisting of information
 * necessary for incremental crawls and resumes.
 * <p>
 * <p>
 * The processing of a document has the following stages:
 * </p>
 * <ul>
 *   <li><b>Queued:</b> References extracted from documents are first queued
 *       for processing.</li>
 *   <li><b>Processing:</b> A reference is currently being processed.</li>
 *   <li><b>Processed:</b> A reference has been processed.  If the same
 *       reference is encountered again during the same run, it will be
 *       ignored.</li>
 * </ul>
 * <p>
 *   Once a crawl completes, processed references become "cached" on the
 *   next run and are used to establish deltas and save processing.</li>
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class CrawlEntryLedger {

    //TODO rename DocLedger
    //TODO remove all synchronized keywords?

    private static final String CURRENT_LEDGER_KEY = "ledger.current.alias";
    //    public static final String KEY_CACHED_MAP = "ledger.cached.map";

    // we use aliases for previous and current ledgers as we can't assume
    // we can physically rename them and copying data over could be
    // too inefficient.
    private static final String LEDGER_A = "ledger_a";
    private static final String LEDGER_B = "ledger_b";

    private final Cache<CrawlEntry> currentLedger;
    private final Cache<CrawlEntry> previousLedger;
    private final long totalMaxDocsThisRun;
    private final Counter processedCounter;

    @Builder
    static CrawlEntryLedger create(
            @NonNull CrawlSession session,
            @NonNull CacheManager manager,
            long runMaxDocs) {
        LOG.info("Initializing crawl entry ledger...");

        // Caches:
        var currentAlias = manager.getGenericCache()
                .computeIfAbsent(CURRENT_LEDGER_KEY, k -> LEDGER_A);
        var previousAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        var currentLedger = manager.getCache(currentAlias, CrawlEntry.class);
        var previousLedger = manager.cacheExists(previousAlias)
                ? manager.getCache(previousAlias, CrawlEntry.class)
                : null;
        var processedCounter = manager.getCounter("processed-counter");

        // Max docs
        var totalMaxDocsThisRun = runMaxDocs;
        var resumed = session.getLaunchMode() == LaunchMode.RESUMED;
        if (resumed && runMaxDocs > -1) {
            totalMaxDocsThisRun += processedCounter.get();
            LOG.info("""
                    An additional maximum of {} processed documents is
                    added to this resumed session, for a maximum total of {}.
                    """, runMaxDocs,
                    totalMaxDocsThisRun);
        }

        LOG.info("Done initializing crawl entry ledger.");
        return new CrawlEntryLedger(currentLedger, previousLedger,
                totalMaxDocsThisRun, processedCounter);
    }

    //    /**
    //     * Gets whether a reference is queued, processed or being processed.
    //     * @param ref document reference
    //     * @return <code>true</code> if in "active" stage
    //     */
    //    public boolean isInActiveStage(String ref) {
    //        return queue.contains(ref) || processed.contains(ref);
    //    }
    //
    //    /**
    //     * Gets a reference document processing stage.
    //     * @param ref document reference
    //     * @return <code>true</code> if in "active" stage
    //     */
    //    public CrawlDocStage getStage(String ref) {
    //        if (queue.contains(ref)) {
    //            return CrawlDocStage.QUEUED;
    //        }
    //        var docCtxOpt = getProcessed(ref);
    //        if (docCtxOpt.isPresent()) {
    //            return docCtxOpt.get().getProcessingStage();
    //        }
    //        return CrawlDocStage.NONE;
    //    }
    //
    //    //--- Processed ---
    //
    //    public long getProcessedCount() {
    //        return processed.size();
    //    }
    //
    //    public boolean isProcessedEmpty() {
    //        return processed.isEmpty();
    //    }
    //
    //    public void clearProcessed() {
    //        processed.clear();
    //    }
    //
    //    public Optional<CrawlDocContext> getProcessed(String id) {
    //        return Optional.ofNullable(processed.get(id))
    //                .map(json -> SerialUtil.fromJson(json, type));
    //    }
    //
    //    public synchronized void processed(@NonNull CrawlDocContext docCtx) {
    //        docCtx.setProcessingStage(CrawlDocStage.RESOLVED);
    //        processed.put(docCtx.getReference(), SerialUtil.toJsonString(docCtx));
    //        var cacheDeleted = cached.delete(docCtx.getReference());
    //        LOG.debug("Saved processed: {} (Deleted from cache: {})", // Deleted from active: {})",
    //                docCtx.getReference(), cacheDeleted);//, activeDeleted);
    //        crawlContext.fire(CrawlerEvent.builder()
    //                .name(CrawlerEvent.DOCUMENT_PROCESSED)
    //                .source(crawlContext)
    //                .docContext(docCtx)
    //                .build());
    //    }
    //
    //    public boolean forEachProcessed(
    //            BiPredicate<String, CrawlDocContext> predicate) {
    //        return processed.forEach(
    //                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    //    }
    //
    //    //--- Queue ---
    //
    //    public boolean isQueueEmpty() {
    //        return queue.isEmpty();
    //    }
    //
    //    public long getQueueCount() {
    //        return queue.size();
    //    }
    //
    //    public void clearQueue() {
    //        queue.clear();
    //    }
    //
    //    public void queue(@NonNull CrawlDocContext docContext) {
    //        docContext.setProcessingStage(CrawlDocStage.QUEUED);
    //        queue.put(docContext.getReference(),
    //                SerialUtil.toJsonString(docContext));
    //        LOG.debug("Saved queued: {}", docContext.getReference());
    //        crawlContext.fire(CrawlerEvent.builder()
    //                .name(CrawlerEvent.DOCUMENT_QUEUED)
    //                .source(crawlContext)
    //                .docContext(docContext)
    //                .build());
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    public Optional<CrawlDocContext> pollQueue() {
    //        var opt = queue
    //                .poll()
    //                .map(json -> SerialUtil.fromJson(json, type));
    //        opt.ifPresent(doc -> {
    //            doc.setProcessingStage(CrawlDocStage.UNRESOLVED);
    //            processed.put(doc.getReference(),
    //                    SerialUtil.toJsonString(doc));
    //        });
    //
    //        //TODO put back unresolved in queue for processing, since those
    //        // are when a node crashed and they could never be marked as resolved
    //
    //        return (Optional<CrawlDocContext>) opt;
    //    }
    //
    //    public boolean forEachQueued(
    //            BiPredicate<String, CrawlDocContext> predicate) {
    //        return queue.forEach(
    //                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    //    }
    //
    //    //--- Cache ---
    //
    //    public long getCachedCount() {
    //        return cached.size();
    //    }
    //
    //    public Optional<CrawlDocContext> getCached(String id) {
    //        return Optional.ofNullable(cached.get(id))
    //                .map(json -> SerialUtil.fromJson(json, type));
    //    }
    //
    //    public boolean forEachCached(
    //            BiPredicate<String, CrawlDocContext> predicate) {
    //        return cached.forEach(
    //                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    //    }
    //
    //    public void clearCached() {
    //        cached.clear();
    //    }
    //
    //    public synchronized void cacheProcessed() {
    //        //TODO really clear cache or keep to have longer history of
    //        // each items?
    //        cached.clear();
    //
    //        // Because we can't rename caches in all impl, we swap references
    //        var processedStoreName = durableAttribs.get(KEY_PROCESSED_MAP);
    //        var cachedStoreName = durableAttribs.get(KEY_CACHED_MAP);
    //        // If one cache name is null they should both be null and we consider
    //        // a null name to be the "processed" one.
    //        if (processedStoreName == null
    //                || PROCESSED_OR_CACHED_1.equals(processedStoreName)) {
    //            processedStoreName = PROCESSED_OR_CACHED_2;
    //            cachedStoreName = PROCESSED_OR_CACHED_1;
    //        } else {
    //            processedStoreName = PROCESSED_OR_CACHED_1;
    //            cachedStoreName = PROCESSED_OR_CACHED_2;
    //        }
    //        durableAttribs.put(KEY_PROCESSED_MAP, processedStoreName);
    //        durableAttribs.put(KEY_CACHED_MAP, cachedStoreName);
    //        var processedBefore = processed;
    //        processed = cached;
    //        cached = processedBefore;
    //    }
    //
    //    public boolean isMaxDocsProcessedReached() {
    //        //TODO replace check for "processedCount" vs "maxDocuments"
    //        // with event counts vs max committed, max processed, max etc...
    //        // Check if we merge with StopCrawlerOnMaxEventListener
    //        // or if we remove maxDocument in favor of the listener.
    //        // what about clustering?
    //        var isMax = actualMaxDocs > -1
    //                && getProcessedCount() >= actualMaxDocs;
    //        if (isMax) {
    //            LOG.info("Maximum documents reached for this crawling "
    //                    + "session: {}", actualMaxDocs);
    //        }
    //        return isMax;
    //    }
}
