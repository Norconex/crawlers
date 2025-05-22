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

import static java.util.Optional.ofNullable;

import java.util.Optional;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.storage.GridMap;

/**
 * Facade to a grid store for persisting or getting session state
 * values (e.g., flags) or other session-specific information, shared across
 * nodes (when on a grid).
 */
public class CrawlSessionProperties {

    private static final String QUEUE_INITIALIZED = "queueInitialized";

    private final Grid grid;
    private final GridMap<String> sessionProps;
    private final String id;

    public CrawlSessionProperties(Grid grid, String crawlerId) {
        this.grid = grid;
        id = crawlerId;
        sessionProps = grid.getStorage().getSessionAttributes();
    }

    public void setQueueInitialized(boolean initialized) {
        sessionProps.put(QUEUE_INITIALIZED, Boolean.toString(initialized));
    }

    public boolean isQueueInitialized() {
        return Boolean.parseBoolean(sessionProps.get(QUEUE_INITIALIZED));
    }

    public Optional<CrawlState> getCrawlState() {
        var store = CrawlSessionManager.sessionStore(grid);
        return ofNullable(store.get(id))
                .map(CrawlSession::getCrawlState);
    }

    public void updateCrawlState(CrawlState state) {
        var store = CrawlSessionManager.sessionStore(grid);
        var session = store.get(id);
        if (session == null) {
            throw new IllegalStateException("Cannot update crawl state "
                    + "for crawler %s: session does not exist.".formatted(
                            id));
        }
        session.setCrawlState(state);
        store.put(id, session);
    }
}
