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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core2.cluster.Cache;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCacheManager;
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

    //TODO test node expiry and seemless coordinator change detection

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
     * Tests that a coordinator leaving the cluster will have another node
     * promoted coordinator and finish the pipeline.
     * (matches owners=2 + 1 from infinispan config)
     */
    @ClusterNodesTest(nodes = 3, infinispanNodeExpiryTimeout = 5000)
    void testCoordinatorSwitch(int nodeCount, List<CrawlSession> sessions) {
        var cacheName =
                ClusterTestUtil.uniqueCacheName("pipetest-coord-switch")
                        + "_replicated"; // matches infinispan config
        var firstCoordinatorName = new AtomicReference<>();
        var isOneNodeDown = new AtomicBoolean();

        // step1 should be done only by node A and step 4 by node B.
        var pipeline = new Pipeline("test-coord-switch", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step1", sess), "step1-coord-"
                            + sess.getCluster().getLocalNode().isCoordinator());
                }),
                PipelineTestUtil.nonDistributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step2", sess), "step2-coord-"
                            + sess.getCluster().getLocalNode().isCoordinator());
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    if (isCoord(sess)) {
                        firstCoordinatorName.set(nodeName(sess));
                        //                    } else {
                        //                        secondCoordinatorName.set(nodeName(sess));
                    }

                    // Wait that both nodes have written step1 then
                    // fail the coordinator.
                    ConcurrentUtil.waitUntil(() -> cache.size() == 4,
                            Duration.ofSeconds(10), Duration.ofMillis(200));
                    if (isCoord(sess) && !isOneNodeDown.getAndSet(true)) {
                        try {
                            LOG.info("Test closing prematurely to "
                                    + "simulate node leaving.");
                            ((InfinispanCacheManager) sess.getCluster()
                                    .getCacheManager()).vendor().close();
                            //                            cacheManager.getTransport().stop();
                            //                            sess.close();
                        } catch (Exception e) {
                            LOG.error("Error while closing session.", e);
                        }
                    } else {
                        cache.put(nodeKey("step3", sess), "step3-coord-"
                                + sess.getCluster().getLocalNode()
                                        .isCoordinator());
                    }
                }),
                PipelineTestUtil.distributedStep("step4", sess -> {
                    System.err.println("XXX STEP 4");
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step4", sess), "step4-coord-"
                            + sess.getCluster().getLocalNode().isCoordinator());
                    //                    ConcurrentUtil.waitUntil(() -> cache.size() == 8,
                    //                            Duration.ofSeconds(10), Duration.ofMillis(200));
                })));

        var results = PipelineTestUtil.executeAndWait(pipeline, sessions);

        var cache = ClusterTestUtil.stringCache(sessions.stream()
                .filter(sess -> !sess.getCluster().getLocalNode().getNodeName()
                        .equals(firstCoordinatorName.get()))
                .findFirst().get(), cacheName);

        assertThat(results)
                .extracting(PipelineResult::getStatus)
                .containsOnly(PipelineStatus.COMPLETED,
                        PipelineStatus.FAILED);

        var completedSteps = new HashBag<String>();
        cache.forEach((k, v) -> {
            System.err.println("XXX in cache: " + k);
            var stepId = StringUtils.substringBefore(k, ":");
            completedSteps.add(stepId);
        });

        // should step3 be reattempted in case its load needs to be
        // reprocessed, hence expecting 2 entries for "step3"?
        assertThat(completedSteps).containsExactlyInAnyOrder(
                "step1", "step1", "step1", "step2", "step3", "step3", "step4",
                "step4");

        // step 2 and 4 should be by different nodes but each being coordinator
        assertThat(cache.get("step2:" + firstCoordinatorName).get())
                .endsWith("coord-true");
    }

    /*
     * Test a FAILED pipeline when all nodes failed.
     */
    @ClusterNodesTest(nodes = { 1, 2 })
    void testAllFailedPipelineResult(
            int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil.uniqueCacheName(
                "pipetest-all-fail");
        var completedSteps = new HashBag<>();
        var pipeline = new Pipeline("test-all-fail", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step1", sess), "byStep1");
                    completedSteps.add("step1");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    throw new PipelineException("I am a fake failure");
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step3", sess), "byStep3");
                    completedSteps.add("step3");
                })));

        var results =
                PipelineTestUtil.executeOrderlyAndWait(pipeline, sessions);

        assertThat(completedSteps.getCount("step1")).isEqualTo(nodeCount);
        assertThat(completedSteps.getCount("step2")).isZero();
        assertThat(completedSteps.getCount("step3")).isZero();

        assertThat(results)
                .extracting(PipelineResult::getStatus)
                .containsOnly(PipelineStatus.FAILED);
        assertThat(results)
                .extracting(PipelineResult::getLastStepId)
                .containsOnly("step2");
        assertThat(results)
                .extracting(PipelineResult::isTimedOut)
                .containsOnly(false);
        assertThat(results)
                .extracting("startedAt", "finishedAt")
                .allMatch(tuple -> (long) tuple.toArray()[1] > (long) tuple
                        .toArray()[0]);
    }

    /*
     * Test a COMPLETED pipeline when only 1 node failed out of 2,
     * or FAILED if a single node.
     */
    @ClusterNodesTest(nodes = { 1, 2 })
    void testPartiallyFailedPipelineResult(
            int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil.uniqueCacheName(
                "pipetest-partial-fail");

        var cnt = new AtomicInteger();
        var completedSteps = new HashBag<String>();

        var pipeline = new Pipeline("test-partial-fail", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step1", sess), "byStep1");
                    completedSteps.add("step1");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    if (cnt.getAndIncrement() == 0) {
                        throw new PipelineException("I am a fake failure");
                    }
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step2", sess), "byStep2");
                    completedSteps.add("step2");
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step3", sess), "byStep3");
                    completedSteps.add("step3");
                })));

        var results =
                PipelineTestUtil.executeOrderlyAndWait(pipeline, sessions);

        if (nodeCount == 1) {
            assertThat(completedSteps.getCount("step1")).isEqualTo(1);
            assertThat(completedSteps.getCount("step2")).isZero();
            assertThat(completedSteps.getCount("step3")).isZero();
        } else {
            assertThat(completedSteps.getCount("step1")).isEqualTo(nodeCount);
            assertThat(completedSteps.getCount("step2"))
                    .isEqualTo(nodeCount - 1);
            assertThat(completedSteps.getCount("step3")).isEqualTo(nodeCount);
        }

        assertThat(results)
                .extracting(PipelineResult::getStatus)
                .containsOnly(nodeCount > 1 ? PipelineStatus.COMPLETED
                        : PipelineStatus.FAILED);
        assertThat(results)
                .extracting(PipelineResult::getLastStepId)
                .containsOnly(nodeCount > 1 ? "step3" : "step2");
        assertThat(results)
                .extracting(PipelineResult::isTimedOut)
                .containsOnly(false);

        assertThat(results)
                .extracting("startedAt", "finishedAt")
                .allMatch(tuple -> (long) tuple.toArray()[1] > (long) tuple
                        .toArray()[0]);
    }

    /*
     * Test all steps are executed without errors and the results show it.
     */
    @ClusterNodesTest(nodes = { 1, 2 })
    void testCompletedPipelineResult(
            int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil.uniqueCacheName(
                "pipetest-completion");

        var pipeline = new Pipeline("test-completion", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step1", sess), "byStep1");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step2", sess), "byStep2");
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step3", sess), "byStep3");
                })));

        var results = PipelineTestUtil.executeAndWait(pipeline, sessions);

        assertThat(results)
                .extracting(PipelineResult::getStatus)
                .containsOnly(PipelineStatus.COMPLETED);
        assertThat(results)
                .extracting(PipelineResult::getLastStepId)
                .containsOnly("step3");
        assertThat(results)
                .extracting(PipelineResult::isTimedOut)
                .containsOnly(false);

        assertThat(results)
                .extracting("startedAt", "finishedAt")
                .allMatch(tuple -> (long) tuple.toArray()[1] > (long) tuple
                        .toArray()[0]);
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
                    cache.put(nodeKey("step1", sess), "byOneNode");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(nodeKey("step2", sess), "byTwoNodes");
                    // Don't leave until second session has written something
                    // to prevent the pipeline from completing before
                    // the second session starts.
                    ConcurrentUtil.waitUntil(() -> cache.size() == 3,
                            Duration.ofSeconds(10), Duration.ofMillis(200));
                })));

        CompletableFuture<PipelineResult> future1;
        CompletableFuture<PipelineResult> future2;
        Cache<String> cache1;
        List<String> values = new ArrayList<>();

        try (var session1 = CrawlSessionStubber
                .multiNodesCrawlSession(tempDir.resolve("node1"))) {
            cache1 = ClusterTestUtil.stringCache(session1, cacheName);
            future1 = session1.getCluster().getPipelineManager()
                    .executePipeline(pipeline, 0);

            // Wait for step1 to be completed by node1
            ClusterTestUtil.waitForCacheSize(cache1, 2, Duration.ofSeconds(10));

            try (var session2 = CrawlSessionStubber
                    .multiNodesCrawlSession(tempDir.resolve("node2"))) {
                future2 = session2.getCluster().getPipelineManager()
                        .executePipeline(pipeline, 0);
                var cache2 = ClusterTestUtil.stringCache(session2, cacheName);

                ClusterTestUtil.waitForCacheSize(cache2, 3,
                        Duration.ofSeconds(20));

                // Wait for both to complete before any session closes
                CompletableFuture.allOf(future1, future2).join();

                // Adding values here to the List since we don't replicate
                // the nodes for testing, the moment session1 closes, the count
                // will go down due to having lost session 2 records
                cache2.forEach((k, v) -> values.add(v));
            }
        }
        assertThat(values).containsExactlyInAnyOrder(
                "byOneNode", "byTwoNodes", "byTwoNodes");
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

    private static String nodeKey(String prefix, CrawlSession session) {
        return prefix + ":" + nodeName(session);

    }

    private static String nodeName(CrawlSession session) {
        return session
                .getCluster()
                .getLocalNode()
                .getNodeName();

    }

    private static boolean isCoord(CrawlSession session) {
        return session
                .getCluster()
                .getLocalNode()
                .isCoordinator();

    }
}
