/* Copyright 2026 Norconex Inc.
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

import lombok.Builder;
import lombok.Value;

/**
 * Result summary of a pipeline execution.
 */
@Value
@Builder(toBuilder = true)
public class PipelineResult {
    String pipelineId;
    PipelineStatus status; // terminal status (or timeout status)
    String lastStepId; // last executed (or attempted) step id
    long startedAt; // coordinator start/resume time (or best-effort on worker-only nodes)
    long finishedAt; // time pipeline reached terminal/timeout condition
    boolean resumed; // true if coordinator detected pre-existing state
    boolean timedOut; // true if manager detected timeout condition
}
