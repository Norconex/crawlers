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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * For workers to know when a pipeline has terminated.
 */
@Slf4j
@RequiredArgsConstructor
public class PipelineTerminationTracker {

    private final HazelcastCluster cluster;
    private final Pipeline pipeline;
    private final PipelineWorkerState state;

    private boolean done = false;
    private boolean timedOut = false;
    private boolean threadAborted = false;

    public PipelineResult await(long timeout) {
        while (!done && !Thread.currentThread().isInterrupted()) {
            if (!resolvePipeState(timeout)) {
                break;
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            threadAborted = true;
            Thread.currentThread().interrupt(); // preserve interrupt status
        }
        PipelineStatus status;
        String lastAttemptedStepId;
        status = state.getCurrentStepRecord().getStatus();
        lastAttemptedStepId = state.getCurrentStepRecord().getStepId();

        // If this worker aborted (e.g., node closed/demoted), always
        // report FAILED
        if (threadAborted) {
            LOG.warn("Worker aborted on node {}, marking as FAILED.",
                    cluster.getLocalNode().getNodeName());
            status = PipelineStatus.FAILED;
        } else if (timedOut && (status == null || !status.isTerminal())) {
            status = PipelineStatus.EXPIRED;
        }
        return PipelineResult.builder()
                .pipelineId(pipeline.getId())
                .status(status)
                .lastStepId(lastAttemptedStepId)
                .startedAt(state.getStartedAt())
                .finishedAt(System.currentTimeMillis())
                .resumed(false)
                .timedOut(timedOut)
                .build();
    }

    // returns true if could resolve, else if something's wrong
    // and suggests breaking right away.
    private boolean resolvePipeState(long timeout) {
        if (!HazelcastUtil.isClusterRunning(cluster)) {
            LOG.warn("Hazelcast node is closed: {}",
                    cluster.getLocalNode().getNodeName());
            done = true;
            threadAborted = true;
            return false;
        }
        var stepRec = state.getCurrentStepRecord();
        // Ignore terminal records older than this worker started
        if (isStaleTerminal(stepRec)) {
            LOG.debug("Ignoring stale terminal step record {} for {} during "
                    + "state resolution (updatedAt={}, startedAt={}).",
                    stepRec != null ? stepRec.getStepId() : null,
                    pipeline.getId(),
                    stepRec != null ? stepRec.getUpdatedAt() : -1,
                    state.getStartedAt());
        }
        if (isPipelineTerminated(pipeline)) {
            done = true;
        } else if (isExpired(timeout)) {
            done = true;
            timedOut = true;
        } else if (!sleep()) {
            done = true;
            threadAborted = true;
            return false;
        }
        return true;
    }

    private boolean isStaleTerminal(StepRecord rec) {
        return rec.getStatus().isTerminal()
                && rec.getUpdatedAt() > 0
                && rec.getUpdatedAt() < state.getStartedAt();
    }

    // it is terminated if the step is terminal and non COMPLETED, or
    // if the step is the last one and COMPLETED
    private boolean isPipelineTerminated(Pipeline pipeline) {
        return HazelcastUtil.isPipelineTerminated(
                pipeline,
                state.getCurrentStepRecord(),
                state.getStopRequested().get());
    }

    // Given the coordinator does not update the steps cache unless there
    // is a change, the expiry would be the crawler-wide max duration, if set.
    private boolean isExpired(long timeout) {
        return state.getCurrentStepRecord().hasTimedOut(timeout);
    }

    // returns false if we could not sleep (thread was aborted)
    private boolean sleep() {
        try {
            Sleeper.sleepMillis(100);
            return true;
        } catch (RuntimeException e) {
            // SleeperException wraps InterruptedException
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            throw e; // propagate unexpected runtime issues
        }
    }
}
