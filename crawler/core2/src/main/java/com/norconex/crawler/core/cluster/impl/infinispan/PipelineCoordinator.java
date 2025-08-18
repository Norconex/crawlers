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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinator implements AutoCloseable {

    private static long TIMEOUT_MS = 30_000;

    private final InfinispanCluster cluster;
    private final Cache<StepRecord> pipelineRecordsCache;
    private final Cache<StepRecord> workerStatusCache;
    private final Pipeline pipeline;
    private final Map<String, StepRecord> workerStatuses =
            new HashMap<>();

    private Object workerStatusListener;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<PipelineResult> completion = new CompletableFuture<>();

    private String lastStepId;
    private PipelineStatus finalStatus = PipelineStatus.PENDING;
    private long startedAt = System.currentTimeMillis();
    private long finishedAt;
    private boolean resumed;

    public PipelineCoordinator(InfinispanCluster cluster, Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        pipelineRecordsCache =
                cluster.getCacheManager().getPipelineCurrentStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusCache();
    }

    void start() {
        Thread.currentThread().setName("COORDINATOR");
        // detect resume: if cache already had a record
        resumed = pipelineRecordsCache.get(CacheKeys.pipelineKey(cluster, pipeline)).isPresent();
        workerStatusListener = new PipelineStepChangeListener((key, rec) -> {
            if (rec.getPipelineId().equals(pipeline.getId())) {
                updateWorkerStatus(key, rec);
            }
        });
        cluster.getCacheManager().addPipelineWorkerStatusListener(workerStatusListener);
        doCoordinatePipelineExecution();
        LOG.info("Pipeline {}  terminated.", pipeline.getId());
        finishedAt = System.currentTimeMillis();
        close();
    }

    void doCoordinatePipelineExecution() {
        // initial sweep
        workerStatusCache.forEach(this::updateWorkerStatus);

        var key = CacheKeys.pipelineKey(cluster, pipeline);

        var currentRec = resolveFirstStep(key, pipeline);
        var firstStep = pipeline.getStep(currentRec.getStepId());

        var steps = pipeline
                .getSteps()
                .values()
                .stream()
                .dropWhile(step -> !step.getId().equals(firstStep.getId()))
                .toList();

        for (var step : steps) {
            // reset worker statuses for this step BEFORE publishing running record
            workerStatuses.clear();
            var stepRec = createRunningStepRecord(step);
            pipelineRecordsCache.put(key, stepRec);
            LOG.debug("Published RUNNING for pipeline {} step {}",
                    pipeline.getId(), step.getId());

            var execStatus = step.isDistributed() && cluster.getNodeCount() > 1
                    ? executeOnAllNodes(step, key, stepRec)
                    : executeLocally(step);

            stepRec.setStatus(execStatus);
            stepRec.setUpdatedAt(System.currentTimeMillis());
            pipelineRecordsCache.put(key, stepRec);
            LOG.debug("Published {} for pipeline {} step {}", execStatus,
                    pipeline.getId(), step.getId());
            lastStepId = step.getId();
            finalStatus = execStatus;
            if (execStatus == PipelineStatus.FAILED) {
                LOG.info("Aborting pipeline execution...");
                return;
            }
        }
        // if we exit loop without failure, finalStatus already set to last step status
    }

    private void updateWorkerStatus(String key, StepRecord stepRec) {
        workerStatuses.put(
                StringUtils.substringAfterLast(key, ":"), stepRec);
    }

    private PipelineStatus executeLocally(Step step) {
        LOG.info("Running step {} on a single node.", step.getId());
        try {
            step.execute(CrawlSession.get(cluster.getLocalNode()));
            LOG.info("Pipeline {} step {} completed.",
                    pipeline.getId(), step.getId());
            return PipelineStatus.COMPLETED;
        } catch (RuntimeException e) {
            LOG.error("Pipeline step {} failed.", step.getId(), e);
            return PipelineStatus.FAILED;
        }
    }

    private PipelineStatus executeOnAllNodes(Step step, String pipeKey,
            StepRecord runningRec) {
        LOG.info("Running step {} on all nodes.", step.getId());

        var reducedStatus = PipelineStatus.PENDING;
        while (!reducedStatus.isTerminal()) {
            Bag<PipelineStatus> statuses = new HashBag<>();
            var nodeNames = cluster.getNodeNames();
            for (String nodeName : nodeNames) {
                // we assume a node has not yet started if not found,
                // so we default to PENDING so we can track for timeout
                var stepRec = workerStatuses.computeIfAbsent(nodeName,
                        nm -> createPendingStepRecord(step));
                statuses.add(hasTimedOut(stepRec)
                        ? PipelineStatus.EXPIRED
                        : stepRec.getStatus());
            }
            reducedStatus = step.reduce(
                    CrawlSession.get(cluster.getLocalNode()), statuses);
            if (reducedStatus.isTerminal()) {
                logTerminalNodeStatuses(step, statuses, nodeNames);
            } else {
                Sleeper.sleepMillis(250);
            }
        }
        return reducedStatus;
    }

    private void logTerminalNodeStatuses(
            Step step,
            Bag<PipelineStatus> statuses,
            List<String> nodeNames) {
        var allWorkerNames = workerStatuses.keySet();
        LOG.info("""
            Distributed step "{}" {} node statuses: \
            {} COMPLETED, \
            {} FAILED, \
            {} STOPPED, \
            {} EXPIRED, \
            {} DROPPED, \
            {} JOINED""",
                statuses.size(),
                step.getId(),
                statuses.getCount(PipelineStatus.COMPLETED),
                statuses.getCount(PipelineStatus.FAILED),
                statuses.getCount(PipelineStatus.STOPPED),
                statuses.getCount(PipelineStatus.EXPIRED),
                allWorkerNames.stream()
                        .filter(el -> !nodeNames.contains(el))
                        .count(),
                nodeNames.stream()
                        .filter(el -> !allWorkerNames.contains(el))
                        .count());
    }

    private boolean hasTimedOut(StepRecord rec) {
        return System.currentTimeMillis() - rec.updatedAt > TIMEOUT_MS;
    }

    private StepRecord resolveFirstStep(String key, Pipeline pipeline) {

        // get starting step
        var stepRec = pipelineRecordsCache
                .get(key)
                .orElse(null);
        if (stepRec == null) {
            var stepId = pipeline.getSteps().firstKey();
            stepRec = new StepRecord();
            stepRec.setPipelineId(pipeline.getId());
            stepRec.setStepId(stepId);
        } else {
            LOG.info("Resuming pipeline %s at step %s from status %s".formatted(
                    stepRec.getPipelineId(), stepRec.getStepId(),
                    stepRec.getStatus()));
        }

        return stepRec;
    }

    private StepRecord createPendingStepRecord(Step step) {
        return createStepRecord(step, PipelineStatus.PENDING);
    }

    private StepRecord createRunningStepRecord(Step step) {
        return createStepRecord(step, PipelineStatus.RUNNING);
    }

    private StepRecord createStepRecord(Step step, PipelineStatus status) {
        return new StepRecord()
                .setPipelineId(pipeline.getId())
                .setStepId(step.getId())
                .setStatus(status)
                .setUpdatedAt(System.currentTimeMillis());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (workerStatusListener != null) {
                try {
                    cluster.getCacheManager().removePipelineWorkerStatusListener(workerStatusListener);
                } catch (Exception e) {
                    LOG.debug("Could not remove worker status listener for pipeline {}: {}", pipeline.getId(), e.toString());
                }
                workerStatusListener = null;
            }
            completion.complete(PipelineResult.builder()
                    .pipelineId(pipeline.getId())
                    .status(finalStatus)
                    .lastStepId(lastStepId)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt == 0 ? System.currentTimeMillis() : finishedAt)
                    .resumed(resumed)
                    .timedOut(false)
                    .build());
        }
    }

    public CompletableFuture<PipelineResult> getCompletionFuture() {
        return completion;
    }
}