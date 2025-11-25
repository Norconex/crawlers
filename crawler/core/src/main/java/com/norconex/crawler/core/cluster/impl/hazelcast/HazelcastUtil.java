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

import com.google.common.base.Objects;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HazelcastUtil {
    private HazelcastUtil() {
    }

    /**
     * Whether a pipeline should be considered "terminated", either by
     * completing all steps (success), or having a non-COMPLETED terminal
     * status on any of the steps (aborting the pipeline), or, having
     * a completed step when stopping was requested.
     * A {@code null} step record suggests no steps have yet run so
     * we consider it non-terminated.
     * @param pipeline the pipeline
     * @param stepRecord the record of the last step ran/attempted.
     * @param stopRequested whether stop was requested
     * @return true if terminated
     */
    public static boolean isPipelineTerminated(
            Pipeline pipeline,
            StepRecord stepRecord,
            boolean stopRequested) {

        if (stepRecord == null || stepRecord.getStatus() == null) {
            return false;
        }

        var terminal = stepRecord.getStatus().isTerminal();

        // stop was requested and the current step has completed
        if (terminal && stopRequested) {
            return true;
        }

        // current step is the same as last step
        var isLastStep = Objects.equal(stepRecord.getStepId(),
                pipeline.getLastStep().getId());

        // all steps have terminated
        if (terminal && isLastStep) {
            return true;
        }

        // the step is a terminal, but non-COMPLETED, which means
        // the pipeline fail and we consider terminated even if not last
        var isCompleted = stepRecord.getStatus() == PipelineStatus.COMPLETED;
        return terminal && !isCompleted;
    }

    // either the first pipeline step or an existing one (joining mid-pipe).
    public static StepRecord currentPipelineStepRecordOrFirst(
            HazelcastCluster cluster, Pipeline pipeline) {
        var pipelineKey = CacheKeys.pipelineKey(cluster, pipeline);
        var stepRec = cluster.getCacheManager().getPipelineStepCache()
                .get(pipelineKey)
                .orElse(null);
        if (stepRec == null) {
            var stepId = pipeline.getFirstStep().getId();
            stepRec = new StepRecord();
            stepRec.setPipelineId(pipeline.getId());
            stepRec.setStepId(stepId);
            stepRec.setStatus(PipelineStatus.PENDING);
            stepRec.setRunId(cluster.getCrawlSession().getCrawlRunId());
            stepRec.setUpdatedAt(System.currentTimeMillis());
        } else {
            LOG.info("Resuming pipeline \"{}\" at step \"{}\" from status {}"
                    .formatted(
                            stepRec.getPipelineId(),
                            stepRec.getStepId(),
                            stepRec.getStatus()));
        }
        return stepRec;
    }

    public static boolean isClusterRunning(HazelcastCluster cluster) {
        return cluster.getCacheManager().vendor()
                .getLifecycleService().isRunning();
    }

    public static void waitForClusterWarmUp(HazelcastCluster cluster) {
        // Allow up to ~5 seconds for cluster to stabilize
        var deadline = System.currentTimeMillis() + 5_000;
        var lastSize = cluster.getNodeCount();
        var stableTicks = 0;
        var coordinatorElected = false;

        while (System.currentTimeMillis() < deadline) {
            if (!HazelcastUtil.isClusterRunning(cluster)) {
                Sleeper.sleepMillis(50);
                continue;
            }
            var currentSize = cluster.getNodeCount();
            if (currentSize == lastSize) {
                stableTicks++;
            } else {
                stableTicks = 0;
            }
            lastSize = currentSize;

            // Also verify coordinator election has completed
            if (!coordinatorElected && hasStableCoordinator(cluster)) {
                coordinatorElected = true;
                LOG.debug("Coordinator election completed");
            }

            if (stableTicks >= 2 && coordinatorElected) {
                return;
            }
            Sleeper.sleepMillis(100);
        }
        LOG.warn("Cluster warm-up timed out (stable={}, coordinator={}); "
                + "proceeding anyway.",
                stableTicks >= 2, coordinatorElected);
    }

    /**
     * Check if the cluster has a stable coordinator elected.
     * @param cluster the cluster to check
     * @return true if a coordinator is elected and consistent
     */
    private static boolean hasStableCoordinator(HazelcastCluster cluster) {
        try {
            var hazelcast = cluster.getCacheManager().vendor();
            if (hazelcast == null
                    || !hazelcast.getLifecycleService().isRunning()) {
                return false;
            }
            var members = hazelcast.getCluster().getMembers();
            // There should be at least one member (the oldest is coordinator)
            return !members.isEmpty();
        } catch (Exception e) {
            LOG.debug("Error checking coordinator status: {}", e.toString());
            return false;
        }
    }
}
