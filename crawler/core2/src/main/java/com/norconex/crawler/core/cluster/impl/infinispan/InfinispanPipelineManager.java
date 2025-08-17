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

import java.util.concurrent.CompletableFuture;

import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.util.ConcurrentUtil;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InfinispanPipelineManager implements PipelineManager {

    //TODO make configurable
    private static long TIMEOUT_MS = 60_000;

    //    private final PipelineRegistry registry =
    //            new PipelineRegistry();
    //    private final Map<String, Pipeline> nodePipelines = new HashMap<>();
    private final InfinispanCluster cluster;

    //TODO if I am coordinator, manage/run the pipeline.
    // else, keep it aside and listen for which current step to process,
    // and pull the right one from the pipeline we just got.

    @Override
    public CompletableFuture<Void> executePipeline(@NonNull Pipeline pipeline) {
        boolean isCoordinator = cluster.getLocalNode().isCoordinator();
        var future = new CompletableFuture<Void>();

        // Start coordinator (async) only on coordinator node
        if (isCoordinator) {
            LOG.debug("Starting pipeline coordinator for {} on node {}", pipeline.getId(), cluster.getLocalNode().getNodeName());
            new Thread(() -> new PipelineCoordinator(cluster, pipeline).start(),
                    "PIPE-COORD-" + cluster.getLocalNode().getNodeName()).start();
        }

        // Start worker (async) on every node
        LOG.debug("Starting pipeline worker for {} on node {}", pipeline.getId(), cluster.getLocalNode().getNodeName());
        new Thread(() -> new PipelineWorker(cluster, pipeline).start(),
                "PIPE-WORKER-" + cluster.getLocalNode().getNodeName()).start();

        // Completion logic
        new Thread(() -> {
            try {
                ConcurrentUtil.waitUntil(() -> isPipelineDone(pipeline));
                future.complete(null);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        }, (isCoordinator ? "PIPE-WAIT-COORD-" : "PIPE-WAIT-") + cluster.getLocalNode().getNodeName()).start();

        return future;
    }

    private boolean isPipelineDone(Pipeline pipeline) {
        var pipelineCache = cluster
                .getCacheManager()
                .getPipelineCurrentStepCache();
        var key = CacheKeys.pipelineKey(cluster, pipeline);
        return pipelineCache
                .get(key)
                .filter(rec -> {
                    if (rec.getStatus().isTerminal()) {
                        LOG.info("Pipeline terminated with status: {}",
                                rec.getStatus());
                        return true;
                    }
                    if (System.currentTimeMillis()
                            - rec.getUpdatedAt() > TIMEOUT_MS) {
                        LOG.info("Pipeline timed out, terminating.");
                        return true;
                    }
                    return false;
                })
                .isPresent();

    }
}