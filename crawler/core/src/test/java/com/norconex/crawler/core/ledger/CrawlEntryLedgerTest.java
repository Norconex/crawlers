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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterNode;
import com.norconex.crawler.core.cluster.support.InMemoryCacheManager;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Unit tests for {@link CrawlEntryLedger}.
 *
 * <p>Uses in-memory caches and Mockito stubs — no Hazelcast required.
 * Covers the full lifecycle: queue → process → processed → archive → resume.
 */
@ExtendWith(MockitoExtension.class)
class CrawlEntryLedgerTest {

    @Mock(lenient = true)
    CrawlSession session;
    @Mock(lenient = true)
    Cluster cluster;
    @Mock(lenient = true)
    ClusterNode localNode;
    @Mock(lenient = true)
    CrawlContext crawlContext;

    private InMemoryCacheManager cacheManager;
    private CrawlEntryLedger ledger;

    @BeforeEach
    void setUp() {
        cacheManager = new InMemoryCacheManager();
        // Wire in real CrawlConfig so maxDocuments defaults to -1 (unlimited).
        var crawlConfig = new CrawlConfig();

        lenient().when(session.getCluster()).thenReturn(cluster);
        lenient().when(cluster.getCacheManager()).thenReturn(cacheManager);
        lenient().when(cluster.getLocalNode()).thenReturn(localNode);
        lenient().when(localNode.getNodeName()).thenReturn("unit-test-node");
        lenient().when(session.getCrawlContext()).thenReturn(crawlContext);
        lenient().when(crawlContext.getCrawlConfig()).thenReturn(crawlConfig);
        lenient().when(session.isResumed()).thenReturn(false);

        ledger = new CrawlEntryLedger();
        ledger.init(session);
        // archiveCurrentLedger() requires the alias key to already exist in
        // session cache (coordinator responsibility before any rotation).
        ledger.ensureCurrentLedgerAliasExists();
    }

    // -----------------------------------------------------------------
    // Queuing
    // -----------------------------------------------------------------

    @Test
    void testQueue_addsEntryToLedgerAndQueue() {
        ledger.queue(entry("ref-1"));

        assertThat(ledger.exists("ref-1")).isTrue();
        assertThat(ledger.getProcessingStatus("ref-1"))
                .isEqualTo(ProcessingStatus.QUEUED);
        assertThat(ledger.getQueueCount()).isEqualTo(1);
    }

    @Test
    void testQueue_duplicateIsIgnored() {
        ledger.queue(entry("ref-1"));
        ledger.queue(entry("ref-1")); // second call must be a no-op

        assertThat(ledger.getQueueCount()).isEqualTo(1);
        assertThat(ledger.countByStatus(ProcessingStatus.QUEUED))
                .isEqualTo(1);
    }

    @Test
    void testQueue_multipleEntries_countsAreCorrect() {
        for (int i = 0; i < 5; i++) {
            ledger.queue(entry("ref-" + i));
        }

        assertThat(ledger.getQueueCount()).isEqualTo(5);
        assertThat(ledger.countByStatus(ProcessingStatus.QUEUED))
                .isEqualTo(5);
        assertThat(ledger.isQueueEmpty()).isFalse();
    }

    // -----------------------------------------------------------------
    // Batch dequeuing → PROCESSING state
    // -----------------------------------------------------------------

    @Test
    void testNextQueuedBatch_promotesEntryToProcessing() {
        ledger.queue(entry("ref-1"));

        var batch = ledger.nextQueuedBatch(10);

        assertThat(batch).hasSize(1);
        assertThat(batch.get(0).getReference()).isEqualTo("ref-1");
        assertThat(ledger.getProcessingStatus("ref-1"))
                .isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(ledger.getQueueCount()).isEqualTo(0);
        assertThat(ledger.getProcessingCount()).isEqualTo(1);
    }

    @Test
    void testNextQueuedBatch_respectsBatchSizeLimit() {
        for (int i = 0; i < 10; i++) {
            ledger.queue(entry("ref-" + i));
        }

        var batch = ledger.nextQueuedBatch(3);

        assertThat(batch).hasSize(3);
        // Remaining references stay in the queue.
        assertThat(ledger.getQueueCount()).isEqualTo(7);
    }

    // -----------------------------------------------------------------
    // Requeue on resume
    // -----------------------------------------------------------------

    @Test
    void testRequeuProcessingEntries_movesBackToQueued() {
        ledger.queue(entry("ref-a"));
        ledger.nextQueuedBatch(1); // → PROCESSING

        var requeued = ledger.requeueProcessingEntries();

        assertThat(requeued).isEqualTo(1);
        assertThat(ledger.getProcessingStatus("ref-a"))
                .isEqualTo(ProcessingStatus.QUEUED);
        assertThat(ledger.getQueueCount()).isEqualTo(1);
        assertThat(ledger.getProcessingCount()).isZero();
    }

    @Test
    void testRequeuQueuedEntries_addsQueuedRefsBackToQueue() {
        ledger.queue(entry("ref-b"));
        // Simulate losing the queue (queue cleared externally); ledger still
        // has the QUEUED entries.
        cacheManager.getCacheQueue("queue-refs", String.class).clear();
        assertThat(ledger.getQueueCount()).isEqualTo(0);

        var requeued = ledger.requeueQueuedEntries();

        assertThat(requeued).isEqualTo(1);
        assertThat(ledger.getQueueCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------
    // Entry lifecycle — update and remove
    // -----------------------------------------------------------------

    @Test
    void testUpdateEntry_changesStatus() {
        ledger.queue(entry("ref-1"));
        var entry = ledger.nextQueuedBatch(1).get(0);
        entry.setProcessingStatus(ProcessingStatus.PROCESSED);
        entry.setProcessingOutcome(ProcessingOutcome.NEW);

        ledger.updateEntry(entry);

        assertThat(ledger.getProcessingStatus("ref-1"))
                .isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(ledger.getProcessedCount()).isEqualTo(1);
    }

    @Test
    void testRemoveEntry_deletesFromLedger() {
        ledger.queue(entry("ref-1"));

        ledger.removeEntry("ref-1");

        assertThat(ledger.exists("ref-1")).isFalse();
    }

    // -----------------------------------------------------------------
    // Max-documents limit
    // -----------------------------------------------------------------

    @Test
    void testIsMaxDocsProcessedReached_notReachedWhenUnlimited() {
        for (int i = 0; i < 100; i++) {
            ledger.queue(entry("ref-" + i));
        }
        assertThat(ledger.isMaxDocsProcessedReached()).isFalse();
    }

    @Test
    void testIsMaxDocsProcessedReached_trueAtLimit() {
        // Reinitialise with a 2-document limit.
        var cappedConfig = new CrawlConfig();
        cappedConfig.setMaxDocuments(2);
        lenient().when(crawlContext.getCrawlConfig()).thenReturn(cappedConfig);
        ledger = new CrawlEntryLedger();
        ledger.init(session);

        // Queue 3, process 2.
        for (int i = 0; i < 3; i++) {
            ledger.queue(entry("ref-" + i));
        }
        var toProcess = ledger.nextQueuedBatch(2);
        for (var e : toProcess) {
            e.setProcessingStatus(ProcessingStatus.PROCESSED);
            ledger.updateEntry(e);
        }

        assertThat(ledger.isMaxDocsProcessedReached()).isTrue();
    }

    // -----------------------------------------------------------------
    // Ledger rotation (archive)
    // -----------------------------------------------------------------

    @Test
    void testArchiveCurrentLedger_currentBecomesBaselineAndNewCurrentIsEmpty() {
        ledger.queue(entry("ref-1"));
        ledger.queue(entry("ref-2"));

        ledger.archiveCurrentLedger();

        // After rotation the current ledger is empty.
        assertThat(ledger.countByStatus(ProcessingStatus.QUEUED)).isZero();
        assertThat(ledger.getQueueCount()).isEqualTo(2); // queue not cleared

        // The old entries are now in the baseline.
        assertThat(ledger.getBaselineCount()).isEqualTo(2);
        assertThat(ledger.getBaselineEntry("ref-1")).isPresent();
        assertThat(ledger.getBaselineEntry("ref-2")).isPresent();
    }

    @Test
    void testArchiveCurrentLedger_doubleRotationClearsOldBaseline() {
        ledger.queue(entry("ref-1"));
        ledger.archiveCurrentLedger(); // ledger_b is now current
        ledger.queue(entry("ref-2")); // goes to ledger_b
        ledger.archiveCurrentLedger(); // rotates back to ledger_a; old baseline cleared

        // Only ref-2 should be in the new baseline.
        assertThat(ledger.getBaselineCount()).isEqualTo(1);
        assertThat(ledger.getBaselineEntry("ref-2")).isPresent();
        assertThat(ledger.getBaselineEntry("ref-1")).isEmpty();
    }

    // -----------------------------------------------------------------
    // Clear queue
    // -----------------------------------------------------------------

    @Test
    void testClearQueue_removesQueuedEntriesAndQueue() {
        ledger.queue(entry("ref-1"));
        ledger.queue(entry("ref-2"));

        ledger.clearQueue();

        assertThat(ledger.getQueueCount()).isEqualTo(0);
        assertThat(ledger.isQueueEmpty()).isTrue();
        assertThat(ledger.countByStatus(ProcessingStatus.QUEUED)).isZero();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static CrawlEntry entry(String ref) {
        return new CrawlEntry(ref);
    }
}
