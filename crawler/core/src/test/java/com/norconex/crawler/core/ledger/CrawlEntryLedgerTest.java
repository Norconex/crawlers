package com.norconex.crawler.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;

import com.norconex.crawler.core._DELETE.CrawlTest;
import com.norconex.crawler.core._DELETE.CrawlTest.Focus;
import com.norconex.crawler.core._DELETE.MockSingleNodeConnector;
import com.norconex.crawler.core.session.CrawlSession;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class CrawlEntryLedgerTest {

    @CrawlTest(
        focus = Focus.SESSION,
        clusters = { MockSingleNodeConnector.class }
    )
    void testStatusCountersReflectActualEntries(CrawlSession session) {
        var ledger = session.getCrawlContext().getCrawlEntryLedger();
        var refs = List.of("ref1", "ref2", "ref3");

        // Queue entries
        refs.forEach(ref -> {
            var entry = new CrawlEntry(ref);
            ledger.queue(entry);
        });
        assertThat(ledger.getQueueCount()).isEqualTo(3);
        assertThat(ledger.getProcessingCount()).isZero();
        assertThat(ledger.getProcessedCount()).isZero();

        // Claim entries for processing
        var batch = ledger.nextQueuedBatch(3);
        assertThat(batch).hasSize(3);
        assertThat(ledger.getQueueCount()).isZero();
        assertThat(ledger.getProcessingCount()).isEqualTo(3);
        assertThat(ledger.getProcessedCount()).isZero();

        // Mark entries as processed
        refs.forEach(ref -> {
            var entry = new CrawlEntry(ref);
            entry.setProcessingStatus(ProcessingStatus.PROCESSED);
            ledger.updateEntry(entry);
        });
        assertThat(ledger.getQueueCount()).isZero();
        assertThat(ledger.getProcessingCount()).isZero();
        assertThat(ledger.getProcessedCount()).isEqualTo(3);

        // Remove one entry
        ledger.removeEntry("ref1");
        assertThat(ledger.getProcessedCount()).isEqualTo(2);
    }

    @Test
    void testRequeueQueuedEntriesOrdersByQueuedAt() {
        // Mocks
        var cacheManager = Mockito.mock(
                com.norconex.crawler.core.cluster.CacheManager.class);
        var cacheMap = Mockito.mock(
                com.norconex.crawler.core.cluster.CacheMap.class);
        var cacheQueue = Mockito.mock(
                com.norconex.crawler.core.cluster.CacheQueue.class);
        var session = Mockito.mock(CrawlSession.class);
        var cluster = Mockito.mock(
                com.norconex.crawler.core.cluster.Cluster.class);
        var crawlContext = Mockito.mock(
                com.norconex.crawler.core.context.CrawlContext.class);

        when(session.getCluster()).thenReturn(cluster);
        when(cluster.getCacheManager()).thenReturn(cacheManager);
        when(session.getCrawlContext()).thenReturn(crawlContext);

        when(cacheManager.getCacheQueue("crawlQueue", String.class))
                .thenReturn(cacheQueue);
        when(cacheManager.getCacheMap(Mockito.anyString(),
                Mockito.eq(CrawlEntry.class))).thenReturn(cacheMap);

        // Create three entries with queuedAt times out of order
        var e1 = new CrawlEntry("r1");
        e1.setQueuedAt(ZonedDateTime.parse("2025-01-01T00:00:10Z"));
        e1.setProcessingStatus(ProcessingStatus.QUEUED);

        var e2 = new CrawlEntry("r2");
        e2.setQueuedAt(ZonedDateTime.parse("2025-01-01T00:00:05Z"));
        e2.setProcessingStatus(ProcessingStatus.QUEUED);

        var e3 = new CrawlEntry("r3");
        e3.setQueuedAt(null); // null should come last
        e3.setProcessingStatus(ProcessingStatus.QUEUED);

        var list = List.of(e1, e2, e3);

        // Mock iterator to return entries in the list order
        Iterator<CrawlEntry> it = list.iterator();
        when(cacheMap.queryIterator(Mockito.any())).thenReturn(it);

        // Make session appear resumed so init will attempt restore
        when(session.isResumed()).thenReturn(true);

        // Provide minimal crawl config behavior for max docs
        var cfg = Mockito.mock(com.norconex.crawler.core.CrawlConfig.class);
        when(crawlContext.getCrawlConfig()).thenReturn(cfg);
        when(cfg.getMaxDocuments()).thenReturn(-1L);

        var ledger = new CrawlEntryLedger();
        // inject cacheManager via session.getCluster().getCacheManager()
        ledger.init(session);

        // Call requeueQueuedEntries directly as well to test ordering
        var count = ledger.requeueQueuedEntries();
        assertThat(count).isEqualTo(3);

        // Verify the queue received add calls in the expected ordered
        // r2 (earliest), r1, r3 (null)
        var inOrder = Mockito.inOrder(cacheQueue);
        inOrder.verify(cacheQueue).add("r2");
        inOrder.verify(cacheQueue).add("r1");
        inOrder.verify(cacheQueue).add("r3");

        verify(cacheQueue, times(3)).add(Mockito.anyString());
    }
}
