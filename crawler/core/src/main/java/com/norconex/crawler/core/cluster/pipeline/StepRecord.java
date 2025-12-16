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

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A record holding information on a pipeline execution current step.
 */
@Data
@Accessors(chain = true)
public class StepRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pipelineId;
    private String stepId;
    private long updatedAt;
    private PipelineStatus status;
    private String runId;

    public boolean hasTimedOut(long timeoutMs) {
        if (timeoutMs == 0) {
            return false;
        }

        // Add a small grace to avoid false expirations (e.g., during brief
        // coordinator switches)
        var graceMs = 1000L;
        return (System.currentTimeMillis() - updatedAt) > (timeoutMs + graceMs);
    }
}
