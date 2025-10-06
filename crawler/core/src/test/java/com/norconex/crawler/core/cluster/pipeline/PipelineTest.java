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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.bag.HashBag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.junit.ClusterNodesTest;
import com.norconex.crawler.core.junit.ClusterTestUtil;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stubs.CrawlSessionStubber;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.extern.slf4j.Slf4j;

@Timeout(60)
@WithTestWatcherLogging
@Slf4j
class PipelineTest {

    //TODO test node expiry

    /*
     * Tests that a pipeline receiving a stop request will indicate to all
     * nodes they must stop and respond accordingly.
     */
    @Test
    void testStop(@TempDir Path tempDir) {
        var cacheName = ClusterTestUtil.uniqueCacheName("pipetest-stop");

        var pipeline = new Pipeline("test-stop", List.of(new BaseStep(
                "waiting-step") {
            @Override
            public void execute(CrawlSession sess) {
                var cache = ClusterTestUtil.stringCache(sess, cacheName);
                cache.put(PipelineTestUtil.nodeKey("node1Waiting", sess),
                        "true");
                assertThatNoException().isThrownBy(() -> {
                    PipelineTestUtil.waitUntilMedium(() -> {
                        if (isStopRequested()) {
                            LOG.info("Stop requested. Ending step.");
                            return true;
                        }
                        return false;
                    }, 12);
                });
            }
        }));

        Cache<String> cache;

        try (var session1 = CrawlSessionStubber
                .multiNodesCrawlSession(tempDir.resolve("node1"))) {
            cache = ClusterTestUtil.stringCache(session1, cacheName);
            session1.getCluster().getPipelineManager()
                    .executePipeline(pipeline);

            // Wait for step1 to be completed by node1
            ClusterTestUtil.waitForCacheSize(cache, 1, Duration.ofSeconds(10));
            LOG.info("Test awating step awaiting stop request...");

            try (var session2 = CrawlSessionStubber
                    .multiNodesCrawlSession(tempDir.resolve("node2"))) {
                session2.getCluster().stop();
            }
        }
    }

    /*
     * Test to assert coordinator takeover without closing a node
     * from inside a step callback. We start workers first, then the coordinator,
     * wait until step2 (non-distributed) is observed, then close the current
     * coordinator. Remaining nodes must complete step3/step4.
     */
    @ClusterNodesTest(nodes = 3, infinispanNodeExpiryTimeout = 3000)
    void testCoordinatorSwitch(int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil
                .uniqueCacheName("pipetest-coord-switch");

        var firstCoordinatorName = new AtomicReference<String>();
        var observed = new ConcurrentHashMap<String, String>();

        var pipeline = new Pipeline("test-coord-switch", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    var key = PipelineTestUtil.nodeKey("step1", sess);
                    cache.put(key, "ok");
                    observed.put(key, "ok");
                }),
                PipelineTestUtil.nonDistributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    var key = PipelineTestUtil.nodeKey("step2", sess);
                    cache.put(key, "ok");
                    observed.put(key, "ok");
                    if (PipelineTestUtil.isCoord(sess)) {
                        firstCoordinatorName
                                .set(PipelineTestUtil.nodeName(sess));
                    }
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    var key = PipelineTestUtil.nodeKey("step3", sess);
                    cache.put(key, "ok");
                    observed.put(key, "ok");
                }),
                PipelineTestUtil.distributedStep("step4", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    var key = PipelineTestUtil.nodeKey("step4", sess);
                    cache.put(key, "ok");
                    observed.put(key, "ok");
                })));

        // Launch workers first, then coordinator, and keep their futures
        var futures = new ArrayList<CompletableFuture<PipelineResult>>();
        sessions.stream()
                .filter(s -> !s.getCluster().getLocalNode().isCoordinator())
                .forEach(s -> futures.add(
                        s.getCluster().getPipelineManager()
                                .executePipeline(pipeline)));
        sessions.stream()
                .filter(s -> s.getCluster().getLocalNode().isCoordinator())
                .findFirst()
                .ifPresent(s -> futures.add(
                        s.getCluster().getPipelineManager()
                                .executePipeline(pipeline)));

        // Pre-warm caches to minimize initial latency
        PipelineTestUtil.prewarmStringCache(sessions, cacheName);
        PipelineTestUtil.briefWarmup(250);

        // Wait until step1 done on all nodes and step2 done by current coordinator
        assertThatNoException().isThrownBy(() -> {
            PipelineTestUtil.waitUntilMedium(
                    () -> countWithPrefix(observed, "step1:") == nodeCount
                            && countWithPrefix(observed, "step2:") == 1,
                    12);
        });

        // Close the current coordinator outside of any worker step
        sessions.stream()
                .filter(s -> s.getCluster().getLocalNode().isCoordinator())
                .findFirst()
                .ifPresent(sess -> ExceptionSwallower
                        .runWithInterruptClear(sess::close));

        // The remaining nodes should complete step3 and step4
        assertThatNoException().isThrownBy(() -> {
            PipelineTestUtil.waitUntilMedium(
                    () -> countWithPrefix(observed, "step3:") >= nodeCount - 1
                            && countWithPrefix(observed,
                                    "step4:") >= nodeCount - 1,
                    15);
        });

        // Ensure step2 was performed by the original coordinator
        var originalCoord = firstCoordinatorName.get();
        assertThat(originalCoord).isNotBlank();
        assertThat(observed.get("step2:" + originalCoord)).isEqualTo("ok");

        // Takeover proven by counts: remaining nodes finished step3 and step4
        assertThat(countWithPrefix(observed, "step3:"))
                .isGreaterThanOrEqualTo(nodeCount - 1);
        assertThat(countWithPrefix(observed, "step4:"))
                .isGreaterThanOrEqualTo(nodeCount - 1);
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
                    cache.put(PipelineTestUtil.nodeKey("step1", sess),
                            "byStep1");
                    completedSteps.add("step1");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    throw new PipelineException("I am a fake failure");
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(PipelineTestUtil.nodeKey("step3", sess),
                            "byStep3");
                    completedSteps.add("step3");
                })));

        // Pre-warm caches
        PipelineTestUtil.prewarmStringCache(sessions, cacheName);

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
                    cache.put(PipelineTestUtil.nodeKey("step1", sess),
                            "byStep1");
                    completedSteps.add("step1");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    if (nodeCount == 2) {
                        // Wait until both nodes have reached step 2.
                        ClusterTestUtil.waitForCacheSize(cache, 2,
                                Duration.ofSeconds(10));
                    }
                    if (cnt.getAndIncrement() == 0) {
                        throw new PipelineException("I am a fake failure");
                    }
                    cache.put(PipelineTestUtil.nodeKey("step2", sess),
                            "byStep2");
                    completedSteps.add("step2");
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(PipelineTestUtil.nodeKey("step3", sess),
                            "byStep3");
                    completedSteps.add("step3");
                })));

        // Pre-warm caches
        PipelineTestUtil.prewarmStringCache(sessions, cacheName);

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
                    cache.put(PipelineTestUtil.nodeKey("step1", sess),
                            "byStep1");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(PipelineTestUtil.nodeKey("step2", sess),
                            "byStep2");
                }),
                PipelineTestUtil.distributedStep("step3", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(PipelineTestUtil.nodeKey("step3", sess),
                            "byStep3");
                })));

        // Pre-warm caches
        PipelineTestUtil.prewarmStringCache(sessions, cacheName);

        var results = PipelineTestUtil.executeAndWait(pipeline, sessions);

        assertThat(results)
                .extracting(PipelineResult::getStatus)
                .containsOnly(PipelineStatus.COMPLETED);
        assertThat(results)
                .extracting(PipelineResult::getLastStepId)
                .containsOnly("step3");

        assertThat(results)
                .extracting("startedAt", "finishedAt")
                .allMatch(tuple -> (long) tuple.toArray()[1] > (long) tuple
                        .toArray()[0]);
    }

    @Test
    void testLateJoiningNode(@TempDir Path tempDir) {
        var cacheName = ClusterTestUtil.uniqueCacheName("pipetest-latejoin");

        var pipeline = new Pipeline("test-latejoin", List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(PipelineTestUtil.nodeKey("step1", sess), "ok");
                }),
                PipelineTestUtil.distributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put(PipelineTestUtil.nodeKey("step2", sess), "ok");
                    // don't leave until the other node joined
                    ClusterTestUtil.waitForCacheSize(cache, 3,
                            Duration.ofSeconds(10));
                })));

        final List<String> step2Nodes = new ArrayList<>();

        try (var s1 = CrawlSessionStubber
                .multiNodesCrawlSession(tempDir.resolve("node1"))) {

            var c1 = ClusterTestUtil.stringCache(s1, cacheName);
            s1.getCluster().getPipelineManager().executePipeline(pipeline);

            // wait until step 2 is reached before adding node 2
            ClusterTestUtil.waitForCacheSize(c1, 2, Duration.ofSeconds(10));

            LOG.info("Launching second node.");
            try (var s2 = CrawlSessionStubber
                    .multiNodesCrawlSession(tempDir.resolve("node2"))) {
                s2.getCluster().getPipelineManager().executePipeline(pipeline);
                var c2 = ClusterTestUtil.stringCache(s2, cacheName);
                // wait until second node added its entry
                ClusterTestUtil.waitForCacheSize(c2, 3, Duration.ofSeconds(10));
            }

            c1.forEach((k, v) -> {
                if (k.startsWith("step2:")) {
                    step2Nodes.add(k.substring("step2:".length()));
                }
            });
        }

        assertThat(step2Nodes)
                .hasSize(2)
                .doesNotContainNull()
                .doesNotContain("");
    }

    /*
     * Tests that a distributed pipeline step is run on all nodes
     * and a non-distributed step on a single node only.
     */
    @ClusterNodesTest(nodes = { 1, 2 })
    void testStepNodeDistribution(
            int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil.uniqueCacheName("pipetest-distrib");
        var pipeId = ClusterTestUtil.uniqueCacheName("test-distrib");

        var pipeline = new Pipeline(pipeId, List.of(
                PipelineTestUtil.distributedStep("step1", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put("step1:" + PipelineTestUtil.nodeName(sess),
                            "distributed");
                    var expectedKeys = sess.getCluster().getNodeNames().stream()
                            .map(n -> "step1:" + n)
                            .toList();
                    assertThatNoException().isThrownBy(() -> {
                        PipelineTestUtil.waitUntilMedium(() -> {
                            var seen = 0;
                            for (var k : expectedKeys) {
                                var v = cache.get(k).orElse(null);
                                if (v != null) {
                                    seen++;
                                }
                            }
                            return seen == expectedKeys.size();
                        }, 15);
                    });
                }),
                PipelineTestUtil.nonDistributedStep("step2", sess -> {
                    var cache = ClusterTestUtil.stringCache(sess, cacheName);
                    cache.put("step2:" + PipelineTestUtil.nodeName(sess),
                            "non-distributed");
                })));

        var futures = new ArrayList<CompletableFuture<PipelineResult>>();

        // Ensure cluster view shows all nodes
        assertThatNoException().isThrownBy(() -> {
            ClusterTestUtil.waitForClusterSize(sessions, nodeCount,
                    Duration.ofSeconds(10));
        });

        // Pre-warm the test cache on all nodes to avoid rebalance during step publication
        PipelineTestUtil.prewarmStringCache(sessions, cacheName);
        PipelineTestUtil.briefWarmup(750);

        // Start non-coordinator workers first so listeners are attached
        sessions.stream()
                .filter(s -> !PipelineTestUtil.isCoord(s))
                .forEach(s -> futures.add(
                        s.getCluster().getPipelineManager()
                                .executePipeline(pipeline)));
        // Brief warm-up
        PipelineTestUtil.briefWarmup(250);

        // Start coordinator last so it publishes step1 with all workers listening
        sessions.stream()
                .filter(PipelineTestUtil::isCoord)
                .findFirst()
                .ifPresent(s -> futures.add(
                        s.getCluster().getPipelineManager()
                                .executePipeline(pipeline)));

        CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .join();

        var coordSess = sessions.stream()
                .filter(PipelineTestUtil::isCoord)
                .findFirst()
                .orElse(sessions.get(0));
        var coordName = PipelineTestUtil.nodeName(coordSess);
        var expectedDistributedCount = nodeCount == 1 ? 1 : 2;

        assertThatNoException().isThrownBy(() -> {
            PipelineTestUtil.waitUntilMedium(() -> {
                var dist = 0;
                for (var s : sessions) {
                    var localCache = ClusterTestUtil.stringCache(s, cacheName);
                    var v = localCache
                            .get("step1:" + PipelineTestUtil.nodeName(s))
                            .orElse(null);
                    if ("distributed".equals(v)) {
                        dist++;
                    }
                }
                var nonDist = ClusterTestUtil
                        .stringCache(coordSess, cacheName)
                        .get("step2:" + coordName)
                        .orElse(null);
                return dist == expectedDistributedCount
                        && "non-distributed".equals(nonDist);
            }, 20);
        });

        var finalDist = 0;
        for (var s : sessions) {
            var localCache = ClusterTestUtil.stringCache(s, cacheName);
            var v = localCache.get("step1:" + PipelineTestUtil.nodeName(s))
                    .orElse(null);
            if ("distributed".equals(v)) {
                finalDist++;
            }
        }
        var finalStep2 = ClusterTestUtil
                .stringCache(coordSess, cacheName)
                .get("step2:" + coordName)
                .orElse(null);

        assertThat(finalDist)
                .as("Exactly %s distributed entry expected",
                        expectedDistributedCount)
                .isEqualTo(expectedDistributedCount);
        assertThat(finalStep2)
                .as("Expected one non-distributed entry on coordinator")
                .isEqualTo("non-distributed");
    }

    // --- test helpers --------------------------------------------------------

    private static int countWithPrefix(ConcurrentMap<String, String> map,
            String prefix) {
        var cnt = new AtomicInteger();
        map.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                cnt.incrementAndGet();
            }
        });
        return cnt.get();
    }

}
