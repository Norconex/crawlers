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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.cluster.support.InMemoryCacheMap;

@Timeout(30)
class CrawlerStateStoreTest {

    private InMemoryCacheMap<String> sessionCache;
    private CrawlerAttributes sessionAttrs;
    private InMemoryCacheMap<String> runCache;
    private CrawlerAttributes runAttrs;
    private CrawlerStateStore store;

    @BeforeEach
    void setUp() {
        sessionCache = new InMemoryCacheMap<>("session-cache");
        sessionAttrs = new CrawlerAttributes(sessionCache);
        runCache = new InMemoryCacheMap<>("run-cache");
        runAttrs = new CrawlerAttributes(runCache);
        store = new CrawlerStateStore(sessionCache, sessionAttrs, runAttrs);
    }

    @Test
    void loadState_emptyCache_returnsNull() {
        assertThat(store.loadState()).isNull();
    }

    @Test
    void updateAndGetCrawlState_roundTrip() {
        store.updateCrawlState(CrawlerState.RUNNING);
        assertThat(store.getCrawlState()).isEqualTo(CrawlerState.RUNNING);
    }

    @Test
    void loadState_afterUpdate_returnsState() {
        store.updateCrawlState(CrawlerState.COMPLETED);
        var state = store.loadState();
        assertThat(state).isNotNull();
        assertThat(state.getCrawlState()).isEqualTo(CrawlerState.COMPLETED);
    }

    @Test
    void getCrawlState_emptyCache_returnsNull() {
        assertThat(store.getCrawlState()).isNull();
    }

    @Test
    void isStartRefsQueueingComplete_default_returnsFalse() {
        assertThat(store.isStartRefsQueueingComplete()).isFalse();
    }

    @Test
    void setAndGetStartRefsQueueingComplete_true() {
        store.setStartRefsQueueingComplete(true);
        assertThat(store.isStartRefsQueueingComplete()).isTrue();
    }

    @Test
    void setAndGetStartRefsQueueingComplete_false() {
        store.setStartRefsQueueingComplete(true);
        store.setStartRefsQueueingComplete(false);
        assertThat(store.isStartRefsQueueingComplete()).isFalse();
    }

    @Test
    void getSessionAttributes_returnsSameInstance() {
        assertThat(store.getSessionAttributes()).isSameAs(sessionAttrs);
    }

    @Test
    void updateState_setsLastUpdated() {
        store.updateCrawlState(CrawlerState.RUNNING);
        var loaded = store.loadState();
        assertThat(loaded.getLastUpdated()).isGreaterThan(0L);
    }
}
