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
package com.norconex.crawler.core.session;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

/**
 * Represents a crawl session current state. In a cluster, the state is
 * shared/synchronized appropriately between instances.
 */
public enum CrawlSessionState {
    /** Never started or crawl store was reset. Ready to be started. */
    UNDEFINED(false),
//    /** Waiting for the next actionable state. */
//    IDLE(false),
//    /** The instance or cluster state expired. */
//    EXPIRED,
    /**
     * The crawl session started and has not yet completed nor stopped
     * (or stopping)
     */
    RUNNING(false),
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

    CrawlSessionState(boolean doneRunning) {
        this.doneRunning = doneRunning;
    }

    public static CrawlSessionState of(Optional<String> state) {
        return of(state.orElse(null));
    }
    public static CrawlSessionState of(String state) {
        if (StringUtils.isBlank(state)) {
            return UNDEFINED;
        }
        return Stream.of(CrawlSessionState.values())
        .filter(v -> v.name().equalsIgnoreCase(state))
        .findFirst()
        .orElse(UNDEFINED);
    }
}
