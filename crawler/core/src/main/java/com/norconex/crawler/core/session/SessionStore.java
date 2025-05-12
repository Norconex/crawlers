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

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.storage.GridMap;

/**
 * Facade to a grid store for persisting or getting session state
 * values (e.g., flags) or other session-specific information, shared across
 * nodes (when on a grid).
 */
public class SessionStore {

    private static final String QUEUE_INITIALIZED = "queueInitialized";

    private final GridMap<String> sessionAttribs;

    public SessionStore(Grid grid) {
        sessionAttribs = grid.getStorage().getSessionAttributes();
    }

    public void setQueueInitialized(boolean initialized) {
        sessionAttribs.put(QUEUE_INITIALIZED, Boolean.toString(initialized));
    }

    public boolean isQueueInitialized() {
        return Boolean.parseBoolean(sessionAttribs.get(QUEUE_INITIALIZED));
    }
}
