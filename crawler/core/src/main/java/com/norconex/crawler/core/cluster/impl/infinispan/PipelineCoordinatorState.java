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
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinatorState implements AutoCloseable {
    private final InfinispanCluster cluster;
    private final Pipeline pipeline;
    private final CrawlSession session;

    // Cache Keys
    private final String workerKeyPrefix; // sessionId:pipelineId:runId
    private final String pipelineKey; // sessionId:pipelineId

    // Caches
    private final Cache<StepRecord> pipelineStepCache;
    private final Cache<StepRecord> workerStatusCache;

    // Flags
    @Getter
    private final AtomicBoolean closed = new AtomicBoolean(false);
    @Getter
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    // Current Step
    @Getter
    private final StepRecord currentStepRecord;

    // Misc.
    private final Map<String, StepRecord> workerStatusesMap = new HashMap<>();
    private CacheEntryChangeListener<StepRecord> workerStatusChangeListener;
    private long nodeExpiryTimeoutMs = 5_000; // 5 seconds

    public PipelineCoordinatorState(InfinispanCluster cluster,
            Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        session = cluster.getCrawlSession();

        pipelineKey = CacheKeys.pipelineKey(cluster, pipeline);
        workerKeyPrefix = CacheKeys.pipelineWorkerKeyPrefix(cluster, pipeline);

        pipelineStepCache =
                cluster.getCacheManager().getPipelineStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusCache();
        currentStepRecord = InfinispanUtil
                .currentPipelineStepRecordOrFirst(cluster, pipeline);

        initNodeExpiryTimeout();
        initWorkerStatusListener();
    }

    public Step getCurrentStep() {
        return pipeline.getStep(currentStepRecord.getStepId());
    }

    public void setRunningStep(@NonNull Step step) {
        currentStepRecord.setStepId(step.getId());
        pushPipelineStatus(PipelineStatus.RUNNING);
        // Reload statuses for this step only (on this pipeline/session/run)
        workerStatusesMap.clear();
        workerStatusCache.forEach((k, v) -> {
            if (k.startsWith(workerKeyPrefix)
                    && v != null
                    && Objects.equals(v.getStepId(), step.getId())) {
                updateWorkerStatusMap(k, v);
            }
        });
    }

    public void pushPipelineStatus(@NonNull PipelineStatus status) {
        currentStepRecord.setStatus(status);
        currentStepRecord.setUpdatedAt(System.currentTimeMillis());
        pipelineStepCache.put(pipelineKey, currentStepRecord);
        LOG.info("Coordinator wrote {}terminal status {} for "
                + "pipeline \"{}\" step \"{}\" to cache.",
                status.isTerminal() ? "" : "non-",
                status,
                pipeline.getId(),
                currentStepRecord.getStepId());
    }

    public boolean isStatusOfAllWorkers(@NonNull PipelineStatus status) {
        return workerStatusesMap.values().stream().allMatch(
                rec -> rec.getStatus() == status);
    }

    public PipelineStatus awaitTerminalWorkersReducedStatus() {
        var reducedStatus = PipelineStatus.PENDING;
        while (!reducedStatus.isTerminal()) {
            var nodeNames = cluster.getNodeNames();

            // If the cluster view is momentarily empty, wait for stability
            if (nodeNames.isEmpty()) {
                LOG.debug("No cluster nodes present; deferring reduction.");
                if (Thread.currentThread().isInterrupted()) {
                    return reducedStatus;
                }
                Sleeper.sleepMillis(250);
                continue;
            }

            // If no nodes left after filtering, we're done
            if (nodeNames.isEmpty()) {
                break;
            }

            var statuses = workerStatusesAsBag(nodeNames);

            reducedStatus = getCurrentStep().reduce(session, statuses);

            logWorkerStatusCounts(reducedStatus, statuses);

            if (!reducedStatus.isTerminal()) {
                if (Thread.currentThread().isInterrupted()) {
                    return reducedStatus;
                }
                Sleeper.sleepMillis(250);
            }
        }
        return reducedStatus;
    }

    private void logWorkerStatusCounts(
            PipelineStatus reducedStatus,
            Bag<PipelineStatus> statuses) {

        if (LOG.isInfoEnabled() && reducedStatus.isTerminal()) {
            var allWorkerNames = workerStatusesMap.keySet();
            var nodeNames = cluster.getNodeNames();
            LOG.info("""
                Step "{}" reduced to {} from worker statuses: {} COMPLETED, \
                {} FAILED, {} STOPPED, {} EXPIRED, {} DROPPED, {} JOINED""",
                    currentStepRecord.getStepId(),
                    reducedStatus,
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
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("""
                Step "{}" reduced to {} from worker statuses: {} PENDING, \
                {} RUNNING, {} COMPLETED, {} FAILED, {} STOPPING, {} STOPPED,\
                {} EXPIRED""",
                    currentStepRecord.getStepId(),
                    reducedStatus,
                    statuses.getCount(PipelineStatus.PENDING),
                    statuses.getCount(PipelineStatus.RUNNING),
                    statuses.getCount(PipelineStatus.COMPLETED),
                    statuses.getCount(PipelineStatus.FAILED),
                    statuses.getCount(PipelineStatus.STOPPING),
                    statuses.getCount(PipelineStatus.STOPPED),
                    statuses.getCount(PipelineStatus.EXPIRED));
        }
    }

    @Override
    public void close() throws Exception {
        if (workerStatusChangeListener != null) {
            try {
                cluster.getPipelineManager()
                        .removeWorkerStatusListener(workerStatusChangeListener);
            } catch (Exception e) {
                LOG.debug("Could not remove worker status listener for "
                        + "pipeline {}: {}",
                        pipeline.getId(), e.toString());
            }
        }
        workerStatusChangeListener = null;
    }

    //--- Private methods ------------------------------------------------------

    private Bag<PipelineStatus> workerStatusesAsBag(List<String> nodeNames) {
        Bag<PipelineStatus> statuses = new HashBag<>();

        // Process nodes currently in the cluster
        for (String nodeName : nodeNames) {
            // Use the latest worker status if and only if it is for this step
            var rec = workerStatusesMap.get(nodeName);
            if (rec == null || !Objects.equals(rec.getStepId(),
                    currentStepRecord.getStepId())) {
                // Node has no status for this step yet
                // During shutdown, if we're stopping and node stays PENDING,
                // treat it as STOPPED after a brief timeout
                if (currentStepRecord.getStatus() == PipelineStatus.STOPPING) {
                    // If we're in STOPPING state and this node never reported,
                    // it's likely a stopper node or already departed - mark as STOPPED
                    LOG.info(
                            "Node {} has no status for step {} during shutdown - marking as STOPPED",
                            nodeName,
                            currentStepRecord.getStepId());
                    statuses.add(PipelineStatus.STOPPED);
                } else {
                    // Normal operation - node hasn't started this step yet
                    LOG.info(
                            "Node {} has no status for step {} (rec={}, recStepId={}, currentStepId={})",
                            nodeName,
                            currentStepRecord.getStepId(),
                            rec != null ? "present" : "null",
                            rec != null ? rec.getStepId() : "N/A",
                            currentStepRecord.getStepId());
                    statuses.add(PipelineStatus.PENDING);
                }
            } else {
                var isExpired = rec.hasTimedOut(nodeExpiryTimeoutMs);
                if (isExpired) {
                    // During shutdown, treat expired nodes as STOPPED rather than EXPIRED
                    // since they likely departed gracefully
                    if (currentStepRecord
                            .getStatus() == PipelineStatus.STOPPING) {
                        LOG.info(
                                "Node {} status expired during shutdown (last update "
                                        + "{} ms ago, was {}) - marking as STOPPED",
                                nodeName,
                                System.currentTimeMillis() - rec.getUpdatedAt(),
                                rec.getStatus());
                        statuses.add(PipelineStatus.STOPPED);
                    } else {
                        LOG.warn(
                                "Node {} status EXPIRED for step {} - last update "
                                        + "was {} ms ago (timeout: {} ms, status: {})",
                                nodeName,
                                currentStepRecord.getStepId(),
                                System.currentTimeMillis() - rec.getUpdatedAt(),
                                nodeExpiryTimeoutMs,
                                rec.getStatus());
                        statuses.add(PipelineStatus.EXPIRED);
                    }
                } else {
                    statuses.add(rec.getStatus());
                }
            }
        }

        // Also process nodes that reported a status for this step but have
        // since left the cluster (e.g., after completing work and exiting).
        // Treat them as having their last reported status if terminal,
        // or EXPIRED if non-terminal (they didn't complete gracefully).
        for (var entry : workerStatusesMap.entrySet()) {
            var nodeName = entry.getKey();
            var rec = entry.getValue();

            // Skip if node is still in cluster (already processed above)
            // Skip if the status is for a different step
            if (nodeNames.contains(nodeName) || !Objects.equals(rec.getStepId(),
                    currentStepRecord.getStepId())) {
                continue;
            }

            // Node has left the cluster but had reported a status for this step
            var lastStatus = rec.getStatus();
            if (lastStatus.isTerminal()) {
                // Node completed its work and exited - honor its final status
                LOG.info(
                        "Node {} has left the cluster with terminal status {} for step {}",
                        nodeName,
                        lastStatus,
                        currentStepRecord.getStepId());
                statuses.add(lastStatus);
            } else {
                // Node left without completing - treat as EXPIRED
                LOG.info(
                        "Node {} has left the cluster with non-terminal status {} for step {} - marking as EXPIRED",
                        nodeName,
                        lastStatus,
                        currentStepRecord.getStepId());
                statuses.add(PipelineStatus.EXPIRED);
            }
        }

        LOG.debug("Worker status map keys: {}, cluster node names: {}",
                workerStatusesMap.keySet(), nodeNames);
        return statuses;
    }

    private void updateWorkerStatusMap(String key, StepRecord stepRec) {
        if (!pipeline.getId().equals(stepRec.getPipelineId())) {
            return; // ignore statuses for other pipelines
        }
        var nodeName = StringUtils.substringAfterLast(key, ":");
        LOG.debug(
                "Updating worker status map: key={}, extracted nodeName={}, stepId={}, status={}",
                key, nodeName, stepRec.getStepId(), stepRec.getStatus());
        workerStatusesMap.put(nodeName, stepRec);
    }

    private void initWorkerStatusListener() {
        workerStatusChangeListener = (key, rec) -> {
            if (!key.startsWith(workerKeyPrefix)) {
                return; // ignore statuses from other runs/sessions
            }
            if (rec.getPipelineId().equals(pipeline.getId())) {
                updateWorkerStatusMap(key, rec);
            }
        };
        cluster.getPipelineManager()
                .addWorkerStatusListener(workerStatusChangeListener);
    }

    private void initNodeExpiryTimeout() {
        var connector = session.getCrawlContext().getCrawlConfig()
                .getClusterConnector();
        if (connector instanceof InfinispanClusterConnector conn) {
            nodeExpiryTimeoutMs = ofNullable(
                    conn.getConfiguration().getNodeExpiryTimeout())
                            .map(Duration::toMillis)
                            .orElse(nodeExpiryTimeoutMs);
        }
    }
}
