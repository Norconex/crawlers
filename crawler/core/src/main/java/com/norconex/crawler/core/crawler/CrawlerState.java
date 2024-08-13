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
package com.norconex.crawler.core.crawler;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

/**
 * Represents a crawler current state. In a cluster, the state is
 * shared/synchronized appropriately between instances.
 */
public enum CrawlerState {
    /** Never started or crawl store was reset. Ready to be started. */
    UNDEFINED(false),
    /** Waiting for the chosen instance to finish performing a task. */
    IDLE(false),

    /** Waiting for the chosen instance to finish performing a task. */
//    IDLE_NOT_CHOSEN(false),
    /** Completed its task and waiting for next state assignment. */
//    IDLE_DONE_TASK(false),
    /**
     * Prepare the crawl stores related to document processing for the next
     * crawling start.
     */
    INIT_DOC_STORES(false),
    /**
     * Queues the initial start references. If start references are async,
     * then this state won't last for long before crawling starts.
     * In a cluster, performed by at most one crawler while others are waiting
     * for the next actionable state.
     */
    INIT_QUEUE(false),
    /**
     * Processing URLs from the queue.
     * In a cluster, performed by any number of crawlers.
     */
    CRAWLING(false),
    /** The instance or cluster state expired. */
    EXPIRED(true),
    /**
     * An event causing a stop or an explicit request for stopping the crawl
     * session triggers this state for each crawler in the session.
     * In a cluster, all crawlers will stop their execution cleanly
     * upon seeing this status.
     */
    STOPPING(true),
    /**
     * Indicates the crawler was stopped, as opposed to have completed normally.
     */
    STOPPED(true),
    /**
     * Indicates the crawler completed normally.
     */
    COMPLETED(true),
    ;

    @Getter
    private final boolean doneRunning;

    CrawlerState(boolean doneRunning) {
        this.doneRunning = doneRunning;
    }

    public static CrawlerState of(Optional<String> state) {
        return of(state.orElse(null));
    }
    public static CrawlerState of(String state) {
        if (StringUtils.isBlank(state)) {
            return UNDEFINED;
        }
        return Stream.of(CrawlerState.values())
        .filter(v -> v.name().equalsIgnoreCase(state))
        .findFirst()
        .orElse(UNDEFINED);
    }
}
