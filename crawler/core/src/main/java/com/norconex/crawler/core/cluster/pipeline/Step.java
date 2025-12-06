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

import lombok.Data;

public interface Step {
    String getId();

    boolean isDistributed();

    void execute(CrawlSession session);

    /**
     * Requests for the step to stop. The method may return before the
     * step is actually stopped (up to implementation).
     * @param session crawl session
     */
    void stop(CrawlSession session);

    PipelineStatus reduce(CrawlSession session, Bag<PipelineStatus> statuses);

    /**
     * Indicates whether this step has been asked to stop.
     * <p>
     * Default implementation returns {@code false} so non {@link BaseStep}
     * implementations are not forced to expose internal state.
     * </p>
     *
     * @return {@code true} if a stop was requested
     */
    default boolean isStopRequested() {
        return false;
    }

    /**
     * Optional method returning the current progress of this step. Returns
     * {@code null} if this task does not track its progress.
     * @return progress or {@code null}
     */
    default StepProgress getProgress() {
        return null;
    }

    @Data
    public static class StepProgress {
        private final float progress;
        private final String message;
    }
}
