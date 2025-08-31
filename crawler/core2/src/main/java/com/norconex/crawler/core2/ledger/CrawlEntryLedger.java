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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Counter;
import com.norconex.crawler.core.cluster.impl.infinispan.CrawlEntryCacheAdapter;
import com.norconex.crawler.core.cluster.impl.infinispan.CrawlEntryProtoAdapter;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.event.CrawlerEvent;

import lombok.NonNull;
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
public final class CrawlEntryLedger {

    //TODO rename DocLedger
    //TODO remove all synchronized keywords?

    private static final String CURRENT_LEDGER_ALIAS_KEY =
            "ledger.current.alias";

    //    public static final String KEY_CACHED_MAP = "ledger.cached.map";

    // we use aliases for previous and current ledgers as we can't assume
    // we can physically rename them and copying data over could be
    // too inefficient. The "indexed" suffix is picked up in Infinispan
    // default config.
    private static final String LEDGER_A = "ledger_a";
    private static final String LEDGER_B = "ledger_b";

    // Status counter prefix for efficiently tracking entry counts by status
    private static final String STATUS_COUNTER_PREFIX = "status-counter-";

    private Cache<CrawlEntry> currentLedger;
    private Cache<CrawlEntry> previousLedger;
    private long totalMaxDocsThisRun;

    // Map to hold references to status-specific counters
    private final Map<ProcessingStatus, Counter> statusCounters =
            new EnumMap<>(ProcessingStatus.class);
    private CacheManager cacheManager;
    private CrawlSession session;

    public void init(CrawlSession session) {
        LOG.info("Initializing crawl entry ledger...");
        this.session = session;
        cacheManager = session.getCluster().getCacheManager();

        // Caches:
        var currentAlias = cacheManager.getCrawlSessionCache()
                .computeIfAbsent(CURRENT_LEDGER_ALIAS_KEY, k -> LEDGER_A);
        var previousAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        currentLedger = new CrawlEntryCacheAdapter(cacheManager.getCache(
                currentAlias, CrawlEntryProtoAdapter.class));
        previousLedger = cacheManager.cacheExists(previousAlias)
                ? new CrawlEntryCacheAdapter(cacheManager
                        .getCache(previousAlias, CrawlEntryProtoAdapter.class))
                : null;

        // Initialize status counters for each ProcessingStatus value
        for (ProcessingStatus status : ProcessingStatus.values()) {
            statusCounters.put(status,
                    cacheManager
                            .getCounter(STATUS_COUNTER_PREFIX + status.name()));
        }

        // If this is a fresh run, initialize counters based on current cache content
        if (session.isResumed()) {
            initializeStatusCounters();
        }

        // Max docs
        long runMaxDocs =
                session.getCrawlContext().getCrawlConfig().getMaxDocuments();
        totalMaxDocsThisRun = runMaxDocs;
        var resumed = session.isResumed();
        if (resumed && runMaxDocs > -1) {
            totalMaxDocsThisRun +=
                    statusCounters.get(ProcessingStatus.PROCESSED).get();
            LOG.info("""
                    An additional maximum of {} processed documents is
                    added to this resumed session, for a maximum total of {}.
                    """, runMaxDocs,
                    totalMaxDocsThisRun);
        }

        LOG.info("Done initializing crawl entry ledger.");
    }

    //    private Counter getStatusCounter(ProcessingStatus status) {
    //        statusCounters.get(status)
    //    }
    //    private Counter getProcessingCounter() {
    //
    //    }
    //    private Counter getProcessedCounter() {
    //
    //    }

    /**
     * Initialize status counters by querying the cache.
     * This is only needed for the first run to establish baseline counts.
     */
    private void initializeStatusCounters() {
        LOG.info("Initializing status counters...");
        for (ProcessingStatus status : ProcessingStatus.values()) {
            var count = currentLedger.count(
                    "FROM %s WHERE processingStatus = '%s'"
                            .formatted(
                                    CrawlEntry.class.getName(),
                                    status.name()));
            var counter = statusCounters.get(status);
            counter.reset();
            counter.set(count);
            LOG.debug("Status counter for {} initialized to {}", status, count);
        }
        LOG.info("Status counters initialized.");
    }

    /**
     * Updates an entry in the ledger, maintaining the status counters.
     * @param entry the entry to update
     * @return the previous entry if it existed
     */
    public Optional<CrawlEntry> updateEntry(CrawlEntry entry) {
        var reference = entry.getReference();
        ProcessingStatus newStatus = entry.getProcessingStatus();

        var previous = currentLedger.get(reference);
        currentLedger.put(reference, entry);

        // Update status counters if needed
        if (previous.isPresent()) {
            ProcessingStatus oldStatus = previous.get().getProcessingStatus();
            if (oldStatus != newStatus) {
                statusCounters.get(oldStatus).decrementAndGet();
                statusCounters.get(newStatus).incrementAndGet();
                LOG.trace("Status changed for {}: {} -> {}", reference,
                        oldStatus, newStatus);
            }
        } else {
            // New entry
            statusCounters.get(newStatus).incrementAndGet();
            LOG.trace("New entry added with status {}: {}", newStatus,
                    reference);
        }

        return previous;
    }

    /**
     * Removes an entry from the ledger, updating the status counters.
     * @param reference the reference to remove
     * @return the removed entry if it existed
     */
    public Optional<CrawlEntry> removeEntry(String reference) {
        var entry = currentLedger.get(reference);
        if (entry.isPresent()) {
            ProcessingStatus status = entry.get().getProcessingStatus();
            currentLedger.remove(reference);
            statusCounters.get(status).decrementAndGet();
            LOG.trace("Entry removed with status {}: {}", status, reference);
        }
        return entry;
    }

    /**
     * Whether a reference exists in the current ledger.
     * @param ref document reference
     * @return {@code true} if existing
     */
    public boolean exists(String ref) {
        return currentLedger.containsKey(ref);
    }

    /**
     * Gets a reference processing status. If a document does not exist,
     * the processing status will be {@link ProcessingStatus#UNTRACKED}.
     * @param ref document reference
     * @return the processing status
     */
    public ProcessingStatus getProcessingStatus(String ref) {
        return currentLedger.get(ref)
                .map(CrawlEntry::getProcessingStatus)
                .orElse(ProcessingStatus.UNTRACKED);
    }

    Object deleteMe() {
        return totalMaxDocsThisRun;
    }

    //--- Processed ---

    public long getProcessedCount() {
        return statusCounters.get(ProcessingStatus.PROCESSED).get();
    }

    public boolean isProcessedEmpty() {
        return statusCounters.get(ProcessingStatus.PROCESSED).get() == 0;
    }
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

    public void forEachQueued(Consumer<CrawlEntry> c) {
        forEachProcessingStatus(ProcessingStatus.QUEUED, c);
    }

    public void forEachProcessing(Consumer<CrawlEntry> c) {
        forEachProcessingStatus(ProcessingStatus.PROCESSING, c);
    }

    public void forEachProcessed(Consumer<CrawlEntry> c) {
        forEachProcessingStatus(ProcessingStatus.PROCESSED, c);
    }

    private void forEachProcessingStatus(
            ProcessingStatus status, Consumer<CrawlEntry> c) {
        currentLedger.queryIterator("FROM %s WHERE %s".formatted(
                CrawlEntry.class.getName(),
                fromWhereStatusQuery(status)))
                .forEachRemaining(c::accept);
    }

    public void forEachPrevious(Consumer<CrawlEntry> c) {
        if (previousLedger != null) {
            previousLedger.forEach((k, v) -> c.accept(v));
        }
    }

    //--- Queue ---

    public boolean isQueueEmpty() {
        return statusCounters.get(ProcessingStatus.QUEUED).get() == 0;
    }

    public long getQueueCount() {
        return statusCounters.get(ProcessingStatus.QUEUED).get();
    }

    public void clearQueue() {
        currentLedger.delete(fromWhereStatusQuery(ProcessingStatus.QUEUED));
    }

    public void queue(@NonNull CrawlEntry crawlEntry) {
        crawlEntry.setProcessingStatus(ProcessingStatus.QUEUED);
        currentLedger.put(crawlEntry.getReference(), crawlEntry);
        LOG.debug("Saved queued: {}", crawlEntry.getReference());
        session.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_QUEUED)
                .source(session)
                .crawlEntry(crawlEntry)
                .build());
    }

    public List<CrawlEntry> nextQueuedBatch(int batchSize) {
        List<CrawlEntry> batch = new ArrayList<>(batchSize);
        currentLedger.queryStream(
                fromWhereStatusQuery(ProcessingStatus.QUEUED),
                entry -> {
                    if (batch.size() >= batchSize)
                        return;
                    var ref = entry.getReference();
                    var claimed = claimQueuedEntry(ref);
                    claimed.ifPresent(batch::add);
                },
                bonifiedBatchSize(batchSize));
        return batch;
    }

    // To stream in slightly larger batches for efficiency on multi-nodes
    private int bonifiedBatchSize(int batchSize) {
        var nodeCnt = session.getCluster().getNodeCount();
        var nodeFactor = (int) Math.ceil(Math.min(nodeCnt - 1, 5) * 0.5);
        return batchSize + (nodeFactor * batchSize);
    }

    public Optional<CrawlEntry> nextQueued() {
        // Find the first QUEUED entry
        var query = fromWhereStatusQuery(ProcessingStatus.QUEUED);
        var queuedEntries = currentLedger.queryIterator(query);
        if (!queuedEntries.hasNext()) {
            return Optional.empty();
        }
        var entry = queuedEntries.next();
        var reference = entry.getReference();
        return claimQueuedEntry(reference);
    }

    //    public boolean forEachQueued(
    //            BiPredicate<String, CrawlEntry> predicate) {
    //        return queue.forEach(
    //                (k, v) -> predicate.test(k, SerialUtil.fromJson(v, type)));
    //    }
    //
    //--- Previous crawl entries ---

    public long getPreviousEntryCount() {
        return previousLedger == null ? 0L : previousLedger.size();
    }

    public Optional<CrawlEntry> getPreviousEntry(String id) {
        return previousLedger == null
                ? Optional.empty()
                : previousLedger.get(id);

        //        return previousLedger.get(id)
        //                .map(json -> (CrawlEntry) SerialUtil.fromJson(json,
        //                        session.getCrawlContext().getCrawlEntryType()));
    }

    /**
     * Gets all entries matching the given processing status.
     * @param status the processing status to match
     * @return matching entries
     */
    public Iterator<CrawlEntry> getEntriesByStatus(ProcessingStatus status) {
        return currentLedger.queryIterator(fromWhereStatusQuery(status));
    }

    /**
     * Counts entries with the given processing status.
     * Uses an efficient O(1) counter instead of executing a query.
     * @param status the processing status to count
     * @return count of entries with the given status
     */
    public long countByStatus(ProcessingStatus status) {
        return statusCounters.get(status).get();
    }

    /**
     * Deletes all entries with the given processing status.
     * Also updates the status counter accordingly.
     * @param status the processing status of entries to delete
     * @return number of entries deleted
     */
    public long deleteByStatus(ProcessingStatus status) {
        var count = currentLedger.delete(fromWhereStatusQuery(status));
        // Reset the counter to 0 since we deleted all entries with this status
        statusCounters.get(status).reset();
        return count;
    }

    /**
     * Make the current leger the pervious one. and blank the current one
     * to start fresh.
     */
    public synchronized void archiveCurrentLedger() {
        //TODO really clear cache or keep to have longer history of
        // each items?
        if (previousLedger != null) {
            previousLedger.clear();
        }

        var currentAlias = cacheManager.getCrawlSessionCache()
                .get(CURRENT_LEDGER_ALIAS_KEY).get();
        currentAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        cacheManager.getCrawlSessionCache()
                .put(CURRENT_LEDGER_ALIAS_KEY, currentAlias);
        var previousAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        currentLedger = cacheManager.getCache(currentAlias, CrawlEntry.class);
        previousLedger = cacheManager.cacheExists(previousAlias)
                ? cacheManager.getCache(previousAlias, CrawlEntry.class)
                : null;
    }

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
    //        public synchronized void cacheProcessed() {
    //            //TODO really clear cache or keep to have longer history of
    //            // each items?
    //            cached.clear();
    //
    //            // Because we can't rename caches in all impl, we swap references
    //            var processedStoreName = durableAttribs.get(KEY_PROCESSED_MAP);
    //            var cachedStoreName = durableAttribs.get(KEY_CACHED_MAP);
    //            // If one cache name is null they should both be null and we consider
    //            // a null name to be the "processed" one.
    //            if (processedStoreName == null
    //                    || PROCESSED_OR_CACHED_1.equals(processedStoreName)) {
    //                processedStoreName = PROCESSED_OR_CACHED_2;
    //                cachedStoreName = PROCESSED_OR_CACHED_1;
    //            } else {
    //                processedStoreName = PROCESSED_OR_CACHED_1;
    //                cachedStoreName = PROCESSED_OR_CACHED_2;
    //            }
    //            durableAttribs.put(KEY_PROCESSED_MAP, processedStoreName);
    //            durableAttribs.put(KEY_CACHED_MAP, cachedStoreName);
    //            var processedBefore = processed;
    //            processed = cached;
    //            cached = processedBefore;
    //        }
    //
    public boolean isMaxDocsProcessedReached() {
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        // Check if we merge with StopCrawlerOnMaxEventListener
        // or if we remove maxDocument in favor of the listener.
        // what about clustering?
        var isMax = totalMaxDocsThisRun > -1
                && getProcessedCount() >= totalMaxDocsThisRun;
        if (isMax) {
            LOG.info("Maximum documents reached for this crawling "
                    + "session: {}", totalMaxDocsThisRun);
        }
        return isMax;
    }

    /**
     * Atomically claims a QUEUED entry for processing, updating its status and timestamp.
     * Updates status counters if successful.
     * @param reference the reference to claim
     * @return the updated entry if successfully claimed, otherwise empty
     */
    private Optional<CrawlEntry> claimQueuedEntry(String reference) {
        var updated = currentLedger.computeIfPresent(reference, (k, v) -> {
            if (v.getProcessingStatus() == ProcessingStatus.QUEUED) {
                v.setProcessingStatus(ProcessingStatus.PROCESSING);
                v.setProcessingAt(ZonedDateTime.now());
            }
            return v;
        });
        if (updated.isPresent() && updated.get()
                .getProcessingStatus() == ProcessingStatus.PROCESSING) {
            statusCounters.get(ProcessingStatus.QUEUED).decrementAndGet();
            statusCounters.get(ProcessingStatus.PROCESSING).incrementAndGet();
            return updated;
        }
        return Optional.empty();
    }

    private String fromWhereStatusQuery(ProcessingStatus status) {
        return "FROM %s WHERE processingStatus = '%s'"
                .formatted(CrawlEntry.class.getName(), status.name());
    }

    private String fromOrderedQueuedQuery() {
        return "FROM %s WHERE processingStatus = '%s' ORDER BY %s"
                .formatted(
                        CrawlEntry.class.getName(),
                        ProcessingStatus.QUEUED.name(),
                        CrawlEntry.Fields.queuedAt);
    }

}
