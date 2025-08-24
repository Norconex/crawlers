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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.impl.infinispan.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanUtil;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineWorker implements AutoCloseable {
    private final InfinispanCluster cluster;
    private final Cache<StepRecord> pipeStepCache;
    private final Cache<StepRecord> workerStatusCache;
    private Pipeline pipeline;
    @Getter
    private Step currentStep;
    private PipelineStatus workerStatus = PipelineStatus.PENDING;
    private final String pipeKey;
    private final String pipeWorkerKey;

    private final ScheduledExecutorService statusUpdater =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> workerStatusFuture;
    // listener reference so we can remove it on close
    private CacheEntryChangeListener<StepRecord> pipelineStepListener;
    private volatile boolean running = false;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> completion =
            new CompletableFuture<>();
    private final Set<String> encounteredSteps = new HashSet<>();
    private long startedAt;

    public PipelineWorker(
            @NonNull InfinispanCluster cluster,
            @NonNull Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        pipeKey = CacheKeys.pipelineKey(cluster, pipeline);
        pipeWorkerKey = CacheKeys.pipelineWorkerKey(cluster, pipeline);
        pipeStepCache = cluster.getCacheManager().getPipelineStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusesCache();
    }

    PipelineResult start() {
        startedAt = System.currentTimeMillis();
        Thread.currentThread().setName("WORKER");
        running = true;
        // Periodically update worker status
        workerStatusFuture = statusUpdater.scheduleAtFixedRate(() -> {
            if (running) {
                updateWorkerStatus(workerStatus);
            }
        }, 0, 10, TimeUnit.SECONDS); // adjust period as needed

        var pipeStepRec = pipeStepCache.get(pipeKey).orElse(null);
        if (pipeStepRec != null && pipeStepRec.getStepId() != null) {
            executeStep(getStep(pipeline, pipeStepRec), pipeStepRec);
        } else if (pipeStepRec != null) {
            LOG.warn("Ignoring invalid current step record with null stepId "
                    + "for pipeline {} (key={}). Will wait for a valid update.",
                    pipeline.getId(), pipeKey);
        }
        pipelineStepListener = (key, pipeRec) -> {
            if (pipeRec.getPipelineId().equals(pipeline.getId())) {
                if (pipeRec.getStepId() == null) {
                    LOG.warn("Received pipeline step record with null stepId "
                            + "for pipeline {} (key={}). Ignoring.",
                            pipeline.getId(), key);
                    return;
                }
                if (pipeRec.getStatus() == PipelineStatus.RUNNING) {
                    executeStep(getStep(pipeline, pipeRec), pipeRec);
                } else {
                    LOG.info("Pipeline {} step {} on node {}: {}",
                            pipeRec.getPipelineId(),
                            pipeRec.getStepId(),
                            cluster.getLocalNode().getNodeName(),
                            pipeRec.getStatus());
                }
            }
        };
        cluster.getPipelineManager()
                .addStepChangeListener(pipelineStepListener);

        //TODO set timeout from coordinator not responding
        //TODO set timeout for crawler-wide timeout (or done in PipelineExecution)?
        return awaitPipelineTerminationResult(0L);
    }

    // end this worker by stopping any active task first then closing.
    public void stop() {
        //TODO implement properly... likely coordinator driving stop
        // execution and worker updating their statuses
        if (currentStep != null) {
            currentStep.stop(CrawlSession.get(cluster.getLocalNode()));
        }
        close();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOG.debug("Closing PipelineWorker for pipeline {}",
                    pipeline.getId());
            running = false;
            if (workerStatusFuture != null) {
                workerStatusFuture.cancel(true);
            }
            // attempt graceful shutdown of scheduler
            statusUpdater.shutdown();
            try {
                if (!statusUpdater.awaitTermination(2, TimeUnit.SECONDS)) {
                    statusUpdater.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                statusUpdater.shutdownNow();
            }
            if (pipelineStepListener != null) {
                try {
                    cluster.getPipelineManager()
                            .removeStepChangeListener(pipelineStepListener);
                } catch (Exception e) {
                    LOG.debug("Could not remove step listener for "
                            + "pipeline {}: {}",
                            pipeline.getId(), e.toString());
                }
                pipelineStepListener = null;
            }
            LOG.debug("PipelineWorker closed for pipeline {}",
                    pipeline.getId());
            completion.complete(null);
        }
    }

    public CompletableFuture<Void> getCompletionFuture() {
        return completion;
    }

    // except for initial invocation, this method is called by a cache listener
    private void executeStep(Step pipeStep, StepRecord pipeStepRec) {
        currentStep = pipeStep;
        if (!InfinispanUtil.isClusterRunning(cluster)) {
            LOG.warn("Infinispan cluster node not RUNNING for {}. "
                    + "Ignoring request to execute step {}.",
                    cluster.getLocalNode().getNodeName(),
                    pipeStepRec.stepId);
            return;
        }
        if (pipeStep == null) {
            LOG.debug("No current step yet for pipeline {} on node {}. "
                    + "Waiting for coordinator to publish a step.",
                    pipeline.getId(), cluster.getLocalNode().getNodeName());
            return;
        }
        if (pipeStepRec.getStatus().isTerminal()) {
            LOG.warn("Request was made to execute pipeline {} step {} "
                    + "on node {} while terminated: {}",
                    pipeline.getId(),
                    pipeStepRec.getStepId(),
                    cluster.getLocalNode().getNodeName(),
                    pipeStepRec.getStatus());
            return;
        }

        if (encounteredSteps.contains(pipeStep.getId())) {
            // not sure why it happens frequently and if normal:
            LOG.info("Pipeline {} step {} has already been executed or is "
                    + "executing. Node: {}.",
                    pipeStepRec.getPipelineId(),
                    pipeStepRec.getStepId(),
                    cluster.getLocalNode().getNodeName());
            return;
        }
        encounteredSteps.add(pipeStep.getId());

        if ((pipeStepRec.getStatus() != PipelineStatus.RUNNING)
                || !pipeStep.isDistributed()) {
            // Coordinator runs non-distributed steps; worker stays silent.
            return;
        }
        LOG.info("Executing pipeline {} step {}.",
                pipeStepRec.getPipelineId(), pipeStepRec.getStepId());
        try {
            updateWorkerStatus(PipelineStatus.RUNNING);
            pipeStep.execute(CrawlSession.get(cluster.getLocalNode()));
            updateWorkerStatus(PipelineStatus.COMPLETED);
        } catch (RuntimeException e) {
            LOG.error("Failure detected in pipeline {} step {} execution.",
                    pipeStepRec.getPipelineId(),
                    pipeStepRec.getStepId(),
                    e);
            updateWorkerStatus(PipelineStatus.FAILED);
        }
    }

    // From a worker standpoint
    private static class PipelineState {
        private boolean done = false;
        private boolean timedOut = false;
        private boolean threadAborted = false;
        private StepRecord rec = null;

        // it is terminated if the step is terminal and non COMPLETED, or
        // if the step is the last one and COMPLETED
        private boolean isPipelineTerminated(Pipeline pipeline) {
            return InfinispanUtil.isPipelineTerminated(pipeline, rec);
        }

        // Given the coordinator does not update the steps cache unless there
        // is a change, the expiry would be the crawler-wide max duration, if set.
        private boolean isExpired(long timeout) {
            return timeout > 0 && rec != null
                    && (System.currentTimeMillis()
                            - rec.getUpdatedAt() > timeout);
        }
    }

    private PipelineResult awaitPipelineTerminationResult(long timeout) {
        var state = new PipelineState();
        while (!state.done && !Thread.currentThread().isInterrupted()) {
            if (!resolvePipeState(state, timeout)) {
                break;
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            state.threadAborted = true;
            Thread.currentThread().interrupt(); // preserve interrupt status
        }
        PipelineStatus status;
        String lastAttemptedStepId;
        if (state.rec != null) {
            status = state.rec.getStatus();
            lastAttemptedStepId = state.rec.getStepId();
        } else {
            status = PipelineStatus.PENDING;
            lastAttemptedStepId = null;
        }
        if (state.timedOut && (status == null || !status.isTerminal())) {
            status = PipelineStatus.EXPIRED;
        }
        if (state.threadAborted && (status == null || !status.isTerminal())) {
            LOG.warn("Thread was aborted, marking node as FAILED.");
            status = PipelineStatus.FAILED;
        }
        return PipelineResult.builder()
                .pipelineId(pipeline.getId())
                .status(status)
                .lastStepId(lastAttemptedStepId)
                .startedAt(startedAt)
                .finishedAt(System.currentTimeMillis())
                .resumed(false)
                .timedOut(state.timedOut)
                .build();
    }

    // returns true if could resolve, else if something's wrong
    // and suggests breaking right away.
    private boolean resolvePipeState(PipelineState state, long timeout) {
        if (!InfinispanUtil.isClusterRunning(cluster)) {
            LOG.warn("Infinispan node is closed: {}",
                    cluster.getLocalNode().getNodeName());
            state.done = true;
            state.threadAborted = true;
            return false;
        }
        state.rec = pipeStepCache.get(pipeKey).orElse(null);
        if (state.isPipelineTerminated(pipeline)) {
            state.done = true;
        } else if (state.isExpired(timeout)) {
            state.done = true;
            state.timedOut = true;
        } else if (!sleep()) {
            state.done = true;
            state.threadAborted = true;
            return false;
        }
        return true;
    }

    // returns false if we could not sleep (thread was aborted)
    private boolean sleep() {
        try {
            Sleeper.sleepMillis(250);
            return true;
        } catch (RuntimeException e) { // SleeperException wraps InterruptedException
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            throw e; // propagate unexpected runtime issues
        }
    }

    private void updateWorkerStatus(PipelineStatus status) {
        workerStatus = status;
        if (InfinispanUtil.isClusterRunning(cluster)) {
            var stepId = currentStep != null ? currentStep.getId()
                    : pipeline.getSteps().firstKey();
            var rec = new StepRecord();
            rec.setPipelineId(pipeline.getId());
            rec.setStepId(stepId);
            rec.setStatus(status);
            rec.setUpdatedAt(System.currentTimeMillis());
            workerStatusCache.put(pipeWorkerKey, rec);
        }
    }

    private Step getStep(Pipeline pipeline, StepRecord rec) {
        if (rec == null || rec.getStepId() == null) {
            return null;
        }
        return pipeline.getStep(rec.getStepId());
    }
}
