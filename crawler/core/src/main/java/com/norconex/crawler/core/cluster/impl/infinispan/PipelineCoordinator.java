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
package com.norconex.crawler.core.cluster.impl.infinispan;

import java.time.Duration;

import com.norconex.commons.lang.SleeperException;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinator implements AutoCloseable {

    private final InfinispanCluster cluster;
    private final Pipeline pipeline;
    private PipelineCoordinatorState state;
    private Step locallyExecutedStep;

    //TODO make sure it respects crawler-wide timeout if set

    public PipelineCoordinator(InfinispanCluster cluster, Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
    }

    void start() {
        Thread.currentThread().setName("COORDINATOR");

        if (cluster.isStandalone()) {
            LOG.info("Standalone mode - no cluster wait needed");
        } else {
            LOG.info("Clustered mode - waiting for cluster...");
            InfinispanUtil.waitForClusterWarmUp(cluster);
        }

        // Touch coordination caches to ensure they are created locally
        try {
            cluster.getCacheManager().getPipelineStepCache();
            cluster.getCacheManager().getPipelineWorkerStatusCache();
        } catch (Exception e) {
            // ignore and retry until deadline
        }

        state = new PipelineCoordinatorState(cluster, pipeline);
        try {
            doCoordinatePipelineExecution();
            LOG.info("Pipeline {}  terminated.", pipeline.getId());

            // Log active threads to diagnose any blocking issues
            logActiveThreads();

        } catch (RuntimeException e) {
            if (!ConcurrentUtil.isInterruption(e)) {
                throw e;
            }
            LOG.info("Coordinator interrupted/demoted while coordinating "
                    + "pipeline {}, exiting gracefully.",
                    pipeline.getId());
        }
        close();
    }

    private void logActiveThreads() {
        var liveCount =
                com.norconex.crawler.core.util.ThreadTracker
                        .getLiveThreadCount();
        var daemonCount =
                com.norconex.crawler.core.util.ThreadTracker
                        .getDaemonThreadCount();
        var nonDaemonCount = liveCount - daemonCount;

        LOG.info("COORDINATOR FINISHED - Active threads: total={}, "
                + "daemon={}, non-daemon={}",
                liveCount, daemonCount, nonDaemonCount);

        // Get non-daemon threads (these prevent JVM exit)
        var nonDaemonThreads =
                com.norconex.crawler.core.util.ThreadTracker.allThreadInfos(
                        t -> !t.isDaemon());

        if (!nonDaemonThreads.isEmpty()) {
            LOG.info("Non-daemon threads ({}): ", nonDaemonThreads.size());
            for (var thread : nonDaemonThreads) {
                LOG.info("  - Thread[{}]: name='{}', state={}",
                        thread.getThreadId(),
                        thread.getThreadName(),
                        thread.getThreadState());
            }
        }
    }

    void stop() {
        ExceptionSwallower.runWithInterruptClear(() -> {
            if (state.getStopRequested().compareAndSet(false, true)) {
                doStop();
            }
        });
    }

    @Override
    public void close() {
        ExceptionSwallower.runWithInterruptClear(() -> {
            if (state != null && state.getClosed().compareAndSet(false, true)) {
                ExceptionSwallower.close(state);
            }
        });
    }

    //--- Private methods ------------------------------------------------------

    private void doStop() {
        try {
            state.pushPipelineStatus(PipelineStatus.STOPPING);

            if (locallyExecutedStep != null) {
                locallyExecutedStep.stop(cluster.getCrawlSession());
            } else {
                try {
                    if (ConcurrentUtil.waitUntil(
                            () -> state.isStatusOfAllWorkers(
                                    PipelineStatus.STOPPED),
                            Duration.ofMinutes(1),
                            Duration.ofSeconds(1))) {
                        LOG.info("App pipeline workers stopped.");
                    } else {
                        LOG.warn("Not all pipeline workers stopped "
                                + "within a minute.");
                    }
                } catch (SleeperException e) {
                    if (!(e.getCause() instanceof InterruptedException)) {
                        throw e;
                    }
                    Thread.currentThread().interrupt();
                    LOG.debug("Stop interrupted while waiting for workers "
                            + "(normal during shutdown).");
                }
            }
            state.pushPipelineStatus(PipelineStatus.STOPPED);
        } finally {
            locallyExecutedStep = null;
        }
    }

    private void doCoordinatePipelineExecution() {

        final var currentStepRecord = state.getCurrentStepRecord();
        if (InfinispanUtil.isPipelineTerminated(
                pipeline, currentStepRecord, state.getStopRequested().get())) {
            LOG.debug("Coordinator detected terminal pipeline {} at step {} "
                    + "with status {}. Exiting.",
                    pipeline.getId(),
                    currentStepRecord.getStepId(),
                    currentStepRecord.getStatus());
            return;
        }

        var initialStep = state.getCurrentStep();
        if (initialStep != pipeline.getFirstStep()
                || currentStepRecord.getStatus().is(PipelineStatus.RUNNING)) {
            LOG.info("Resuming coordination of pipeline {} at step {} "
                    + "with status {} on node {}.",
                    pipeline.getId(),
                    currentStepRecord.stepId,
                    currentStepRecord.status,
                    cluster.getLocalNode().getNodeName());
        }

        var steps = pipeline
                .getSteps()
                .values()
                .stream()
                .dropWhile(step -> !step.getId().equals(initialStep.getId()))
                .toList();

        for (var step : steps) {
            if (state.getStopRequested().get()) {
                LOG.info("Stop requested before executing {}.", step.getId());
                return;
            }
            state.setRunningStep(step);

            var execStatus = step.isDistributed()
                    ? executeOnAllNodes(step, currentStepRecord)
                    : executeLocally(step);

            if (!InfinispanUtil.isClusterRunning(cluster)) {
                LOG.warn("Cluster reported as no longer running after step {}. "
                        + "This node {} will no longer execute pipeine {}.",
                        currentStepRecord.stepId,
                        cluster.getLocalNode().getNodeName(),
                        pipeline.getId());
                return;
            }

            state.pushPipelineStatus(execStatus);

            if (execStatus == PipelineStatus.FAILED) {
                LOG.info("Step {} reported as failed. Aborting pipeline "
                        + "execution...", currentStepRecord.stepId);
                return;
            }
        }
    }

    private PipelineStatus executeLocally(Step step) {
        locallyExecutedStep = step;
        LOG.info("Running step {} on a single node.", step.getId());
        try {
            step.execute(CrawlSession.get(cluster.getLocalNode()));
            LOG.info("Pipeline {} step {} completed.",
                    pipeline.getId(), step.getId());
            return PipelineStatus.COMPLETED;
        } catch (RuntimeException e) {
            LOG.error("Pipeline step {} failed.", step.getId(), e);
            return PipelineStatus.FAILED;
        } finally {
            locallyExecutedStep = null;
        }
    }

    private PipelineStatus executeOnAllNodes(Step step, StepRecord runningRec) {
        locallyExecutedStep = null;
        LOG.info("Coordinating the run of step {} on all nodes.", step.getId());

        return state.awaitTerminalWorkersReducedStatus();
    }

}
