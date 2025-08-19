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

import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineWorker implements AutoCloseable {
    private final InfinispanCluster cluster;
    private final Cache<StepRecord> currentStepCache;
    private final Cache<StepRecord> workerStatusCache;
    private Pipeline pipeline;
    private Step currentStep;
    private PipelineStatus workerStatus = PipelineStatus.PENDING;
    private final String pipeKey;
    private final String pipeWorkerKey;

    private final ScheduledExecutorService statusUpdater =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> statusFuture;
    // listener reference so we can remove it on close
    private Object stepListener;
    private volatile boolean running = false;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> completion =
            new CompletableFuture<>();
    private final Set<String> encounteredSteps = new HashSet<>();

    public PipelineWorker(
            @NonNull InfinispanCluster cluster,
            @NonNull Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        pipeKey = CacheKeys.pipelineKey(cluster, pipeline);
        pipeWorkerKey = CacheKeys.pipelineWorkerKey(cluster, pipeline);
        currentStepCache =
                cluster.getCacheManager().getPipelineCurrentStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusCache();
    }

    void start() {
        Thread.currentThread().setName("WORKER");
        running = true;
        // Periodically update worker status
        statusFuture = statusUpdater.scheduleAtFixedRate(() -> {
            if (running) {
                updateWorkerStatus(workerStatus);
            }
        }, 0, 10, TimeUnit.SECONDS); // adjust period as needed
        // Publish an initial readiness status so coordinator knows this worker exists
        try {
            var firstStepId = pipeline.getSteps().firstKey();
            var rec = new StepRecord();
            rec.setPipelineId(pipeline.getId());
            rec.setStepId(firstStepId);
            rec.setStatus(PipelineStatus.PENDING);
            rec.setUpdatedAt(System.currentTimeMillis());
            workerStatusCache.put(pipeWorkerKey, rec);
            LOG.debug("Registered worker readiness for pipeline {} on node {} "
                    + "(stepId={})",
                    pipeline.getId(),
                    cluster.getLocalNode().getNodeName(),
                    firstStepId);
        } catch (Exception e) {
            LOG.warn("Could not publish initial worker readiness for "
                    + "pipeline {} on node {}: {}",
                    pipeline.getId(),
                    cluster.getLocalNode().getNodeName(),
                    e.toString());
        }
        var stepRec = currentStepCache.get(pipeKey).orElse(null);
        if (stepRec != null && stepRec.getStepId() != null) {
            execute(getStep(pipeline, stepRec), stepRec);
        } else if (stepRec != null) {
            LOG.warn("Ignoring invalid current step record with null stepId "
                    + "for pipeline {} (key={}). Will wait for a valid update.",
                    pipeline.getId(), pipeKey);
        }
        stepListener = new PipelineStepChangeListener((key, rec) -> {
            if (rec.getPipelineId().equals(pipeline.getId())) {
                if (rec.getStepId() == null) {
                    LOG.warn("Received pipeline step record with null stepId "
                            + "for pipeline {} (key={}). Ignoring.",
                            pipeline.getId(), key);
                    return;
                }
                execute(getStep(pipeline, rec), rec);
            }
        });
        cluster.getCacheManager().addPipelineCurrentStepListener(stepListener);
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
            if (statusFuture != null) {
                statusFuture.cancel(true);
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
            if (stepListener != null) {
                try {
                    cluster.getCacheManager()
                            .removePipelineCurrentStepListener(stepListener);
                } catch (Exception e) {
                    LOG.debug("Could not remove step listener for "
                            + "pipeline {}: {}",
                            pipeline.getId(), e.toString());
                }
                stepListener = null;
            }
            LOG.debug("PipelineWorker closed for pipeline {}",
                    pipeline.getId());
            completion.complete(null);
        }
    }

    public CompletableFuture<Void> getCompletionFuture() {
        return completion;
    }

    void execute(Step step, StepRecord stepRec) {
        currentStep = step;
        if (step == null) {
            LOG.debug("No current step yet for pipeline {} on node {}. "
                    + "Waiting for coordinator to publish a step.",
                    pipeline.getId(), cluster.getLocalNode().getNodeName());
            return;
        }

        if (encounteredSteps.contains(step.getId())) {
            // not sure why it happens frequently and if normal:
            LOG.info("Pipeline {} step {} has already been executed or is "
                    + "being executed on node {}.",
                    stepRec.getPipelineId(),
                    stepRec.getStepId(),
                    cluster.getLocalNode().getNodeName());
            return;
        }
        encounteredSteps.add(step.getId());

        if ((stepRec.getStatus() != PipelineStatus.RUNNING)
                || !step.isDistributed()) {
            // Coordinator runs non-distributed steps; worker stays silent.
            return;
        }
        LOG.info("Executing pipeline {} step {}.",
                stepRec.getPipelineId(), stepRec.getStepId());
        try {
            updateWorkerStatus(PipelineStatus.RUNNING);
            step.execute(CrawlSession.get(cluster.getLocalNode()));
            updateWorkerStatus(PipelineStatus.COMPLETED);
        } catch (RuntimeException e) {
            LOG.error("Failure detected in pipeline {} step {} execution.",
                    stepRec.getPipelineId(),
                    stepRec.getStepId(),
                    e);
            updateWorkerStatus(PipelineStatus.FAILED);
        }
    }

    private void updateWorkerStatus(PipelineStatus status) {
        workerStatus = status;
        var stepId = currentStep != null ? currentStep.getId()
                : pipeline.getSteps().firstKey();
        var rec = new StepRecord();
        rec.setPipelineId(pipeline.getId());
        rec.setStepId(stepId);
        rec.setStatus(status);
        rec.setUpdatedAt(System.currentTimeMillis());
        workerStatusCache.put(pipeWorkerKey, rec);
    }

    private Step getStep(Pipeline pipeline, StepRecord rec) {
        if (rec == null || rec.getStepId() == null) {
            return null;
        }
        return pipeline.getStep(rec.getStepId());
    }
}
