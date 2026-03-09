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
package com.norconex.crawler.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link CrawlerMetricsImpl}.
 */
@Timeout(30)
class CrawlerMetricsImplTest {

    // ------------------------------------------------------------------
    // Helper to build a fully initialized CrawlerMetricsImpl
    // Uses a mocked session, cluster, cacheManager, ledger,
    // eventManager.
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private CrawlerMetricsImpl buildInitialized(
            CrawlEntryLedger ledger,
            CacheMap<Long> eventCountsStore) {
        var eventManager = mock(EventManager.class);
        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getEventManager()).thenReturn(eventManager);

        var cacheManager = mock(CacheManager.class);
        doReturn(eventCountsStore).when(cacheManager)
                .getCacheMap(anyString(), any());

        var cluster = mock(Cluster.class);
        when(cluster.getCacheManager()).thenReturn(cacheManager);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.getCluster()).thenReturn(cluster);

        var metrics = new CrawlerMetricsImpl();
        metrics.init(session);
        return metrics;
    }

    // ------------------------------------------------------------------
    // Initial state (before init)
    // ------------------------------------------------------------------

    @Test
    void constructor_isNotClosed() {
        assertThat(new CrawlerMetricsImpl().isClosed()).isFalse();
    }

    @Test
    void flush_noOp_doesNotThrow() {
        var metrics = new CrawlerMetricsImpl();
        // flush() is documented as a no-op; must not throw
        metrics.flush();
    }

    // ------------------------------------------------------------------
    // incrementCounter (before init — eventCountsStore is null)
    // ------------------------------------------------------------------

    @Test
    void incrementCounter_zeroIncrement_doesNothing() {
        var metrics = new CrawlerMetricsImpl();
        // Incrementing by 0 must be a no-op: no NPE, no entry
        metrics.incrementCounter("evt", 0L);
        assertThat(metrics.getEventCounts()).doesNotContainKey("evt");
    }

    @Test
    void incrementCounter_beforeInit_accumulatesInMemory() {
        var metrics = new CrawlerMetricsImpl();
        // eventCountsStore is null → NPE is swallowed, memCache is updated
        metrics.incrementCounter("SOME_EVENT", 3L);
        // getEventCounts() will also get an NPE from the null store, swallow it
        assertThat(metrics.getEventCounts()).containsEntry("SOME_EVENT", 3L);
    }

    // ------------------------------------------------------------------
    // close (before init — ledger and eventCountsStore are both null)
    // ------------------------------------------------------------------

    @Test
    void close_beforeInit_setsClosedAndDoesNotThrow() {
        var metrics = new CrawlerMetricsImpl();
        metrics.close();
        assertThat(metrics.isClosed()).isTrue();
    }

    @Test
    void close_twice_isIdempotent() {
        var metrics = new CrawlerMetricsImpl();
        metrics.close();
        metrics.close(); // second close should be a no-op
        assertThat(metrics.isClosed()).isTrue();
    }

    // ------------------------------------------------------------------
    // getEventCounts after close → returns last known memCache
    // ------------------------------------------------------------------

    @Test
    void getEventCounts_afterClose_returnsLastKnownCounts() {
        var metrics = new CrawlerMetricsImpl();
        metrics.incrementCounter("MY_EVENT", 5L);
        metrics.close();
        // After close, no sync from cluster occurs; memCache should be intact
        assertThat(metrics.getEventCounts()).containsEntry("MY_EVENT", 5L);
    }

    // ------------------------------------------------------------------
    // Tests with init()
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void getEventCounts_withInitAndStore_returnsMergedCounts() {
        var ledger = mock(CrawlEntryLedger.class);
        CacheMap<Long> store = mock(CacheMap.class);
        // Store contains one pre-existing count
        var metrics = buildInitialized(ledger, store);

        metrics.incrementCounter("EVT_A", 2L);

        // getEventCounts() calls store.forEach(...) — mocked → no-op
        var counts = metrics.getEventCounts();
        assertThat(counts).containsEntry("EVT_A", 2L);
    }

    @Test
    void getProcessingCount_delegatesToLedger() {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getProcessingCount()).thenReturn(7L);

        @SuppressWarnings("unchecked")
        var metrics = buildInitialized(ledger, mock(CacheMap.class));

        assertThat(metrics.getProcessingCount()).isEqualTo(7L);
    }

    @Test
    void getProcessedCount_delegatesToLedger() {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getProcessedCount()).thenReturn(42L);

        @SuppressWarnings("unchecked")
        var metrics = buildInitialized(ledger, mock(CacheMap.class));

        assertThat(metrics.getProcessedCount()).isEqualTo(42L);
    }

    @Test
    void getQueuedCount_delegatesToLedger() {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getQueueCount()).thenReturn(12L);

        @SuppressWarnings("unchecked")
        var metrics = buildInitialized(ledger, mock(CacheMap.class));

        assertThat(metrics.getQueuedCount()).isEqualTo(12L);
    }

    @Test
    void getBaselineCount_delegatesToLedger() {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getBaselineCount()).thenReturn(100L);

        @SuppressWarnings("unchecked")
        var metrics = buildInitialized(ledger, mock(CacheMap.class));

        assertThat(metrics.getBaselineCount()).isEqualTo(100L);
    }

    // ------------------------------------------------------------------
    // atomicIncrement via incrementCounter with a real CacheMap mock
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void incrementCounter_withStore_updatesViaAtomicIncrement() {
        var ledger = mock(CrawlEntryLedger.class);
        CacheMap<Long> store = mock(CacheMap.class);
        // Simulate empty store (key absent) → putIfAbsent succeeds (null return)
        when(store.get(anyString())).thenReturn(Optional.empty());
        when(store.putIfAbsent(anyString(), any())).thenReturn(null);

        var metrics = buildInitialized(ledger, store);
        metrics.incrementCounter("DOC_PROCESSED", 1L);

        assertThat(metrics.getEventCounts()).containsEntry("DOC_PROCESSED", 1L);
    }

    // ------------------------------------------------------------------
    // close with initialized store and ledger
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void close_withInitedStore_syncsAndCloses() {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getQueueCount()).thenReturn(3L);
        when(ledger.getProcessingCount()).thenReturn(1L);
        when(ledger.getProcessedCount()).thenReturn(99L);
        when(ledger.getBaselineCount()).thenReturn(50L);

        CacheMap<Long> store = mock(CacheMap.class);
        var metrics = buildInitialized(ledger, store);

        metrics.close();

        assertThat(metrics.isClosed()).isTrue();
        // After close, counts are frozen in memCache
        assertThat(metrics.getQueuedCount()).isEqualTo(3L);
        assertThat(metrics.getProcessingCount()).isEqualTo(1L);
        assertThat(metrics.getProcessedCount()).isEqualTo(99L);
        assertThat(metrics.getBaselineCount()).isEqualTo(50L);
    }
}
