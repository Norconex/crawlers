package com.norconex.crawler.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.norconex.crawler.core._DELETE.CrawlTest;
import com.norconex.crawler.core._DELETE.CrawlTest.Focus;
import com.norconex.crawler.core._DELETE.MockSingleNodeConnector;
import com.norconex.crawler.core.session.CrawlSession;

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
}
