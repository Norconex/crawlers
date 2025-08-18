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
    PipelineStatus status;            // terminal status (or timeout status)
    String lastStepId;                // last executed (or attempted) step id
    long startedAt;                   // coordinator start/resume time (or best-effort on worker-only nodes)
    long finishedAt;                  // time pipeline reached terminal/timeout condition
    boolean resumed;                  // true if coordinator detected pre-existing state
    boolean timedOut;                 // true if manager detected timeout condition
}
