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

import java.io.Serializable;

public enum PipelineStatus implements Serializable {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    STOPPING,
    STOPPED,
    /**
     * Typically set by the coordinator on when a worker fails to give signs
     * of life.
     */
    EXPIRED;

    public boolean isTerminal() {
        return this == STOPPED
                || this == COMPLETED
                || this == FAILED
                || this == EXPIRED;
    }

    public boolean is(PipelineStatus status) {
        if (status == null) {
            return false;
        }
        return this == status;
    }

    public boolean isNot(PipelineStatus status) {
        return !is(status);
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isStopping() {
        return this == STOPPING;
    }

    public boolean isStopped() {
        return this == STOPPED;
    }

    public boolean isComleted() {
        return this == COMPLETED;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}
