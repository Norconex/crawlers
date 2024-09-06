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
package com.norconex.crawler.core.services;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.commons.lang.PercentFormatter;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocContext.Stage;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.store.DataStore;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

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
 *   <li><b>Active:</b> A reference is being processed.</li>
 *   <li><b>Processed:</b> A reference has been processed.  If the same URL is
 *       encountered again during the same run, it will be ignored.</li>
 *   <li><b>Cached:</b> When crawling is over, processed references will be
 *       cached on the next run.</li>
 * </ul>
 */
@Slf4j
public class DocTrackerService implements Closeable {

    // new ones
    private DataStore<CrawlDocContext> queue;
    private DataStore<CrawlDocContext> active;
    //TODO split into rejected/accepted?
    private DataStore<CrawlDocContext> processed;
    private DataStore<CrawlDocContext> cached;
    private Class<? extends CrawlDocContext> type;

    private final Crawler crawler;

    private boolean initialized;
    private boolean resuming;

    public DocTrackerService(
            @NonNull Crawler crawler,
            @NonNull Class<? extends CrawlDocContext> type) {
        this.crawler = crawler;
        this.type = type;
    }

    // return true if resuming (holds records that have not been processed),
    // false otherwise
    public void init() {
        if (initialized) {
            throw new IllegalStateException("Already initialized.");
        }

        var storeEngine = crawler.getDataStoreEngine();

        queue = storeEngine.openStore("queued", type);
        active = storeEngine.openStore("active", type);
        processed = storeEngine.openStore("processed", type);
        cached = storeEngine.openStore("cached", type);

        resuming = !isQueueEmpty() || !isActiveEmpty();
        crawler.getState().setResuming(resuming);
    }

    // prepare for processing start
    public void prepareForCrawl() {

        if (resuming) {

            // Active -> Queued
            LOG.debug(
                    "Moving any {} active URLs back into queue.",
                    crawler.getId());
            active.forEach((k, v) -> {
                queue.save(k, v);
                return true;
            });
            active.clear();

            if (LOG.isInfoEnabled()) {
                //TODO use total count to track progress independently
                var processedCount = getProcessedCount();
                var totalCount =
                        processedCount + queue.count() + cached.count();
                LOG.info(
                        "RESUMING \"{}\" at {} ({}/{}).",
                        crawler.getId(),
                        PercentFormatter.format(
                                processedCount, totalCount, 2, Locale.ENGLISH),
                        processedCount, totalCount);
            }
        } else {
            var storeEngine = crawler.getDataStoreEngine();
            //TODO really clear cache or keep to have longer history of
            // each items?
            cached.clear();
            active.clear();
            queue.clear();

            // Valid Processed -> Cached
            LOG.debug("Caching any valid references from previous run.");

            //TODO make swap a method on store engine?

            // cached -> swap
            storeEngine.renameStore(cached, "swap");
            var swap = cached;

            // processed -> cached
            storeEngine.renameStore(processed, "cached");
            cached = processed;

            // swap -> processed
            storeEngine.renameStore(swap, "processed");
            processed = swap;

            if (LOG.isInfoEnabled()) {
                var cacheCount = cached.count();
                if (cacheCount > 0) {
                    LOG.info(
                            "STARTING an incremental crawl from previous {} "
                                    + "valid references.",
                            cacheCount);
                } else {
                    LOG.info("STARTING a fresh crawl.");
                }
            }
        }

        initialized = true;
        //          return resuming;
    }

    public Stage getProcessingStage(String id) {
        if (active.exists(id)) {
            return Stage.ACTIVE;
        }
        if (queue.exists(id)) {
            return Stage.QUEUED;
        }
        if (processed.exists(id)) {
            return Stage.PROCESSED;
        }
        return null;
    }

    //--- Active ---

    public long getActiveCount() {
        return active.count();
    }

    public boolean isActiveEmpty() {
        return active.isEmpty();
    }

    public boolean forEachActive(
            BiPredicate<String, CrawlDocContext> predicate) {
        return active.forEach(predicate);
    }

    //--- Processed ---

    public long getProcessedCount() {
        return processed.count();
    }

    public boolean isProcessedEmpty() {
        return processed.isEmpty();
    }

    public Optional<CrawlDocContext> getProcessed(String id) {
        return processed.find(id);
    }

    public synchronized void processed(CrawlDocContext docRec) {
        Objects.requireNonNull(docRec, "'docInfo' must not be null.");
        processed.save(docRec.getReference(), docRec);
        var cacheDeleted = cached.delete(docRec.getReference());
        var activeDeleted = active.delete(docRec.getReference());
        LOG.debug(
                "Saved processed: {} "
                        + "(Deleted from cache: {}; Deleted from active: {})",
                docRec.getReference(), cacheDeleted, activeDeleted);
        crawler.fire(
                CrawlerEvent.builder()
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
        return queue.count();
    }

    public void queue(CrawlDocContext docRec) {
        Objects.requireNonNull(docRec, "'docInfo' must not be null.");
        queue.save(docRec.getReference(), docRec);
        LOG.debug("Saved queued: {}", docRec.getReference());
        crawler.fire(
                CrawlerEvent.builder()
                        .name(CrawlerEvent.DOCUMENT_QUEUED)
                        .source(crawler)
                        .docContext(docRec)
                        .build());
    }

    // get and delete and mark as active
    public synchronized Optional<CrawlDocContext> pollQueue() {
        var docInfo = queue.deleteFirst();
        if (docInfo.isPresent()) {
            active.save(docInfo.get().getReference(), docInfo.get());
            LOG.debug("Saved active: {}", docInfo.get().getReference());
        }
        return docInfo;
    }

    public boolean forEachQueued(
            BiPredicate<String, CrawlDocContext> predicate) {
        return queue.forEach(predicate);
    }

    //--- Cache ---

    public Optional<CrawlDocContext> getCached(String id) {
        return cached.find(id);
    }

    public boolean forEachCached(
            BiPredicate<String, CrawlDocContext> predicate) {
        return cached.forEach(predicate);
    }

    @Override
    public void close() {
        try {
            ofNullable(queue).ifPresent(DataStore::close);
            ofNullable(active).ifPresent(DataStore::close);
            ofNullable(processed).ifPresent(DataStore::close);
            ofNullable(cached).ifPresent(DataStore::close);
        } finally {
            initialized = false;
        }
    }
}
