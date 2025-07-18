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
package com.norconex.crawler.core.doc;

import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.session.LaunchMode;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.util.SerialUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

//TODO instead of making an atomic call to hold the queue entry,
// save if in processed store right away before returning, but
// have a flag that says "processed entirely" which is false by default,
// and also add field to capture an error.

/**
 * <p>
 * Tracks documents states and any other meta information required
 * for document processing. Acts as a facade over a few processing
 * state-spacific data stores. Includes persisting of information
 * necessary for incremental crawls and resumes.
 * <p>
 * <p>
 * A document has the following stages:
 * </p>
 * The few stages a reference should have in most implementations are:</p>
 * <ul>
 *   <li><b>Queued:</b> References extracted from documents are first queued for
 *       future processing.</li>
 *   <li><b>Processed:</b> A reference has been processed.  If the same URL is
 *       encountered again during the same run, it will be ignored.</li>
 *   <li><b>Cached:</b> When crawling is over, processed references will be
 *       cached on the next run.</li>
 * </ul>
 */
@Slf4j
public class CrawlDocLedger {

    //TODO rename DocLedger
    //TODO remove all synchronized keywords?

    public static final String KEY_PROCESSED_MAP = "ledger.process.map";
    public static final String KEY_CACHED_MAP = "ledger.cached.map";

    private static final String PROCESSED_OR_CACHED_1 = "processedOrCached";
    private static final String PROCESSED_OR_CACHED_2 = "cachedOrProcessed";

    private GridQueue<String> queue;
    private GridMap<String> processed;
    private GridMap<String> cached;
    private Class<? extends CrawlDocContext> type;
    private GridMap<String> durableAttribs;
    private CrawlContext crawlContext;
    private long actualMaxDocs;

    //NOTE: This init performs the necessary steps to "create" the ledger.
    // Actual preparation before crawling start is typically done elsewhere,
    // on the created ledger. Example, the CrawlerSpec with its
    // DocLegerInitializer.
    public void init(CrawlContext crawlerContext) {
        LOG.info("Initializing document ledger...");
        crawlContext = crawlerContext;
        type = crawlerContext.getDocContextType();
        var storage = crawlerContext.getGrid().getStorage();

        // Because we can't rename caches in all impl, we use references.
        durableAttribs = storage.getDurableAttributes();
        var processedMapName = durableAttribs.get(KEY_PROCESSED_MAP);
        var cachedMapName = durableAttribs.get(KEY_CACHED_MAP);

        //TODO if one is null they should both be null. If a concern, check
        // for that and throw an error and/or delete the non-null one.
        if (processedMapName == null) {
            processedMapName = PROCESSED_OR_CACHED_1;
            cachedMapName = PROCESSED_OR_CACHED_2;
        }

        queue = storage.getQueue("queue", String.class);
        processed = storage.getMap(processedMapName, String.class);
        cached = storage.getMap(cachedMapName, String.class);

        long maxDocs = crawlContext.getCrawlConfig().getMaxDocuments();
        actualMaxDocs = maxDocs;
        var resumed = crawlContext.getResumeState() == LaunchMode.RESUMED;
        if (resumed && maxDocs > -1) {
            actualMaxDocs += getProcessedCount();
            LOG.info("""
                An additional maximum of {} processed documents is
                added to this resumed session, for a maximum total of {}.
                """, maxDocs, actualMaxDocs);
        }
        LOG.info("Done initializing document ledger.");
    }

    /**
     * Gets whether a reference is queued, processed or being processed.
     * @param ref document reference
     * @return <code>true</code> if in "active" stage
     */
    public boolean isInActiveStage(String ref) {
        return queue.contains(ref) || processed.contains(ref);
    }

    /**
     * Gets a reference document processing stage.
     * @param ref document reference
     * @return <code>true</code> if in "active" stage
     */
    public CrawlDocStage getStage(String ref) {
        if (queue.contains(ref)) {
            return CrawlDocStage.QUEUED;
        }
        var docCtxOpt = getProcessed(ref);
        if (docCtxOpt.isPresent()) {
            return docCtxOpt.get().getProcessingStage();
        }
        return CrawlDocStage.NONE;
    }

    //--- Processed ---

    public long getProcessedCount() {
        return processed.size();
    }

    public boolean isProcessedEmpty() {
        return processed.isEmpty();
    }

    public void clearProcessed() {
        processed.clear();
    }

    public Optional<CrawlDocContext> getProcessed(String id) {
        return Optional.ofNullable(processed.get(id))
                .map(json -> SerialUtil.fromJson(json, type));
    }

    public synchronized void processed(@NonNull CrawlDocContext docCtx) {
        docCtx.setProcessingStage(CrawlDocStage.RESOLVED);
        processed.put(docCtx.getReference(), SerialUtil.toJsonString(docCtx));
        var cacheDeleted = cached.delete(docCtx.getReference());
        LOG.debug("Saved processed: {} (Deleted from cache: {})", // Deleted from active: {})",
                docCtx.getReference(), cacheDeleted);//, activeDeleted);
        crawlContext.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_PROCESSED)
                .source(crawlContext)
                .docContext(docCtx)
                .build());
    }

    public boolean forEachProcessed(
            BiPredicate<String, CrawlDocContext> predicate) {
        return processed.forEach(
                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    }

    //--- Queue ---

    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    public long getQueueCount() {
        return queue.size();
    }

    public void clearQueue() {
        queue.clear();
    }

    public void queue(@NonNull CrawlDocContext docContext) {
        docContext.setProcessingStage(CrawlDocStage.QUEUED);
        queue.put(docContext.getReference(),
                SerialUtil.toJsonString(docContext));
        LOG.debug("Saved queued: {}", docContext.getReference());
        crawlContext.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_QUEUED)
                .source(crawlContext)
                .docContext(docContext)
                .build());
    }

    @SuppressWarnings("unchecked")
    public Optional<CrawlDocContext> pollQueue() {
        var opt = queue
                .poll()
                .map(json -> SerialUtil.fromJson(json, type));
        opt.ifPresent(doc -> {
            doc.setProcessingStage(CrawlDocStage.UNRESOLVED);
            processed.put(doc.getReference(),
                    SerialUtil.toJsonString(doc));
        });

        //TODO put back unresolved in queue for processing, since those
        // are when a node crashed and they could never be marked as resolved

        return (Optional<CrawlDocContext>) opt;
    }

    public boolean forEachQueued(
            BiPredicate<String, CrawlDocContext> predicate) {
        return queue.forEach(
                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    }

    //--- Cache ---

    public long getCachedCount() {
        return cached.size();
    }

    public Optional<CrawlDocContext> getCached(String id) {
        return Optional.ofNullable(cached.get(id))
                .map(json -> SerialUtil.fromJson(json, type));
    }

    public boolean forEachCached(
            BiPredicate<String, CrawlDocContext> predicate) {
        return cached.forEach(
                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    }

    public void clearCached() {
        cached.clear();
    }

    public synchronized void cacheProcessed() {
        //TODO really clear cache or keep to have longer history of
        // each items?
        cached.clear();

        // Because we can't rename caches in all impl, we swap references
        var processedStoreName = durableAttribs.get(KEY_PROCESSED_MAP);
        var cachedStoreName = durableAttribs.get(KEY_CACHED_MAP);
        // If one cache name is null they should both be null and we consider
        // a null name to be the "processed" one.
        if (processedStoreName == null
                || PROCESSED_OR_CACHED_1.equals(processedStoreName)) {
            processedStoreName = PROCESSED_OR_CACHED_2;
            cachedStoreName = PROCESSED_OR_CACHED_1;
        } else {
            processedStoreName = PROCESSED_OR_CACHED_1;
            cachedStoreName = PROCESSED_OR_CACHED_2;
        }
        durableAttribs.put(KEY_PROCESSED_MAP, processedStoreName);
        durableAttribs.put(KEY_CACHED_MAP, cachedStoreName);
        var processedBefore = processed;
        processed = cached;
        cached = processedBefore;
    }

    public boolean isMaxDocsProcessedReached() {
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        // Check if we merge with StopCrawlerOnMaxEventListener
        // or if we remove maxDocument in favor of the listener.
        // what about clustering?
        var isMax = actualMaxDocs > -1
                && getProcessedCount() >= actualMaxDocs;
        if (isMax) {
            LOG.info("Maximum documents reached for this crawling "
                    + "session: {}", actualMaxDocs);
        }
        return isMax;
    }
}
