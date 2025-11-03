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
package com.norconex.crawler.core.ledger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Counter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;

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

    //TODO remove all synchronized keywords?

    private static final String CURRENT_LEDGER_ALIAS_KEY =
            "ledger.current.alias";

    // we use aliases for previous and current ledgers as we can't assume
    // we can physically rename them and copying data over could be
    // too inefficient. The "indexed" suffix is picked up in Infinispan
    // default config.
    private static final String LEDGER_A = "ledger_a";
    private static final String LEDGER_B = "ledger_b";

    // Status counter prefix for efficiently tracking entry counts by status
    private static final String STATUS_COUNTER_PREFIX = "status-counter-";

    private Cache<CrawlEntry> currentLedger;
    private Cache<CrawlEntry> baselineLedger;
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
        // Avoid lambda in computeIfAbsent to prevent serialization issues
        // in distributed cache replication
        var sessionCache = cacheManager.getCrawlSessionCache();
        var currentAlias = sessionCache.get(CURRENT_LEDGER_ALIAS_KEY)
                .orElseGet(() -> {
                    sessionCache.put(CURRENT_LEDGER_ALIAS_KEY, LEDGER_A);
                    return LEDGER_A;
                });
        var previousAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        //        currentLedger = new CrawlEntryCacheAdapter(cacheManager.getCache(
        //                currentAlias, CrawlEntryProtoAdapter.class));
        //        previousLedger = cacheManager.cacheExists(previousAlias)
        //                ? new CrawlEntryCacheAdapter(cacheManager
        //                        .getCache(previousAlias, CrawlEntryProtoAdapter.class))
        //                : null;
        currentLedger = cacheManager.getCache(
                currentAlias, CrawlEntry.class);
        baselineLedger = cacheManager.cacheExists(previousAlias)
                ? cacheManager.getCache(previousAlias, CrawlEntry.class)
                : null;

        // Initialize status counters for each ProcessingStatus value
        for (ProcessingStatus status : ProcessingStatus.values()) {
            statusCounters.put(status,
                    cacheManager
                            .getCounter(STATUS_COUNTER_PREFIX + status.name()));
        }

        // If this is a fresh run, initialize counters based on current cache content
        if (!session.isResumed()) {
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

    //--- Queue ---

    public boolean isQueueEmpty() {
        return statusCounters.get(ProcessingStatus.QUEUED).get() == 0;
    }

    public long getQueueCount() {
        return statusCounters.get(ProcessingStatus.QUEUED).get();
    }

    public void clearQueue() {
        currentLedger.delete(fromWhereStatusQuery(ProcessingStatus.QUEUED));
        statusCounters.get(ProcessingStatus.QUEUED).reset();
    }

    public void forEachQueued(Consumer<CrawlEntry> c) {
        forEachProcessingStatus(ProcessingStatus.QUEUED, c);
    }

    public void queue(@NonNull CrawlEntry crawlEntry) {
        crawlEntry.setProcessingStatus(ProcessingStatus.QUEUED);
        currentLedger.put(crawlEntry.getReference(), crawlEntry);
        statusCounters.get(ProcessingStatus.QUEUED).incrementAndGet();
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
                fromOrderedQueuedQuery(),
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

    public Optional<CrawlEntry> nextQueued() {
        var query = fromWhereStatusQuery(ProcessingStatus.QUEUED);
        var queuedEntries = currentLedger.queryIterator(query);
        if (!queuedEntries.hasNext()) {
            return Optional.empty();
        }
        var entry = queuedEntries.next();
        var reference = entry.getReference();
        return claimQueuedEntry(reference);
    }

    //--- Processing ---

    public long getProcessingCount() {
        return statusCounters.get(ProcessingStatus.PROCESSING).get();
    }

    public boolean isProcessingEmpty() {
        return statusCounters.get(ProcessingStatus.PROCESSING).get() == 0;
    }

    public void forEachProcessing(Consumer<CrawlEntry> c) {
        forEachProcessingStatus(ProcessingStatus.PROCESSING, c);
    }

    private void forEachProcessingStatus(
            ProcessingStatus status, Consumer<CrawlEntry> c) {
        currentLedger.queryIterator(
                fromWhereStatusQuery(status))
                .forEachRemaining(c::accept);
    }

    //--- Processed ---

    public long getProcessedCount() {
        return statusCounters.get(ProcessingStatus.PROCESSED).get();
    }

    public boolean isProcessedEmpty() {
        return statusCounters.get(ProcessingStatus.PROCESSED).get() == 0;
    }

    public void forEachProcessed(Consumer<CrawlEntry> c) {
        forEachProcessingStatus(ProcessingStatus.PROCESSED, c);
    }

    //--- Previous baseline crawl entries ---

    public long getBaselineCount() {
        return baselineLedger == null ? 0L : baselineLedger.size();
    }

    public void forEachBaseline(Consumer<CrawlEntry> c) {
        if (baselineLedger != null) {
            baselineLedger.forEach((k, v) -> c.accept(v));
        }
    }

    public Optional<CrawlEntry> getBaselineEntry(String id) {
        return baselineLedger == null
                ? Optional.empty()
                : baselineLedger.get(id);
    }

    //--- Misc. ---

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
        if (baselineLedger != null) {
            // Remove all entries individually without clearing persistent
            // storage. Using clear() tries to delete files that may still
            // be locked on Windows. Using forEach to remove each entry
            // avoids the file deletion issue while still emptying the cache.
            // The persistent storage will be reused when entries are added.
            baselineLedger.forEach((key, value) -> baselineLedger.remove(key));
        }
        var currentAlias = cacheManager.getCrawlSessionCache()
                .get(CURRENT_LEDGER_ALIAS_KEY).orElse(null);
        currentAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        cacheManager.getCrawlSessionCache()
                .put(CURRENT_LEDGER_ALIAS_KEY, currentAlias);
        var previousAlias = LEDGER_A.equals(currentAlias)
                ? LEDGER_B
                : LEDGER_A;
        currentLedger = cacheManager.getCache(currentAlias, CrawlEntry.class);
        baselineLedger = cacheManager.cacheExists(previousAlias)
                ? cacheManager.getCache(previousAlias, CrawlEntry.class)
                : null;
    }

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

    //--- Private methods ------------------------------------------------------

    /**
     * Atomically claims a QUEUED entry for processing, updating its status
     * and timestamp.
     * Updates status counters if successful.
     * Uses atomic replace() to avoid serialization issues with BiFunction.
     * @param reference the reference to claim
     * @return the updated entry if successfully claimed, otherwise empty
     */
    private Optional<CrawlEntry> claimQueuedEntry(String reference) {
        // Use atomic replace() instead of computeIfPresent() to avoid
        // serializing functions with ProtoStream marshalling.
        // This is thread-safe and works across distributed nodes.

        var current = currentLedger.get(reference);
        if (current.isEmpty()) {
            return Optional.empty();
        }

        var entry = current.get();
        if (entry.getProcessingStatus() != ProcessingStatus.QUEUED) {
            return Optional.empty();
        }

        // Create updated entry with new status
        var updatedEntry = BeanUtil.clone(entry);
        updatedEntry.setProcessingStatus(ProcessingStatus.PROCESSING);
        updatedEntry.setProcessingAt(Instant.now().atZone(ZoneOffset.UTC));

        // Atomically replace only if the current value hasn't changed
        // This ensures thread-safety without requiring function serialization
        boolean replaced = currentLedger.replace(
                reference, entry, updatedEntry);

        if (replaced) {
            statusCounters.get(ProcessingStatus.QUEUED).decrementAndGet();
            statusCounters.get(ProcessingStatus.PROCESSING).incrementAndGet();
            return Optional.of(updatedEntry);
        }

        // Entry was modified by another thread/node, retry would be needed
        return Optional.empty();
    }

    private String fromWhereStatusQuery(ProcessingStatus status) {
        return "FROM %s WHERE processingStatus = '%s'".formatted(
                CrawlEntry.class.getName(),
                status.name());
    }

    private String fromOrderedQueuedQuery() {
        return "FROM %s WHERE processingStatus = '%s' ORDER BY %s".formatted(
                CrawlEntry.class.getName(),
                ProcessingStatus.QUEUED.name(),
                CrawlEntry.Fields.queuedAt);
    }

    // To stream in slightly larger batches for efficiency on multi-nodes
    private int bonifiedBatchSize(int batchSize) {
        var nodeCnt = session.getCluster().getNodeCount();
        var nodeFactor = (int) Math.ceil(Math.min(nodeCnt - 1, 5) * 0.5);
        return batchSize + (nodeFactor * batchSize);
    }

}
