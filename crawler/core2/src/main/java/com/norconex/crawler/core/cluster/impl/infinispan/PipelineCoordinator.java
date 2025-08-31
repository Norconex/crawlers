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

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.impl.infinispan.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.util.ConcurrentUtil;
import com.norconex.crawler.core2.util.ExceptionSwallower;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinator implements AutoCloseable {

    private final InfinispanCluster cluster;
    private final Cache<StepRecord> pipelineActiveStepCache;
    private final Cache<StepRecord> workerStatusCache;
    private final Pipeline pipeline;
    private Step currentLocalStep;
    private final Map<String, StepRecord> workerStatuses =
            new HashMap<>();

    private CacheEntryChangeListener<StepRecord> workerStatusListener;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private CrawlSession session;
    private long nodeExpiryTimeoutMs = 30_000;
    private StepRecord activePipeRec;

    //TODO make sure it respects crawler-wide timeout if set

    public PipelineCoordinator(InfinispanCluster cluster, Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        pipelineActiveStepCache =
                cluster.getCacheManager().getPipelineStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusesCache();
        session = CrawlSession.get(cluster.getLocalNode());

        var connector = session.getCrawlContext().getCrawlConfig()
                .getClusterConnector();
        if (connector instanceof InfinispanClusterConnector conn) {
            nodeExpiryTimeoutMs =
                    ofNullable(conn.getConfiguration().getNodeExpiryTimeout())
                            .map(Duration::toMillis)
                            .orElse(nodeExpiryTimeoutMs);
        }
    }

    void start() {
        Thread.currentThread().setName("COORDINATOR");
        workerStatusListener = (key, rec) -> {
            if (rec.getPipelineId().equals(pipeline.getId())) {
                updateWorkerStatus(key, rec);
            }
        };
        cluster.getPipelineManager()
                .addWorkerStatusListener(workerStatusListener);
        doCoordinatePipelineExecution();
        LOG.info("Pipeline {}  terminated.", pipeline.getId());
        close();
    }

    void stop() {
        ExceptionSwallower.runWithInterruptClear(() -> {
            if (activePipeRec != null
                    && stopRequested.compareAndSet(false, true)) {
                var key = CacheKeys.pipelineKey(cluster, pipeline);

                activePipeRec.setStatus(PipelineStatus.STOPPING);
                activePipeRec.setUpdatedAt(System.currentTimeMillis());
                pipelineActiveStepCache.put(key, activePipeRec);

                if (currentLocalStep != null) {
                    currentLocalStep.stop(session);
                } else {
                    var allStopped = ConcurrentUtil.waitUntil(
                            () -> workerStatuses.values().stream().allMatch(
                                    rec -> rec
                                            .getStatus() == PipelineStatus.STOPPED),
                            Duration.ofMinutes(1), Duration.ofSeconds(1));
                    if (allStopped) {
                        LOG.info("App pipeline workers stopped.");
                    } else {
                        LOG.warn("Not all pipeline workers stopped within a "
                                + "minute.");
                    }
                }

                activePipeRec.setUpdatedAt(System.currentTimeMillis());
                activePipeRec.setStatus(PipelineStatus.STOPPED);
                pipelineActiveStepCache.put(key, activePipeRec);
            }
        });
    }

    void doCoordinatePipelineExecution() {
        var key = CacheKeys.pipelineKey(cluster, pipeline);

        activePipeRec = resolveFirstStepToRun(key, pipeline);

        // If pipeline already terminal, just record and exit without
        // modifying caches.

        //TODO when a new run, does the crawler wipe out the pipeline state
        // or shall we rely on the run ID or equivalent to know if
        // we restart when pipeline is terminated or just leave?
        // For now, we leave here.
        if (InfinispanUtil.isPipelineTerminated(pipeline, activePipeRec)) {
            LOG.debug("Coordinator detected terminal pipeline {} at step {} "
                    + "with status {}. Exiting.",
                    pipeline.getId(),
                    activePipeRec.getStepId(),
                    activePipeRec.getStatus());
            return;
        }

        var firstStepToRun = pipeline.getStep(activePipeRec.getStepId());
        // If picking up from another coordinator, log it.
        if (firstStepToRun != pipeline.getFirstStep()
                || activePipeRec.getStatus() == PipelineStatus.RUNNING) {
            LOG.info("Resuming coordination of pipeline {} at step {} "
                    + "with status {} on node {}.",
                    pipeline.getId(),
                    activePipeRec.stepId,
                    activePipeRec.status,
                    cluster.getLocalNode().getNodeName());
        }

        var steps = pipeline
                .getSteps()
                .values()
                .stream()
                .dropWhile(step -> !step.getId().equals(firstStepToRun.getId()))
                .toList();

        for (var step : steps) {
            if (stopRequested.get()) {
                LOG.info("Stop requested before executing {}.", step.getId());
            }

            workerStatuses.clear();
            //TODO not sure why this seems needed (likely race condition) since
            // we have the listener to workers... and we technically don't
            // need to know there state before starting a step.
            workerStatusCache.forEach(this::updateWorkerStatus);

            // could be RUNNING already if recovering from other coordinator.
            if (activePipeRec.getStatus() != PipelineStatus.RUNNING) {
                activePipeRec = createRunningStepRecord(step);
                pipelineActiveStepCache.put(key, activePipeRec);
                LOG.debug("Published RUNNING for pipeline {} step {}",
                        pipeline.getId(), step.getId());
            }

            var execStatus = step.isDistributed()
                    ? executeOnAllNodes(step, activePipeRec)
                    : executeLocally(step);

            activePipeRec.setStatus(execStatus);
            activePipeRec.setUpdatedAt(System.currentTimeMillis());
            if (!InfinispanUtil.isClusterRunning(cluster)) {
                LOG.warn("Coordinator node went down on {}. It will no longer "
                        + "execute pipeine {}.",
                        cluster.getLocalNode().getNodeName(),
                        pipeline.getId());
                return;
            }
            pipelineActiveStepCache.put(key, activePipeRec);
            LOG.debug("Published {} for pipeline {} step {}", execStatus,
                    pipeline.getId(), step.getId());
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
        currentLocalStep = step;
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

    private PipelineStatus executeOnAllNodes(Step step, StepRecord runningRec) {
        currentLocalStep = null;
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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
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
                step.getId(),
                statuses.size(),
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
        return System.currentTimeMillis() - rec.updatedAt > nodeExpiryTimeoutMs;
    }

    private StepRecord resolveFirstStepToRun(String key, Pipeline pipeline) {

        // get starting step
        var stepRec = pipelineActiveStepCache
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
        ExceptionSwallower.runWithInterruptClear(() -> {
            if (closed.compareAndSet(false, true)
                    && (workerStatusListener != null)) {
                try {
                    cluster.getPipelineManager()
                            .removeWorkerStatusListener(workerStatusListener);
                } catch (Exception e) {
                    LOG.debug(
                            "Could not remove worker status listener for pipeline {}: {}",
                            pipeline.getId(), e.toString());
                }
                workerStatusListener = null;
            }
        });
    }
}