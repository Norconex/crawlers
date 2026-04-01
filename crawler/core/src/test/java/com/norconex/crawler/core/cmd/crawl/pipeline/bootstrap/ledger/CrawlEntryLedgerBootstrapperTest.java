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
package com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.session.CrawlAttributes;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link CrawlEntryLedgerBootstrapper}.
 */
@Timeout(30)
class CrawlEntryLedgerBootstrapperTest {

    /**
     * Builds a mocked CrawlSession for testing.
     *
     * @param resumed        whether the session is resumed
     * @param queueCount     value returned by ledger.getQueueCount()
     * @param allowBootstrap if true, setBooleanIfAbsent returns true (proceed);
     *                       if false, returns false (skip)
     */
    private CrawlSession buildSession(
            boolean resumed, long queueCount, boolean allowBootstrap) {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getQueueCount()).thenReturn(queueCount);
        when(ledger.getProcessedCount()).thenReturn(0L);
        when(ledger.getBaselineCount()).thenReturn(0L);
        when(ledger.requeueProcessingEntries()).thenReturn(0);
        when(ledger.countByStatus(
                com.norconex.crawler.core.ledger.ProcessingStatus.QUEUED))
                        .thenReturn(0L);

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);

        var attrs = mock(CrawlAttributes.class);
        when(attrs.setBooleanIfAbsent(
                CrawlEntryLedgerBootstrapper.BOOTSTRAP_KEY, true))
                        .thenReturn(allowBootstrap);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.isResumed()).thenReturn(resumed);
        when(session.getCrawlRunAttributes()).thenReturn(attrs);
        return session;
    }

    @Test
    void bootstrap_whenAlreadyBootstrapped_skipsLedgerOperations() {
        // setBooleanIfAbsent returns false → another node already bootstrapped
        var session = buildSession(false, 0, false);
        var ledger = session.getCrawlContext().getCrawlEntryLedger();

        new CrawlEntryLedgerBootstrapper().bootstrap(session);

        // Bootstrap key check was the early exit; ledger should not be touched
        verify(ledger, never()).ensureCurrentLedgerAliasExists();
        verify(ledger, never()).clearQueuedEntriesInLedger();
        verify(ledger, never()).archiveCurrentLedger();
    }

    @Test
    void bootstrap_freshCrawl_emptyQueue_archivesLedger() {
        // Not resumed, queue is empty -> clear queued ledger entries and
        // archive.
        var session = buildSession(false, 0, true);
        var ledger = session.getCrawlContext().getCrawlEntryLedger();

        new CrawlEntryLedgerBootstrapper().bootstrap(session);

        verify(ledger).ensureCurrentLedgerAliasExists();
        verify(ledger).clearQueuedEntriesInLedger();
        verify(ledger).archiveCurrentLedger();
    }

    @Test
    void bootstrap_resumed_requeuesProcessingEntries() {
        // Resumed with items in the queue → requeues processing entries
        var session = buildSession(true, 3, true);
        var ledger = session.getCrawlContext().getCrawlEntryLedger();

        new CrawlEntryLedgerBootstrapper().bootstrap(session);

        verify(ledger).ensureCurrentLedgerAliasExists();
        verify(ledger).requeueProcessingEntries();
        // Should NOT archive or clear in resume path
        verify(ledger, never()).clearQueuedEntriesInLedger();
        verify(ledger, never()).archiveCurrentLedger();
    }

    @Test
    void bootstrap_notResumed_nonEmptyQueue_preservesQueue() {
        // Not resumed but queue has items (resume detection issue)
        // -> preserves queue (no clear/archive in this path)
        var session = buildSession(false, 7, true);
        var ledger = session.getCrawlContext().getCrawlEntryLedger();

        new CrawlEntryLedgerBootstrapper().bootstrap(session);

        verify(ledger).ensureCurrentLedgerAliasExists();
        verify(ledger, never()).clearQueuedEntriesInLedger();
        verify(ledger, never()).archiveCurrentLedger();
    }

    @Test
    void bootstrap_alwaysSetsBootstrapKeyToFalseInFinally() {
        // When proceeding, the finally block must reset the bootstrap key
        var session = buildSession(false, 0, true);
        var attrs = session.getCrawlRunAttributes();

        new CrawlEntryLedgerBootstrapper().bootstrap(session);

        verify(attrs).setBoolean(CrawlEntryLedgerBootstrapper.BOOTSTRAP_KEY,
                false);
    }
}
