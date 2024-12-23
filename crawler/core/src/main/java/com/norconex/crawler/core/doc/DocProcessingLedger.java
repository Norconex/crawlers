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
package com.norconex.crawler.core.doc;

import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

//TODO instead of making an atomic call to hold the queue entry,
// save if in processed store right away before returning, but
// have a flag that says "processed entirely" which is false by default,
// and also add field to capture an error.

@Slf4j
public class DocProcessingLedger { //implements Closeable {

    //TODO rename DocLedger
    //TODO remove all synchronized keywords?

    public static final String KEY_PROCESSED_CACHE = "ledger.process.cache";
    public static final String KEY_CACHED_CACHE = "ledger.cached.cache";

    private static final String PROCESSED_OR_CACHED_1 = "processedOrCached";
    private static final String PROCESSED_OR_CACHED_2 = "cachedOrProcessed";

    private GridQueue<String> queue;
    private GridCache<String> processed;
    private GridCache<String> cached;
    private Class<? extends CrawlDocContext> type;
    private GridCache<String> globalCache;
    private CrawlerContext crawlerContext;
    @Getter
    private int maxDocsProcessed;

    // return true if resuming (holds records that have not been processed),
    // false otherwise
    public void init(CrawlerContext crawlerContext) {
        this.crawlerContext = crawlerContext;
        type = crawlerContext.getDocContextType();
        var storage = crawlerContext.getGrid().storage();

        // Because we can't rename caches in all impl, we use references.
        globalCache = storage.getGlobalCache();
        var processedCacheName = globalCache.get(KEY_PROCESSED_CACHE);
        var cachedCacheName = globalCache.get(KEY_CACHED_CACHE);
        //TODO if one is null they should both be null. If a concern, check
        // for that and throw an error and/or delete the non-null one.
        if (processedCacheName == null) {
            processedCacheName = PROCESSED_OR_CACHED_1;
            cachedCacheName = PROCESSED_OR_CACHED_2;
        }

        queue = storage.getQueue("queue", String.class);
        processed = storage.getCache(processedCacheName, String.class);
        cached = storage.getCache(cachedCacheName, String.class);

        var cfgMaxDocs = crawlerContext.getConfiguration().getMaxDocuments();
        maxDocsProcessed = cfgMaxDocs;
        if (cfgMaxDocs > -1 && crawlerContext.isResuming()) {
            maxDocsProcessed += getProcessedCount();
            LOG.info("""
                    An additional maximum of {} processed documents is added to
                    this resumed session, for a maximum total of {}.
                    """, cfgMaxDocs, maxDocsProcessed);
        }
    }

    /**
     * Gets whether a reference is in the ledger, excluding cache.
     * @param ref document reference
     * @return <code>true</code> if in "active" ledger
     */
    public boolean isInActiveLedger(String ref) {
        return queue.contains(ref) || processed.contains(ref);
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
        docCtx.setProcessingStage(DocProcessingStage.RESOLVED);
        processed.put(docCtx.getReference(), SerialUtil.toJsonString(docCtx));
        var cacheDeleted = cached.delete(docCtx.getReference());
        LOG.debug("Saved processed: {} (Deleted from cache: {})", // Deleted from active: {})",
                docCtx.getReference(), cacheDeleted);//, activeDeleted);
        crawlerContext.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_PROCESSED)
                .source(crawlerContext)
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
        docContext.setProcessingStage(DocProcessingStage.QUEUED);
        queue.put(docContext.getReference(),
                SerialUtil.toJsonString(docContext));
        LOG.debug("Saved queued: {}", docContext.getReference());
        crawlerContext.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_QUEUED)
                .source(crawlerContext)
                .docContext(docContext)
                .build());
    }

    @SuppressWarnings("unchecked")
    public Optional<CrawlDocContext> pollQueue() {
        var opt = queue
                .poll()
                .map(json -> SerialUtil.fromJson(json, type));
        opt.ifPresent(doc -> {
            doc.setProcessingStage(DocProcessingStage.UNRESOLVED);
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
        var processedCacheName = globalCache.get(KEY_PROCESSED_CACHE);
        var cachedCacheName = globalCache.get(KEY_CACHED_CACHE);
        // If one cache name is null they should both be null and we consider
        // a null name to be the "processed" one.
        if (processedCacheName == null
                || PROCESSED_OR_CACHED_1.equals(processedCacheName)) {
            processedCacheName = PROCESSED_OR_CACHED_2;
            cachedCacheName = PROCESSED_OR_CACHED_1;
        } else {
            processedCacheName = PROCESSED_OR_CACHED_1;
            cachedCacheName = PROCESSED_OR_CACHED_2;
        }
        globalCache.put(KEY_PROCESSED_CACHE, processedCacheName);
        globalCache.put(KEY_CACHED_CACHE, cachedCacheName);
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
        var isMax = maxDocsProcessed > -1
                && getProcessedCount() >= maxDocsProcessed;
        if (isMax) {
            LOG.info("Maximum documents reached for this crawling session: {}",
                    maxDocsProcessed);
        }
        return isMax;
    }
}
