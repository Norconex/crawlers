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
package com.norconex.crawler.core.session;

import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.RequiredArgsConstructor;

/**
 * Manages persistent crawl-state and session-scoped attributes stored in the
 * distributed session cache. Can be injected directly in tests without the
 * full {@link CrawlSession}.
 */
@RequiredArgsConstructor
public class CrawlStateStore {

    static final String SESSION_STATE_KEY = "session.state";
    static final String START_REFS_QUEUED_KEY =
            "session.startRefsQueuingComplete";

    private final CacheMap<String> sessionCache;
    private final CrawlAttributes sessionAttributes;
    /**
     * Ephemeral (non-MapStore-backed) attributes for per-run flags that must
     * survive partition migration after a node crash. Backed by the
     * {@code eph-crawlRun} cache so that in-memory backup promotion is used
     * directly without triggering a LAZY MapStore reload.
     */
    private final CrawlAttributes runAttributes;

    // Local copy refreshed on each explicit getCrawlState() call
    private CrawlSession.State cachedState;

    /**
     * Loads the current crawl state directly from the distributed cache.
     * @return the current state, or {@code null} if none has been saved yet
     */
    public CrawlSession.State loadState() {
        return SerialUtil.fromJson(
                sessionCache.getOrDefault(SESSION_STATE_KEY, null),
                CrawlSession.State.class);
    }

    /**
     * Updates the crawl state in the distributed cache with a new timestamp.
     * @param state the new crawl state
     */
    public void updateCrawlState(CrawlState state) {
        saveState(new CrawlSession.State()
                .setCrawlState(state)
                .setLastUpdated(System.currentTimeMillis()));
    }

    /**
     * Returns the current crawl state, reading fresh from the distributed
     * cache each call.
     * @return the current crawl state, or {@code null}
     */
    public CrawlState getCrawlState() {
        cachedState = loadState();
        return cachedState != null ? cachedState.getCrawlState() : null;
    }

    /**
     * Returns whether start-reference queuing has completed.
     * @return {@code true} when all start references have been queued
     */
    public boolean isStartRefsQueueingComplete() {
        return runAttributes.getBoolean(START_REFS_QUEUED_KEY);
    }

    /**
     * Marks whether start-reference queuing has completed.
     * @param isComplete {@code true} once all start references are queued
     */
    public void setStartRefsQueueingComplete(boolean isComplete) {
        runAttributes.setBoolean(START_REFS_QUEUED_KEY, isComplete);
    }

    /**
     * Returns the session-scoped attribute store backed by the session cache.
     * @return session attributes
     */
    public CrawlAttributes getSessionAttributes() {
        return sessionAttributes;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void saveState(CrawlSession.State state) {
        sessionCache.put(SESSION_STATE_KEY, SerialUtil.toJsonString(state));
    }
}
