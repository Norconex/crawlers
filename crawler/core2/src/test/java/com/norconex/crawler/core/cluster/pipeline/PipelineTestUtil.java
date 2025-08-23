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
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core2.session.CrawlSession;

public final class PipelineTestUtil {
    private PipelineTestUtil() {
    }

    public static Step nonDistributedStep(
            String stepId, Consumer<CrawlSession> executor) {
        return new BaseStep(stepId) {
            @Override
            public void execute(CrawlSession session) {
                executor.accept(session);
            }
        };
    }

    public static Step distributedStep(
            String stepId, Consumer<CrawlSession> executor) {
        return ((BaseStep) nonDistributedStep(stepId, executor))
                .setDistributed(true);
    }

    /**
     * In a multi-node setup, execute the pipeline on different nodes
     * waiting for the increment provider value to change between each
     * (starting at zero).
     * @param pipeline the pipeline to execute
     * @param sessions the multi-node sessions
     * @param incrementSupplier function returning an incremented value
     *     (or the same if no change)
     * @return results for each node
     */
    public static List<PipelineResult> executeAtIncrementAndWait(
            Pipeline pipeline,
            List<CrawlSession> sessions,
            Supplier<Integer> incrementSupplier) {
        // Start pipeline execution at interval
        var futures = new ArrayList<CompletableFuture<PipelineResult>>();
        var prevValue = -1;
        for (var s : sessions) {
            var newValue = 0;
            while ((newValue = incrementSupplier.get()) == prevValue) {
                Sleeper.sleepMillis(500);
            }
            prevValue = newValue;
            futures.add(s.getCluster()
                    .getPipelineManager().executePipeline(pipeline, 0));
        }

        return waitAndGetAll(futures);
    }

    /**
     * In a multi-node setup, execute the pipeline on different nodes
     * at interval between each. Can be used to simulate late-joining scenarios.
     * This method does not assume pipeline completion status before
     * other nodes join. This is for implementors to handle.
     * @param pipeline the pipeline to execute
     * @param sessions the multi-node sessions
     * @param intervalMs interval between each node pipeline executions
     * @return results for each node
     */
    public static List<PipelineResult> executeAtIntervalAndWait(
            Pipeline pipeline, List<CrawlSession> sessions, long intervalMs) {
        // Start pipeline execution at interval
        var futures = new ArrayList<CompletableFuture<PipelineResult>>();
        var applyInterval = false;
        for (var s : sessions) {
            if (applyInterval) {
                Sleeper.sleepMillis(intervalMs);
            }
            applyInterval = true;
            futures.add(s.getCluster()
                    .getPipelineManager().executePipeline(pipeline, 0));
        }

        return waitAndGetAll(futures);
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
                        .getPipelineManager().executePipeline(pipeline, 0)));
        // Then start coordinator so all workers are listening before first
        // step is published
        sessions.stream()
                .filter(s -> s.getCluster().getLocalNode().isCoordinator())
                .findFirst()
                .ifPresent(s -> futures.add(s.getCluster()
                        .getPipelineManager().executePipeline(pipeline, 0)));

        return waitAndGetAll(futures);
    }

    /**
     * Execute all nodes, with no guarantee of coordinator being first.
     * @param pipeline the pipeline to execute
     * @param sessions the multi-node sessions
     * @return results for each node
     */
    public static List<PipelineResult> executeAndWait(
            Pipeline pipeline, List<CrawlSession> sessions) {
        var futures = new ArrayList<CompletableFuture<PipelineResult>>();
        sessions.stream()
                .forEach(s -> futures.add(s.getCluster()
                        .getPipelineManager().executePipeline(pipeline, 0)));
        return waitAndGetAll(futures);
    }

    public static List<PipelineResult> waitAndGetAll(
            List<CompletableFuture<PipelineResult>> futures) {

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
