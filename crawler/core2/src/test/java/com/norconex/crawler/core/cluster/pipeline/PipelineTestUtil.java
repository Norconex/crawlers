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
package com.norconex.crawler.core.cluster.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.norconex.crawler.core2.session.CrawlSession;

public final class PipelineTestUtil {
    private PipelineTestUtil() {
    }

    /**
     * In a multi-node setup, execute the pipeline on the worker nodes first,
     * and then the coordinator, to ensure all workers are listening for steps
     * from the coordinator. Use when you want to make sure you have
     * all your workers participating.
     * @param pipeline the pipeline to execute
     * @param sessions the multi-node sessions
     * @return results for each node
     */
    public static List<PipelineResult> executeOrderlyAndWait(
            Pipeline pipeline, List<CrawlSession> sessions) {
        // Start pipeline execution: launch workers on non-coordinator nodes
        // first
        var futures = new ArrayList<CompletableFuture<PipelineResult>>();
        sessions.stream()
                .filter(s -> !s.getCluster().getLocalNode().isCoordinator())
                .forEach(s -> futures.add(s.getCluster()
                        .getPipelineManager().executePipeline(pipeline)));
        // Then start coordinator so all workers are listening before first
        // step is published
        sessions.stream()
                .filter(s -> s.getCluster().getLocalNode().isCoordinator())
                .findFirst()
                .ifPresent(s -> futures.add(s.getCluster()
                        .getPipelineManager().executePipeline(pipeline)));

        // Wait for pipeline completion on all nodes
        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .join();

        // Collect results (all futures are completed at this point)
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }
}