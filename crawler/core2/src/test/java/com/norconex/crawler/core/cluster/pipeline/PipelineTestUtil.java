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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.junit.ClusterTestUtil;
import com.norconex.crawler.core2.util.ConcurrentUtil;

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

    // --- New test helpers ----------------------------------------------------

    public static void prewarmStringCache(
            CrawlSession session, String cacheName) {
        var cache = ClusterTestUtil.stringCache(session, cacheName);
        cache.put("__warmup__", "1");
        cache.remove("__warmup__");
    }

    public static void prewarmStringCache(
            List<CrawlSession> sessions, String cacheName) {
        sessions.forEach(s -> prewarmStringCache(s, cacheName));
    }

    public static void briefWarmup(long millis) {
        Sleeper.sleepMillis(millis);
    }

    public static void waitUntilFast(
            BooleanSupplier condition, long timeoutSeconds) {
        ConcurrentUtil.waitUntil(condition,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofMillis(100));
    }

    public static void waitUntilMedium(
            BooleanSupplier condition, long timeoutSeconds) {
        ConcurrentUtil.waitUntil(condition,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofMillis(150));
    }

    public static long countCacheKeysWithPrefix(Cache<String> cache,
            String prefix) {
        final long[] cnt = { 0 };
        cache.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                cnt[0]++;
            }
        });
        return cnt[0];
    }

    public static String nodeName(CrawlSession session) {
        return session.getCluster().getLocalNode().getNodeName();
    }

    public static String nodeKey(String prefix, CrawlSession session) {
        return prefix + ":" + nodeName(session);
    }

    public static boolean isCoord(CrawlSession session) {
        return session.getCluster().getLocalNode().isCoordinator();
    }

    /**
     * In a multi-node setup, execute the pipeline on different nodes
     * at interval between each. Can be used to simulate late-joining scenarios.
     * This method does not assume pipeline completion status before
     * other nodes join. This is for implementors to handle.
     * @param pipeline the pipeline to execute
     * @param sessions the multi-node sessions
     * @param incrementSupplier supplies the increment to use
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
                    .getPipelineManager().executePipeline(pipeline));
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
                    .getPipelineManager().executePipeline(pipeline));
        }

        return waitAndGetAll(futures);
    }

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

        return waitAndGetAll(futures);
    }

    public static List<PipelineResult> executeAndWait(
            Pipeline pipeline, List<CrawlSession> sessions) {
        var futures = new ArrayList<CompletableFuture<PipelineResult>>();
        sessions.stream()
                .forEach(s -> futures.add(s.getCluster()
                        .getPipelineManager().executePipeline(pipeline)));
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