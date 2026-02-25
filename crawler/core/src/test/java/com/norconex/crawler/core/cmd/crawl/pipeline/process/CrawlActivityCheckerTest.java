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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link CrawlActivityChecker}.
 */
class CrawlActivityCheckerTest {

    private CrawlSession buildSession(boolean queueEmpty,
            boolean maxDocsReached) {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.isQueueEmpty()).thenReturn(queueEmpty);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(maxDocsReached);

        var config = mock(CrawlConfig.class);
        when(config.getMaxCrawlDuration()).thenReturn(null);
        when(config.getIdleTimeout()).thenReturn(null);

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getCrawlConfig()).thenReturn(config);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.isStartRefsQueueingComplete()).thenReturn(true);
        return session;
    }

    @Test
    void constructor_withNullMaxDuration_doesNotThrow() {
        // null maxDuration → expireAt=0 (no expiry)
        var session = buildSession(false, false);
        var checker = new CrawlActivityChecker(session, false);
        assertThat(checker).isNotNull();
    }

    @Test
    void isDeleting_reflectsConstructorFlag() {
        var session = buildSession(false, false);
        assertThat(new CrawlActivityChecker(session, true).isDeleting())
                .isTrue();
        assertThat(new CrawlActivityChecker(session, false).isDeleting())
                .isFalse();
    }

    @Test
    void isActive_whenQueueNotEmpty_returnsTrue() {
        var session = buildSession(false, false);
        var checker = new CrawlActivityChecker(session, false);
        assertThat(checker.isActive()).isTrue();
    }

    @Test
    void isActive_whenQueueEmptyAndInitialized_returnsFalse() {
        // queue empty + queuing complete + no idleTimeout → not active
        var session = buildSession(true, false);
        var checker = new CrawlActivityChecker(session, false);
        assertThat(checker.isActive()).isFalse();
    }

    @Test
    void canContinue_whenDeleting_returnsTrueEvenIfMaxDocsReached() {
        // deleting=true bypasses max-docs check
        var session = buildSession(false, true);
        var checker = new CrawlActivityChecker(session, true);
        assertThat(checker.canContinue()).isTrue();
    }

    @Test
    void canContinue_whenMaxDocsReached_returnsFalse() {
        var session = buildSession(false, true);
        var checker = new CrawlActivityChecker(session, false);
        // session.updateCrawlState is a no-op on mock
        assertThat(checker.canContinue()).isFalse();
    }

    @Test
    void doCanContinue_whenNothingReached_returnsTrue() {
        var session = buildSession(false, false);
        var checker = new CrawlActivityChecker(session, false);
        assertThat(checker.doCanContinue()).isTrue();
    }

    @Test
    void canContinue_whenMaxDurationExpired_returnsFalse()
            throws InterruptedException {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.isQueueEmpty()).thenReturn(false);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(false);

        var config = mock(CrawlConfig.class);
        when(config.getMaxCrawlDuration()).thenReturn(Duration.ofMillis(1));
        when(config.getIdleTimeout()).thenReturn(null);

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getCrawlConfig()).thenReturn(config);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.isStartRefsQueueingComplete()).thenReturn(true);

        var checker = new CrawlActivityChecker(session, false);
        Thread.sleep(20); // ensure the 1ms duration has expired
        assertThat(checker.doCanContinue()).isFalse();
    }

    @Test
    void isActive_afterBecomingInactive_returnsFalseFromCache() {
        // queue empty + fully initialized → isActive() sets isPresumedActive=false
        var session = buildSession(true, false);
        var checker = new CrawlActivityChecker(session, false);
        // First call: resolves and sets isPresumedActive = false
        assertThat(checker.isActive()).isFalse();
        // Second call: early-return from cached isPresumedActive == false
        assertThat(checker.isActive()).isFalse();
    }

    @Test
    void canContinue_afterBecomingFalse_cachedFalseReturnedFast() {
        // maxDocsReached=true → canContinue() returns false and caches result
        var session = buildSession(false, true);
        var checker = new CrawlActivityChecker(session, false);
        assertThat(checker.canContinue()).isFalse(); // sets canContinue=false
        // Second call takes the fast-path: !canContinue.get() → return false
        assertThat(checker.canContinue()).isFalse();
    }

    @Test
    void isActive_withMaxDocsReached_triggersDoIsActiveCanContinueBranch() {
        // queue NOT empty (queueEmpty=false) but maxDocsReached=true
        // → isActive() → doIsActive() → !canContinue() path
        var session = buildSession(false, true);
        var checker = new CrawlActivityChecker(session, false);
        assertThat(checker.isActive()).isFalse();
    }

    @Test
    void isActive_withShortIdleTimeout_queueStillEmpty_returnsFalse() {
        // queue initialized + empty + idleTimeout=50ms → wait loop runs ≈1s
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.isQueueEmpty()).thenReturn(true);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(false);

        var config = mock(CrawlConfig.class);
        when(config.getMaxCrawlDuration()).thenReturn(null);
        when(config.getIdleTimeout()).thenReturn(Duration.ofMillis(50));

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getCrawlConfig()).thenReturn(config);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.isStartRefsQueueingComplete()).thenReturn(true);

        var checker = new CrawlActivityChecker(session, false);
        // Queue is empty + 50ms timeout elapses before poll loop finishes →
        // considered still empty → doIsActive returns false
        assertThat(checker.isActive()).isFalse();
    }

    @Test
    void isActive_withQueueEmptyAndNotYetInitialized_waitsForInit()
            throws InterruptedException {
        // Session: queue empty + first call to isStartRefsQueueingComplete
        // returns false, second returns true → tests the do-while wait loop
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.isQueueEmpty()).thenReturn(true);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(false);

        var config = mock(CrawlConfig.class);
        when(config.getMaxCrawlDuration()).thenReturn(null);
        when(config.getIdleTimeout()).thenReturn(null); // no idle timeout

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getCrawlConfig()).thenReturn(config);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        // First call: not ready; subsequent calls: ready
        when(session.isStartRefsQueueingComplete())
                .thenReturn(false)
                .thenReturn(true);

        var checker = new CrawlActivityChecker(session, false);
        // do-while loop fires once (sleeps 1s), then sees initialized=true
        // → queue is empty+initialized → idle check returns true → inactive
        assertThat(checker.isActive()).isFalse();
    }
}
