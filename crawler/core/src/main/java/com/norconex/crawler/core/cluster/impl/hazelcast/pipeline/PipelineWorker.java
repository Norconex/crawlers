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
package com.norconex.crawler.core.cluster.impl.hazelcast.pipeline;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastCluster;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastUtil;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineWorker implements AutoCloseable {
    private final HazelcastCluster cluster;
    private final Pipeline pipeline;
    private PipelineWorkerState state;

    // Execute user steps off the Hazelcast event thread to avoid blocking RPC
    private final ExecutorService stepExecutor =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final ThreadFactory delegate =
                        Executors.defaultThreadFactory();

                @Override
                public Thread newThread(Runnable r) {
                    var t = delegate.newThread(r);
                    t.setName("PIPE-STEP-"
                            + cluster.getLocalNode().getNodeName());
                    t.setDaemon(true);
                    return t;
                }
            });

    public PipelineWorker(
            @NonNull HazelcastCluster cluster,
            @NonNull Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
    }

    PipelineResult start() {
        Thread.currentThread().setName("WORKER");
        // Warm-up: wait briefly for stable cluster view and caches
        if (!cluster.isStandalone()) {
            LOG.info("PipelineWorker - waiting for cluster warm-up");
            HazelcastUtil.waitForClusterWarmUp(cluster);
        }
        LOG.info("PipelineWorker - cluster ready");

        state = new PipelineWorkerState(cluster, pipeline,
                this::executeStep);
        LOG.info("PipelineWorker - state initialized");
        // Register listener only after state is non-null to avoid race
        state.registerStepListener();
        LOG.info("PipelineWorker - step listener registered");

        return new PipelineTerminationTracker(
                cluster, pipeline, state).await(0L);
    }

    /**
     * End this worker by stopping any active task first then closing.
     */
    public void stop() {
        LOG.debug("PipelineWorker stop() called. State is: {}", state);
        if (state != null
                && state.getStopRequested().compareAndSet(false, true)) {
            state.pushWorkerStatus(PipelineStatus.STOPPING);
            state.getCurrentStep().stop(cluster.getCrawlSession());
            state.pushWorkerStatus(PipelineStatus.STOPPED);
            // we don't close here. Closed by caller.
        }
    }

    @Override
    public void close() {
        LOG.info("PipelineWorker close() called. State is: {}", state);
        if (state != null) {
            state.close();
        }

        // shut down step executor
        ConcurrentUtil.shutdownAndAwait(stepExecutor, Duration.ofSeconds(2));
    }

    PipelineWorkerState getState() {
        return state;
    }

    // NOTE: only invoked when the current/new step is set to RUNNING
    private void executeStep(StepRecord stepRec) {
        if (state == null) {
            LOG.warn("Worker state not yet initialized for node {}. Ignoring"
                    + " execution request for step {}.",
                    cluster.getLocalNode().getNodeName(),
                    stepRec != null ? stepRec.getStepId() : "<null>");
            return;
        }
        var step = pipeline.getStep(stepRec.getStepId());
        if (!HazelcastUtil.isClusterRunning(cluster)) {
            LOG.warn("Hazelcast cluster node not RUNNING for {}. "
                    + "Ignoring request to execute step {}.",
                    cluster.getLocalNode().getNodeName(),
                    stepRec.stepId);
            return;
        }

        if (!step.isDistributed()) {
            // Coordinator runs non-distributed steps; worker stays silent,
            LOG.debug("""
                Worker node "{}" got a request to execute \
                non-distributed step "{}". Letting coordinator do it. \
                Ignoring.""",
                    cluster.getLocalNode().getNodeName(),
                    stepRec.getStepId());
            return;
        }

        if (!state.getEncounteredSteps().add(stepRec.getStepId())) {
            LOG.debug("Worker node \"{}\" already got request to execute "
                    + "step \"{}\". Ignoring.",
                    cluster.getLocalNode().getNodeName(),
                    stepRec.getStepId());
            return;
        }

        // Offload step execution to avoid blocking Hazelcast listener
        stepExecutor.submit(() -> {
            LOG.info("Worker node \"{}\" executing pipeline \"{}\" "
                    + "step \"{}\".",
                    cluster.getLocalNode().getNodeName(),
                    stepRec.getPipelineId(),
                    stepRec.getStepId());
            try {
                state.pushWorkerStatus(PipelineStatus.RUNNING);
                step.execute(cluster.getCrawlSession());
                // If we were closed/demoted during execution, report failure
                if (Thread.currentThread().isInterrupted()
                        || !HazelcastUtil.isClusterRunning(cluster)) {
                    LOG.warn("Node {} closed/demoted during step {}, marking "
                            + "as FAILED.",
                            cluster.getLocalNode().getNodeName(),
                            stepRec.getStepId());
                    state.pushWorkerStatus(PipelineStatus.FAILED);
                    return;
                }
                state.pushWorkerStatus(PipelineStatus.COMPLETED);
            } catch (RuntimeException e) {
                LOG.error("Failure detected in pipeline {} step {} execution.",
                        stepRec.getPipelineId(),
                        stepRec.getStepId(),
                        e);
                state.pushWorkerStatus(PipelineStatus.FAILED);
            }
        });
    }
}
