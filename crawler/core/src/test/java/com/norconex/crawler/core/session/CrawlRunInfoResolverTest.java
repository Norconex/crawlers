/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.support.InMemoryCacheManager;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.util.SerialUtil;

/**
 * Unit tests for {@link CrawlerRunInfoResolver}.
 *
 * All tests use in-memory caches (no Hazelcast) and a Mockito-stubbed
 * {@link CrawlerSession}.  This verifies the resolver's decision logic
 * in isolation, covering every branch of the state-machine.
 */
@ExtendWith(MockitoExtension.class)
@WithTestWatcherLogging
@Timeout(30)
class CrawlerRunInfoResolverTest {

    @Mock(strictness = Strictness.LENIENT)
    CrawlerSession session;
    @Mock(strictness = Strictness.LENIENT)
    Cluster cluster;

    private InMemoryCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new InMemoryCacheManager();
        lenient().when(session.getCrawlerId()).thenReturn("crawler-1");
        lenient().when(session.getCluster()).thenReturn(cluster);
        lenient().when(cluster.getCacheManager()).thenReturn(cacheManager);
        // Default: no previous crawl state in the session cache.
        lenient().when(session.loadState()).thenReturn(null);
    }

    // -----------------------------------------------------------------
    // 1. Brand new crawler — nothing persisted yet
    // -----------------------------------------------------------------

    @Test
    void testFirstCrawl_createsNewFullSession() {
        var info = CrawlerRunInfoResolver.resolve(session);

        assertThat(info.getCrawlResumeState())
                .isEqualTo(CrawlerResumeState.NEW);
        assertThat(info.getCrawlMode())
                .isEqualTo(CrawlerMode.FULL);
        assertThat(info.getCrawlSessionId()).startsWith("cs-");
        assertThat(info.getCrawlRunId()).startsWith("cr-");
        assertThat(info.getCrawlerId()).isEqualTo("crawler-1");
    }

    @Test
    void testFirstCrawl_persistsInfoToSessionCache() {
        CrawlerRunInfoResolver.resolve(session);

        // The resolved info must be readable from the durable session cache
        // so that subsequent runs can detect whether to resume or go fresh.
        var stored = cacheManager.getCrawlSessionCache()
                .get(CrawlerRunInfoResolver.CRAWL_RUN_INFO_KEY);
        assertThat(stored).isPresent();
        var reloaded = SerialUtil.fromJson(stored.get(), CrawlerRunInfo.class);
        assertThat(reloaded.getCrawlResumeState())
                .isEqualTo(CrawlerResumeState.NEW);
    }

    // -----------------------------------------------------------------
    // 2. Previous session completed → start incremental
    // -----------------------------------------------------------------

    @Test
    void testCompletedSession_createsIncrementalNewSession() {
        primeSessionCache(CrawlerState.COMPLETED);

        var info = CrawlerRunInfoResolver.resolve(session);

        assertThat(info.getCrawlResumeState())
                .isEqualTo(CrawlerResumeState.NEW);
        assertThat(info.getCrawlMode())
                .isEqualTo(CrawlerMode.INCREMENTAL);
        // A new session means a new session ID (different from the prior one).
        assertThat(info.getCrawlSessionId()).isNotEqualTo("cs-prior");
    }

    // -----------------------------------------------------------------
    // 3. Previous session interrupted (STOPPED / RUNNING / FAILED) → resume
    // -----------------------------------------------------------------

    @ParameterizedTest(name = "Prior state {0} must trigger RESUMED")
    @EnumSource(
        value = CrawlerState.class, names = { "STOPPED", "RUNNING",
                "FAILED" }
    )
    void testInterruptedSession_resumesSameSession(CrawlerState state) {
        primeSessionCache(state);

        var info = CrawlerRunInfoResolver.resolve(session);

        assertThat(info.getCrawlResumeState())
                .isEqualTo(CrawlerResumeState.RESUMED);
        // Session identity is preserved across the resume.
        assertThat(info.getCrawlSessionId()).isEqualTo("cs-prior");
        // Crawl mode is preserved from the prior run (was FULL).
        assertThat(info.getCrawlMode()).isEqualTo(CrawlerMode.FULL);
    }

    // -----------------------------------------------------------------
    // 4. Prior session exists but state is null/corrupt → go fresh
    // -----------------------------------------------------------------

    @Test
    void testCorruptState_startsFreshSession() {
        // Prior run info in session cache but loadState() returns nothing.
        var prior = priorRunInfo();
        cacheManager.getCrawlSessionCache().put(
                CrawlerRunInfoResolver.CRAWL_RUN_INFO_KEY,
                SerialUtil.toJsonString(prior));
        when(session.loadState()).thenReturn(null);

        var info = CrawlerRunInfoResolver.resolve(session);

        assertThat(info.getCrawlResumeState())
                .isEqualTo(CrawlerResumeState.NEW);
        assertThat(info.getCrawlMode())
                .isEqualTo(CrawlerMode.FULL);
    }

    // -----------------------------------------------------------------
    // 5. Fast-path: second resolve() within the same run adopts first result
    // -----------------------------------------------------------------

    @Test
    void testConcurrentResolve_secondCallAdoptsPrimaryRunInfo() {
        // Simulates two nodes both calling resolve() on a shared cache:
        // the first call publishes to the ephemeral run cache; the second
        // call sees that entry and takes the fast-path without re-electing.
        var first = CrawlerRunInfoResolver.resolve(session);
        var second = CrawlerRunInfoResolver.resolve(session);

        // Both nodes must agree on the same runId and sessionId.
        assertThat(second.getCrawlRunId())
                .isEqualTo(first.getCrawlRunId());
        assertThat(second.getCrawlSessionId())
                .isEqualTo(first.getCrawlSessionId());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Stores a CrawlRunInfo for a prior run in the durable session cache,
     * and primes the session-state so the resolver detects {@code priorState}.
     */
    private void primeSessionCache(CrawlerState priorState) {
        var prior = priorRunInfo();
        cacheManager.getCrawlSessionCache().put(
                CrawlerRunInfoResolver.CRAWL_RUN_INFO_KEY,
                SerialUtil.toJsonString(prior));
        when(session.loadState()).thenReturn(
                new CrawlerSession.State()
                        .setCrawlState(priorState)
                        .setLastUpdated(System.currentTimeMillis()));
    }

    private static CrawlerRunInfo priorRunInfo() {
        return CrawlerRunInfo.builder()
                .crawlerId("crawler-1")
                .crawlSessionId("cs-prior")
                .crawlRunId("cr-prior")
                .crawlMode(CrawlerMode.FULL)
                .crawlResumeState(CrawlerResumeState.NEW)
                .build();
    }
}
