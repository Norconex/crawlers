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
import java.util.Objects;
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
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

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
    private StepRecord activeStepRecord;

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
        // Warm-up: wait briefly for stable cluster view and caches
        waitForClusterWarmUp();
        workerStatusListener = (key, rec) -> {
            if (rec.getPipelineId().equals(pipeline.getId())) {
                updateWorkerStatus(key, rec);
            }
        };
        cluster.getPipelineManager()
                .addWorkerStatusListener(workerStatusListener);
        try {
            doCoordinatePipelineExecution();
        } catch (RuntimeException e) {
            if (!isInterruption(e)) {
                throw e;
            }
            LOG.info("Coordinator interrupted/demoted while coordinating "
                    + "pipeline {}, exiting gracefully.",
                    pipeline.getId());
        }
        LOG.info("Pipeline {}  terminated.", pipeline.getId());
        close();
    }

    private boolean isInterruption(Throwable t) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        var cur = t;
        var depth = 0;
        while (cur != null && depth++ < 5) {
            if (cur instanceof java.lang.InterruptedException
                    || cur instanceof java.util.concurrent.CancellationException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void waitForClusterWarmUp() {
        // Allow up to ~5 seconds for same-JVM multi-node tests to stabilize
        var deadline = System.currentTimeMillis() + 5_000;
        var cm = cluster.getCacheManager();
        var lastNames = cluster.getNodeNames();
        var stableTicks = 0;
        while (System.currentTimeMillis() < deadline) {
            if (!InfinispanUtil.isClusterRunning(cluster)) {
                Sleeper.sleepMillis(50);
                continue;
            }
            // Touch coordination caches to ensure they are created locally
            try {
                cm.getPipelineStepCache();
                cm.getPipelineWorkerStatusesCache();
            } catch (Exception e) {
                // ignore and retry until deadline
            }
            var names = cluster.getNodeNames();
            if (names.equals(lastNames)) {
                stableTicks++;
            } else {
                stableTicks = 0;
            }
            lastNames = names;
            if (stableTicks >= 5) { // ~500ms of stability
                return;
            }
            Sleeper.sleepMillis(100);
        }
        LOG.debug("Cluster warm-up timed out; proceeding.");
    }

    void stop() {
        ExceptionSwallower.runWithInterruptClear(() -> {
            if (activeStepRecord != null
                    && stopRequested.compareAndSet(false, true)) {
                var key = CacheKeys.pipelineKey(cluster, pipeline);

                activeStepRecord.setStatus(PipelineStatus.STOPPING);
                activeStepRecord.setUpdatedAt(System.currentTimeMillis());
                pipelineActiveStepCache.put(key, activeStepRecord);

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

                activeStepRecord.setUpdatedAt(System.currentTimeMillis());
                activeStepRecord.setStatus(PipelineStatus.STOPPED);
                pipelineActiveStepCache.put(key, activeStepRecord);
            }
        });
    }

    void doCoordinatePipelineExecution() {

        //TODO add progress reporter here?

        var key = CacheKeys.pipelineKey(cluster, pipeline);

        activeStepRecord = resolveFirstStepToRun(key, pipeline);

        // If pipeline already terminal, just record and exit without
        // modifying caches.

        //TODO when a new run, does the crawler wipe out the pipeline state
        // or shall we rely on the run ID or equivalent to know if
        // we restart when pipeline is terminated or just leave?
        // For now, we leave here.
        if (InfinispanUtil.isPipelineTerminated(pipeline, activeStepRecord)) {
            LOG.debug("Coordinator detected terminal pipeline {} at step {} "
                    + "with status {}. Exiting.",
                    pipeline.getId(),
                    activeStepRecord.getStepId(),
                    activeStepRecord.getStatus());
            return;
        }

        var firstStepToRun = pipeline.getStep(activeStepRecord.getStepId());
        // If picking up from another coordinator, log it.
        if (firstStepToRun != pipeline.getFirstStep()
                || activeStepRecord.getStatus() == PipelineStatus.RUNNING) {
            LOG.info("Resuming coordination of pipeline {} at step {} "
                    + "with status {} on node {}.",
                    pipeline.getId(),
                    activeStepRecord.stepId,
                    activeStepRecord.status,
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
            if (activeStepRecord.getStatus() != PipelineStatus.RUNNING) {
                activeStepRecord = createRunningStepRecord(step);
                pipelineActiveStepCache.put(key, activeStepRecord);
                LOG.debug("Published RUNNING for pipeline {} step {}",
                        pipeline.getId(), step.getId());
            }

            var execStatus = step.isDistributed()
                    ? executeOnAllNodes(step, activeStepRecord)
                    : executeLocally(step);

            activeStepRecord.setStatus(execStatus);
            activeStepRecord.setUpdatedAt(System.currentTimeMillis());
            if (!InfinispanUtil.isClusterRunning(cluster)) {
                LOG.warn("Coordinator node went down on {}. It will no longer "
                        + "execute pipeine {}.",
                        cluster.getLocalNode().getNodeName(),
                        pipeline.getId());
                return;
            }
            pipelineActiveStepCache.put(key, activeStepRecord);
            LOG.debug("Published {} for pipeline {} step {}", execStatus,
                    pipeline.getId(), step.getId());
            if (execStatus == PipelineStatus.FAILED) {
                LOG.info("Aborting pipeline execution...");
                return;
            }
        }
        // if we exit loop without failure, finalStatus already set to last
        // step status
    }

    private void updateWorkerStatus(String key, StepRecord stepRec) {
        if (!pipeline.getId().equals(stepRec.getPipelineId())) {
            return; // ignore statuses for other pipelines
        }
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
                // Use the latest worker status if and only if it is for this step
                var rec = workerStatuses.get(nodeName);
                if (rec == null
                        || !pipeline.getId().equals(rec.getPipelineId())
                        || !Objects.equals(rec.getStepId(), step.getId())) {
                    // stale status from a previous step: ignore and wait
                    statuses.add(PipelineStatus.PENDING);
                    continue;
                }
                statuses.add(hasTimedOut(rec)
                        ? PipelineStatus.EXPIRED
                        : rec.getStatus());
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
        // Add a small grace to avoid false expirations during brief coordinator switches
        var graceMs = 1000L;
        return System.currentTimeMillis()
                - rec.updatedAt > (nodeExpiryTimeoutMs + graceMs);
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