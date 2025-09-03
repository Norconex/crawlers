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
package com.norconex.crawler.core.cluster.pipeline;

import org.apache.commons.collections4.Bag;

import com.norconex.crawler.core.session.CrawlSession;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class BaseStep implements Step {

    private final String id;
    private boolean distributed;
    @Setter(value = AccessLevel.NONE)
    @Getter(value = AccessLevel.PROTECTED)
    private boolean stopRequested;

    @Override
    public void stop(CrawlSession session) {
        stopRequested = true;
    }

    @Override
    public PipelineStatus reduce(
            CrawlSession session, Bag<PipelineStatus> statuses) {
        // all terminal
        if (statuses.stream().allMatch(PipelineStatus::isTerminal)) {
            if (statuses.getCount(PipelineStatus.COMPLETED) > 0) {
                return PipelineStatus.COMPLETED;
            }
            if (statuses.getCount(PipelineStatus.STOPPED) > 0) {
                return PipelineStatus.STOPPED;
            }
            if (statuses.getCount(PipelineStatus.FAILED) > 0) {
                return PipelineStatus.FAILED;
            }
            return PipelineStatus.EXPIRED;
        }

        // all pending
        if (statuses.stream()
                .allMatch(status -> status == PipelineStatus.PENDING)) {
            return PipelineStatus.PENDING;
        }

        // all stopping or stopped
        if (statuses.stream()
                .allMatch(status -> status == PipelineStatus.STOPPING
                        || status == PipelineStatus.STOPPED)) {
            return PipelineStatus.STOPPING;
        }

        // We either have a mix of all running
        return PipelineStatus.RUNNING;
    }
}
