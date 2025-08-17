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
import java.util.Map;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.PipelineStep;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineCoordinator {

    private static long TIMEOUT_MS = 30_000;

    private final InfinispanCluster cluster;
    private final Cache<PipelineStepRecord> pipelineRecordsCache;
    private final Cache<PipelineStepRecord> workerStatusCache;
    private final Pipeline pipeline;
    private final Map<String, PipelineStepRecord> workerStatuses =
            new HashMap<>();

    public PipelineCoordinator(InfinispanCluster cluster, Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        pipelineRecordsCache =
                cluster.getCacheManager().getPipelineCurrentStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusCache();
    }

    void start() {
        // Now runs in the caller thread (already spawned asynchronously by the manager).
        Thread.currentThread().setName("COORDINATOR");
        doCoordinatePipelineExecution();
        LOG.info("Pipeline {}  terminated.", pipeline.getId());
    }

    void doCoordinatePipelineExecution() {
        cluster.getCacheManager().addPipelineWorkerStatusListener(
                new PipelineStepChangeListener((key, rec) -> {
                    System.err.println(
                            "XXX Coordinator got this: " + key + " -> " + rec);
                    if (rec.getPipelineId().equals(pipeline.getId())) {
                        updateWorkerStatus(key, rec);
                    }
                }));
        workerStatusCache.forEach(this::updateWorkerStatus);

        //TODO do a sweep to load existing statuses

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
            // create and publish RUNNING record for this step
            var runningRec = new PipelineStepRecord();
            runningRec.setPipelineId(pipeline.getId());
            runningRec.setStepId(step.getId());
            runningRec.setStatus(PipelineStatus.RUNNING);
            runningRec.setUpdatedAt(System.currentTimeMillis());
            pipelineRecordsCache.put(key, runningRec);
            LOG.debug("Published RUNNING for pipeline {} step {}",
                    pipeline.getId(), step.getId());

            var execStatus = step.isDistributed()
                    ? executeOnAllNodes(step, key, runningRec)
                    : executeLocally(step);

            // publish terminal status for this step
            runningRec.setStatus(execStatus);
            runningRec.setUpdatedAt(System.currentTimeMillis());
            pipelineRecordsCache.put(key, runningRec);
            LOG.debug("Published {} for pipeline {} step {}", execStatus,
                    pipeline.getId(), step.getId());

            currentRec = runningRec; // advance pointer
            if (execStatus == PipelineStatus.FAILED) {
                LOG.info("Aborting pipeline execution...");
                return;
            }
        }
    }

    private void updateWorkerStatus(String key, PipelineStepRecord stepRec) {
        workerStatuses.put(
                StringUtils.substringAfterLast(key, ":"), stepRec);
    }

    private PipelineStatus executeLocally(PipelineStep step) {
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

    private PipelineStatus executeOnAllNodes(PipelineStep step, String pipeKey,
            PipelineStepRecord runningRec) {
        LOG.info("Running step {} on all nodes.", step.getId());
        var start = System.currentTimeMillis();
        var nodeNames = cluster.getAllNodeNames();
        // Fast path: only one node -> run locally but still considered distributed
        if (nodeNames.size() == 1) {
            try {
                step.execute(CrawlSession.get(cluster.getLocalNode()));
                // simulate worker completion entry for coordinator logic consistency
                var rec = new PipelineStepRecord();
                rec.setPipelineId(pipeline.getId());
                rec.setStepId(step.getId());
                rec.setStatus(PipelineStatus.COMPLETED);
                rec.setUpdatedAt(System.currentTimeMillis());
                workerStatuses.put(nodeNames.get(0), rec);
                return PipelineStatus.COMPLETED;
            } catch (RuntimeException e) {
                LOG.error("Single-node distributed step {} failed.",
                        step.getId(), e);
                return PipelineStatus.FAILED;
            }
        }
        var reducedStatus = PipelineStatus.PENDING;
        while (!reducedStatus.isTerminal()) {
            var allPresent = workerStatuses.keySet().containsAll(nodeNames);
            Bag<PipelineStatus> statuses = new HashBag<>();
            workerStatuses.values().forEach(r -> statuses.add(r.getStatus()));
            var allTerminal = allPresent
                    && statuses.stream().allMatch(PipelineStatus::isTerminal);
            if (allTerminal) {
                LOG.info(
                        "Distributed step {} node statuses: {} COMPLETED, {} FAILED, {} STOPPED",
                        step.getId(),
                        statuses.getCount(PipelineStatus.COMPLETED),
                        statuses.getCount(PipelineStatus.FAILED),
                        statuses.getCount(PipelineStatus.STOPPED));
                if (statuses.getCount(PipelineStatus.COMPLETED) > 0) {
                    reducedStatus = PipelineStatus.COMPLETED;
                } else if (statuses.getCount(PipelineStatus.STOPPED) > 0) {
                    reducedStatus = PipelineStatus.STOPPED;
                } else {
                    reducedStatus = PipelineStatus.FAILED;
                }
            } else if (System.currentTimeMillis() - start > TIMEOUT_MS) {
                LOG.warn(
                        "Distributed step {} timed out waiting for all workers. Present: {}/{}",
                        step.getId(), workerStatuses.size(), nodeNames.size());
                if (statuses.getCount(PipelineStatus.COMPLETED) > 0) {
                    reducedStatus = PipelineStatus.COMPLETED;
                } else {
                    reducedStatus = PipelineStatus.FAILED;
                }
            } else {
                Sleeper.sleepMillis(250);
            }
        }
        return reducedStatus;
    }

    private PipelineStepRecord resolveFirstStep(String key, Pipeline pipeline) {

        // get starting step
        var stepRec = pipelineRecordsCache
                .get(key)
                .orElse(null);
        if (stepRec == null) {
            var stepId = pipeline.getSteps().firstKey();
            stepRec = new PipelineStepRecord();
            stepRec.setPipelineId(pipeline.getId());
            stepRec.setStepId(stepId);
        } else {
            LOG.info("Resuming pipeline %s at step %s from status %s".formatted(
                    stepRec.getPipelineId(), stepRec.getStepId(),
                    stepRec.getStatus()));
        }

        return stepRec;
    }

}