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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.junit.ClusterNodesTest;
import com.norconex.crawler.core2.junit.ClusterTestUtil;
import com.norconex.crawler.core2.junit.WithTestWatcherLogging;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.stubs.CrawlSessionStubber;
import com.norconex.crawler.core2.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Timeout(60)
@WithTestWatcherLogging
@Slf4j
class PipelineTest {

    /*
     * Tests that a pipeline receiving a stop request will indicate to all
     * nodes they must stop and respond accordingly.
     */
    //    @ClusterNodesTest(nodes = { 1, 2 })
    void testStop(
            int nodeCount, List<CrawlSession> sessions) {
        //TODO implement me
    }

    /*
     * Tests that a late-joining node can pick up execution at the current
     * pipeline step.
     */
    @Test
    void testLateJoiningNode(@TempDir Path tempDir) {
        var cacheName = ClusterTestUtil.uniqueCacheName("pipetest-latejoin");

        var pipeline = new Pipeline("test-latejoin", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put("step1:" + TimeIdGenerator.next(), "byOneNode");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put("step2:" + TimeIdGenerator.next(), "byTwoNodes");
                    // Don't leave until second session has written something
                    // to prevent the pipeline from completing before
                    // the second session starts.
                    ConcurrentUtil.waitUntil(() -> cache.size() == 3,
                            Duration.ofSeconds(10), Duration.ofMillis(200));
                })));

        // --- Create and start first session/node ---
        CompletableFuture<PipelineResult> future1;
        Cache<String> cache;
        try (var session1 = CrawlSessionStubber
                .multiNodesCrawlSession(tempDir.resolve("node1"))) {
            cache = ClusterTestUtil.stringCache(session1, cacheName);
            future1 = session1.getCluster().getPipelineManager()
                    .executePipeline(pipeline, 0);

            // Wait for step1 to be completed by node1
            ClusterTestUtil.waitForCacheSize(cache, 2, Duration.ofSeconds(10));

            try (var session2 = CrawlSessionStubber
                    .multiNodesCrawlSession(tempDir.resolve("node2"))) {
                var future2 = session2.getCluster().getPipelineManager()
                        .executePipeline(pipeline, 0);

                // Wait for both to complete
                CompletableFuture.allOf(future1, future2).join();

                List<String> values = new ArrayList<>();
                cache.forEach((k, v) -> values.add(v));

                System.err.println("VALUES: " + String.join(", ", values));
                assertThat(values).containsExactlyInAnyOrder(
                        "byOneNode", "byTwoNodes", "byTwoNodes");
            }
        }
    }

    /*
     * Tests that a distributed pipeline step is run on all nodes
     * and a non-distributed step on a single node only.
     */
    @ClusterNodesTest(nodes = { 1, 2 })
    void testStepNodeDistribution(
            int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil.uniqueCacheName("pipetest-distrib");

        var pipeline = new Pipeline("test-distrib", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put("step1:" + sess
                            .getCluster()
                            .getLocalNode()
                            .getNodeName(), "distributed");
                }),
                PipelineTestUtil.nonDistributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put("step2:" + sess
                            .getCluster()
                            .getLocalNode()
                            .getNodeName(), "non-distributed");
                })));

        var results =
                PipelineTestUtil.executeOrderlyAndWait(pipeline, sessions);

        assertThat(results)
                .extracting(PipelineResult::getStatus)
                .containsOnly(PipelineStatus.COMPLETED);

        // Wait until all steps have executed and written to the shared cache
        var cache = ClusterTestUtil.stringCache(sessions.get(0), cacheName);
        var expectedDistributedCount = nodeCount == 1 ? 1 : 2;
        var expectedCacheSize = expectedDistributedCount + 1;

        ClusterTestUtil.waitForCacheSize(
                cache, expectedCacheSize, Duration.ofSeconds(20));

        var distributedCount = new AtomicInteger();
        var nonDistributedCount = new AtomicInteger();
        cache.forEach((k, v) -> {
            if ("distributed".equals(v)) {
                distributedCount.incrementAndGet();
            } else if ("non-distributed".equals(v)) {
                nonDistributedCount.incrementAndGet();
            }
        });

        assertThat(distributedCount.get())
                .as("Exactly %s distributed entry expected",
                        expectedDistributedCount)
                .isEqualTo(expectedDistributedCount);
        assertThat(nonDistributedCount.get())
                .as("Expected one non-coordinator(s)")
                .isEqualTo(1);
    }
}