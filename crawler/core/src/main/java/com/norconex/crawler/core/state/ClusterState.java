/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.state;

import java.util.stream.Stream;

/**
 * Represents a crawler current state. In a cluster, the state is shared
 * and the same between crawlers.
 */
public enum ClusterState {
    /** Never started or crawl store was reset. Ready to be started. */
    UNDEFINED,
    /**
     * Queues the initial start references. If start references are async,
     * then this state won't last for long before crawling starts.
     * In a cluster, performed by at most one crawler while others are waiting
     * for the next actionable state.
     */
    QUEUE_INIT,
    /**
     * Processing URLs from the queue.
     * In a cluster, performed by any number of crawlers.
     */
    CRAWLING,
    /**
     * An event causing a stop or an explicit request for stopping the crawl
     * session triggers this state for each crawler in the session.
     * In a cluster, all crawlers will stop their execution cleanly
     * upon seeing this status.
     */
    STOPPING,
    /**
     * Indicates the crawler was stopped, as opposed to have completed normally.
     */
    STOPPED,
    /**
     * Indicates the crawler completed normally.
     */
    COMPLETED,
    ;

    public static ClusterState of(String state) {
        return Stream.of(ClusterState.values())
        .filter(v -> v.name().equalsIgnoreCase(state))
        .findFirst()
        .orElse(UNDEFINED);
    }
}
