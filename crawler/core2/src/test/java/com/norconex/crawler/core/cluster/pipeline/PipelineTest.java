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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core2.junit.ClusterNodesTest;
import com.norconex.crawler.core2.junit.ClusterTestUtil;
import com.norconex.crawler.core2.junit.WithTestWatcherLogging;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Timeout(60)
@WithTestWatcherLogging
@Slf4j
public class PipelineTest {

    @ClusterNodesTest(nodes = { 1, 2 })
    void testSingleAndMultiNodesPipelineStep(
            int nodeCount, List<CrawlSession> sessions) {
        var cacheName = ClusterTestUtil.uniqueCacheName("pipetest");

        var pipeline = new Pipeline("test-pipeline", List.of(
                new BaseStep("step1") {
                    @Override
                    public void execute(CrawlSession sess) {
                        System.err.println("XXX IN STEP 1");
                        var cache =
                                ClusterTestUtil.stringCache(sess, cacheName);
                        cache.put("step1:" + sess
                                .getCluster()
                                .getLocalNode()
                                .getNodeName(), "distributed");
                    }
                }.setDistributed(true),
                new BaseStep("step2") {
                    @Override
                    public void execute(CrawlSession sess) {
                        System.err.println("XXX IN STEP 2");
                        var cache =
                                ClusterTestUtil.stringCache(sess, cacheName);
                        cache.put("step2:" + sess
                                .getCluster()
                                .getLocalNode()
                                .getNodeName(), "non-distributed");
                    }
                }));

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