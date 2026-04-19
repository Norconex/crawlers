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
package com.norconex.crawler.core.cluster.pipeline;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

import com.norconex.crawler.core.session.CrawlSession;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Data
@Accessors(chain = true)
@Slf4j
public abstract class BaseStep implements Step {

    private final String id;
    private boolean distributed;
    @Setter(value = AccessLevel.NONE)
    @Getter
    private volatile boolean stopRequested;

    @Override
    public void stop(CrawlSession session) {
        stopRequested = true;
    }

    @Override
    public PipelineStatus reduce(
            CrawlSession session, Bag<PipelineStatus> statuses) {
        // Treat empty status bag as not-yet-started
        // all pending
        if (statuses.isEmpty() || statuses.stream()
                .allMatch(status -> status == PipelineStatus.PENDING)) {
            return PipelineStatus.PENDING;
        }

        // remove non-pending ones
        var nonPendings = new HashBag<PipelineStatus>();
        statuses.stream().forEach(status -> {
            if (!status.isPending()) {
                nonPendings.add(status, statuses.getCount(status));
            }
        });

        // all terminal
        if (nonPendings.stream().allMatch(PipelineStatus::isTerminal)) {
            if (nonPendings.getCount(PipelineStatus.COMPLETED) > 0) {
                return PipelineStatus.COMPLETED;
            }
            if (nonPendings.getCount(PipelineStatus.STOPPED) > 0) {
                return PipelineStatus.STOPPED;
            }
            if (nonPendings.getCount(PipelineStatus.FAILED) > 0) {
                return PipelineStatus.FAILED;
            }
            return PipelineStatus.EXPIRED;
        }

        // all stopping or stopped
        if (nonPendings.stream()
                .allMatch(status -> status == PipelineStatus.STOPPING
                        || status == PipelineStatus.STOPPED)) {
            return PipelineStatus.STOPPING;
        }

        //TODO maybe even if only one stopping, reduce to stopping?

        // We either have a mix of all running
        return PipelineStatus.RUNNING;
    }
}
