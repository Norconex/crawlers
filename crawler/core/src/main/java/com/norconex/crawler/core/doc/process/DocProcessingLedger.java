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
package com.norconex.crawler.core.doc.process;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocContext.Stage;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.grid.GridStore;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DocProcessingLedger implements Closeable {

    public static final String KEY_RESUMING = "ledger.resuming";
    public static final String KEY_PROCESSED_CACHE = "ledger.process.cache";
    public static final String KEY_CACHED_CACHE = "ledger.cached.cache";

    private static final String PROCESSED_OR_CACHED_1 = "processed-or-cached";
    private static final String PROCESSED_OR_CACHED_2 = "cached-or-processed";

    private GridQueue<CrawlDocContext> queue;
    private GridCache<CrawlDocContext> processed;
    private GridCache<CrawlDocContext> cached;
    private Class<? extends CrawlDocContext> type;
    private GridCache<String> globalCache;

    private final Crawler crawler;

    public DocProcessingLedger(
            @NonNull Crawler crawler,
            @NonNull Class<? extends CrawlDocContext> type) {
        this.crawler = crawler;
        this.type = type;
    }

    // return true if resuming (holds records that have not been processed),
    // false otherwise
    public void init() {
        var grid = crawler.getGridSystem();

        // Because we can't rename caches in all impl, we use references.
        globalCache = grid.getGlobalCache();
        var processedCacheName = globalCache.get(KEY_PROCESSED_CACHE);
        var cachedCacheName = globalCache.get(KEY_CACHED_CACHE);
        //TODO if one is null they should both be null. If a concern, check
        // for that and throw an error and/or delete the non-null one.
        if (processedCacheName == null) {
            processedCacheName = PROCESSED_OR_CACHED_1;
            cachedCacheName = PROCESSED_OR_CACHED_2;
        }

        queue = grid.getQueue("queue", type);
        //        active = storeEngine.openStore("active", type);
        processed = grid.getCache(processedCacheName, type);
        cached = grid.getCache(cachedCacheName, type);

        var resuming = !isQueueEmpty();// || !isActiveEmpty();
        crawler.getState().setResuming(resuming);

        grid.getGlobalCache().put(KEY_RESUMING, Boolean.toString(resuming));
    }

    public Stage getProcessingStage(String id) {
        //        if (active.exists(id)) {
        //            return Stage.ACTIVE;
        //        }
        if (queue.contains(id)) {
            return Stage.QUEUED;
        }
        if (processed.contains(id)) {
            return Stage.PROCESSED;
        }
        return null;
    }

    //--- Active ---

    //    public long getActiveCount() {
    //        return active.count();
    //    }
    //
    //    public boolean isActiveEmpty() {
    //        return active.isEmpty();
    //    }
    //
    //    public boolean forEachActive(
    //            BiPredicate<String, CrawlDocContext> predicate) {
    //        return active.forEach(predicate);
    //    }

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
        return Optional.ofNullable(processed.get(id));
    }

    public synchronized void processed(@NonNull CrawlDocContext docRec) {
        processed.put(docRec.getReference(), docRec);
        var cacheDeleted = cached.delete(docRec.getReference());
        //        var activeDeleted = active.delete(docRec.getReference());
        LOG.debug("Saved processed: {} (Deleted from cache: {})", // Deleted from active: {})",
                docRec.getReference(), cacheDeleted);//, activeDeleted);
        crawler.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_PROCESSED)
                .source(crawler)
                .docContext(docRec)
                .build());
    }

    public boolean forEachProcessed(
            BiPredicate<String, CrawlDocContext> predicate) {
        return processed.forEach(predicate);
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
        queue.put(docContext.getReference(), docContext);
        LOG.debug("Saved queued: {}", docContext.getReference());
        crawler.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_QUEUED)
                .source(crawler)
                .docContext(docContext)
                .build());
    }

    // get and delete and mark as active
    public synchronized Optional<CrawlDocContext> pollQueue() {

        //        if (docInfo.isPresent()) {
        //            active.save(docInfo.get().getReference(), docInfo.get());
        //            LOG.debug("Saved active: {}", docInfo.get().getReference());
        //        }
        return queue.poll();
    }

    public boolean forEachQueued(
            BiPredicate<String, CrawlDocContext> predicate) {
        return queue.forEach(predicate);
    }

    //--- Cache ---

    public long getCachedCount() {
        return cached.size();
    }

    public Optional<CrawlDocContext> getCached(String id) {
        return Optional.ofNullable(cached.get(id));
    }

    public boolean forEachCached(
            BiPredicate<String, CrawlDocContext> predicate) {
        return cached.forEach(predicate);
    }

    public void clearCached() {
        cached.clear();
    }

    @Override
    public void close() {
        ofNullable(queue).ifPresent(GridStore::close);
        //            ofNullable(active).ifPresent(GridStore::close);
        ofNullable(processed).ifPresent(GridStore::close);
        ofNullable(cached).ifPresent(GridStore::close);
    }

    public synchronized void cacheProcessed() {

        //TODO really clear cache or keep to have longer history of
        // each items?
        cached.clear();

        // Because we can't rename caches in all impl, we swap references
        var processedCacheName = globalCache.get(KEY_PROCESSED_CACHE);
        var cachedCacheName = globalCache.get(KEY_CACHED_CACHE);
        //TODO if one is null they should both be null. If a concern, check
        // for that and throw an error and/or delete the non-null one.
        if (processedCacheName == null) {
            return;
        }
        if (PROCESSED_OR_CACHED_1.equals(processedCacheName)) {
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
}
