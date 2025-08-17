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

import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.PipelineStep;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineWorker {
    private final InfinispanCluster cluster;
    private final Cache<PipelineStepRecord> currentStepCache;
    private final Cache<PipelineStepRecord> workerStatusCache;
    private Pipeline pipeline;
    private PipelineStep currentStep;
    private PipelineStatus workerStatus = PipelineStatus.PENDING;
    private final String pipeKey;
    private final String pipeWorkerKey;

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
        // Publish an initial readiness status so coordinator knows this worker exists
        try {
            var firstStepId = pipeline.getSteps().firstKey();
            var rec = new PipelineStepRecord();
            rec.setPipelineId(pipeline.getId());
            rec.setStepId(firstStepId);
            rec.setStatus(PipelineStatus.PENDING);
            rec.setUpdatedAt(System.currentTimeMillis());
            workerStatusCache.put(pipeWorkerKey, rec);
            LOG.debug("Registered worker readiness for pipeline {} on node {} (stepId={})", pipeline.getId(), cluster.getLocalNode().getNodeName(), firstStepId);
        } catch (Exception e) {
            LOG.warn("Could not publish initial worker readiness for pipeline {} on node {}: {}", pipeline.getId(), cluster.getLocalNode().getNodeName(), e.toString());
        }
        var stepRec = currentStepCache.get(pipeKey).orElse(null);
        if (stepRec != null && stepRec.getStepId() != null) {
            execute(getStep(pipeline, stepRec), stepRec);
        } else if (stepRec != null) {
            LOG.warn("Ignoring invalid current step record with null stepId for pipeline {} (key={}). Will wait for a valid update.",
                    pipeline.getId(), pipeKey);
        }
        cluster.getCacheManager().addPipelineCurrentStepListener(
                new PipelineStepChangeListener((key, rec) -> {
                    System.err.println("XXX GOT SOMETHING?");
                    if (rec.getPipelineId().equals(pipeline.getId())) {
                        if (rec.getStepId() == null) {
                            LOG.warn("Received pipeline step record with null stepId for pipeline {} (key={}). Ignoring.",
                                    pipeline.getId(), key);
                            return;
                        }
                        execute(getStep(pipeline, rec), rec);
                    }
                }));
    }

    void execute(PipelineStep step, PipelineStepRecord stepRec) {
        System.err.println("XXX WORKER EXECUTE: " + stepRec);

        currentStep = step;
        if (step == null) {
            LOG.debug(
                    "No current step yet for pipeline {} on node {}. Waiting for coordinator to publish a step.",
                    pipeline.getId(), cluster.getLocalNode().getNodeName());
            return;
        }

        if (stepRec.getStatus() != PipelineStatus.RUNNING) {
            // Ignore terminal or transitional statuses; coordinator drives progression.
            return;
        }
        if (!step.isDistributed()) {
            // Coordinator runs non-distributed steps; worker stays silent.
            return;
        }
        LOG.info("Executing pipeline {} task {}.",
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
        String stepId = currentStep != null ? currentStep.getId() : pipeline.getSteps().firstKey();
        var rec = new PipelineStepRecord();
        rec.setPipelineId(pipeline.getId());
        rec.setStepId(stepId);
        rec.setStatus(status);
        rec.setUpdatedAt(System.currentTimeMillis());
        System.err.println(
                "XXX updating worker status: " + pipeWorkerKey + " -> " + rec);
        workerStatusCache.put(pipeWorkerKey, rec);
    }

    private PipelineStep getStep(Pipeline pipeline, PipelineStepRecord rec) {
        if (rec == null || rec.getStepId() == null) {
            return null;
        }
        return pipeline.getStep(rec.getStepId());
    }
}

//        if (!Keys.CURRENT_TASK.equals(key))
//            return;
//        PipelineStepRecord env = taskCache.get(key);
//        if (env == null || env.phase() != TaskPhase.PUBLISHED)
//            return;
//
//        TaskPayload t = env.payload();
//        try {
//            // Optionally: write a "RUNNING" hint somewhere if you want
//            executeTask(t); // <- your actual work
//            statusCache.put(Keys.statusKey(t.taskId(), nodeId), "OK");
//        } catch (Exception ex) {
//            statusCache.put(Keys.statusKey(t.taskId(), nodeId),
//                    "FAILED:" + ex.getClass().getSimpleName());
//        }

//    private final String nodeId;
//    private final Cache<String, TaskEnvelope> taskCache;
//    private final Cache<String, String> statusCache; // value: "OK" | "FAILED:<reason>"

//    public class TaskListener {
//        @CacheEntryCreated
//        @CacheEntryModified
//        public void
//                onTask(CacheEntryCreatedEvent<String, PipelineStepRecord> e) {
//            if (!e.isPre()) {
//                System.out.println("New task received: " + e.getKey());
//            }
//        }
//    }
//
//    public TaskListener(String nodeId, Cache<String, TaskEnvelope> taskCache, Cache<String, String> statusCache) {
//      this.nodeId = nodeId;
//      this.taskCache = taskCache;
//      this.statusCache = statusCache;
//    }
//
//    @CacheEntryCreated
//    public void onCreated(CacheEntryCreatedEvent<String, TaskEnvelope> e) {
//        if (!e.isPre())
//            react(e.getKey());
//    }
//
//    @CacheEntryModified
//    public void onModified(CacheEntryModifiedEvent<String, TaskEnvelope> e) {
//        if (!e.isPre())
//            react(e.getKey());
//    }
//
//    private void react(String key) {
//        if (!Keys.CURRENT_TASK.equals(key))
//            return;
//        TaskEnvelope env = taskCache.get(key);
//        if (env == null || env.phase() != TaskPhase.PUBLISHED)
//            return;
//
//        TaskPayload t = env.payload();
//        try {
//            // Optionally: write a "RUNNING" hint somewhere if you want
//            executeTask(t); // <- your actual work
//            statusCache.put(Keys.statusKey(t.taskId(), nodeId), "OK");
//        } catch (Exception ex) {
//            statusCache.put(Keys.statusKey(t.taskId(), nodeId),
//                    "FAILED:" + ex.getClass().getSimpleName());
//        }
//    }
//
//    private void executeTask(TaskPayload t) {
//        // Your worker logic here — deterministic, idempotent if possible
//    }
//
//    //
//    //    and register it on each worker:
//    //
//    //    cache.addListener(new TaskListener());
