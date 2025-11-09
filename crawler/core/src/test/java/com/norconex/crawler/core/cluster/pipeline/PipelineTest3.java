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

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core._DELETE.ClusterTestUtil;
import com.norconex.crawler.core._DELETE.crawler.ClusteredCrawlContext;
import com.norconex.crawler.core._DELETE.tomerge_or_delete.SharedClusteredCrawl;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Example test class demonstrating the use of {@link SharedClusteredCrawl}
 * annotation. The cluster is created once before all tests and reused,
 * significantly reducing test execution time.
 */
@Timeout(60)
@WithTestWatcherLogging
@SharedClusteredCrawl(
    driverSupplierClass = PipelineTest3.TestCompletedPipelineResultDriver.class,
    nodes = 2,
    threads = 2
)
@Slf4j
class PipelineTest3 {

    /**
     * Driver that creates a simple 3-step pipeline for testing.
     */
    public static class TestCompletedPipelineResultDriver
            implements Supplier<CrawlDriver> {
        @Override
        public CrawlDriver get() {
            var cacheName = ClusterTestUtil.uniqueCacheName(
                    "pipetest-completion");

            var pipeline = new Pipeline("test-completion", List.of(
                    PipelineTestUtil.distributedStep("step1", sess -> {
                        var cache =
                                ClusterTestUtil.stringCache(sess, cacheName);
                        cache.put(PipelineTestUtil.nodeKey("step1", sess),
                                "byStep1");
                    }),
                    PipelineTestUtil.distributedStep("step2", sess -> {
                        var cache =
                                ClusterTestUtil.stringCache(sess, cacheName);
                        cache.put(PipelineTestUtil.nodeKey("step2", sess),
                                "byStep2");
                    }),
                    PipelineTestUtil.distributedStep("step3", sess -> {
                        var cache =
                                ClusterTestUtil.stringCache(sess, cacheName);
                        cache.put(PipelineTestUtil.nodeKey("step3", sess),
                                "byStep3");
                    })));

            return TestCrawlDriverFactory.builder()
                    .crawlPipelineFactory(session -> pipeline)
                    .build();
        }
    }

    /**
     * Test that the pipeline completed successfully.
     * The cluster output is injected automatically.
     */
    @Test
    @Timeout(30) // Only test logic timeout, NOT cluster startup!
    void testCompletedPipelineResult(ClusteredCrawlContext context) {
        var pipeResult = context.getOuput().getPipeResult();

        assertThat(pipeResult).isNotNull();
        assertThat(pipeResult.getStatus()).isSameAs(PipelineStatus.COMPLETED);
        assertThat(pipeResult.getStepId()).isEqualTo("step3");
        // Note: timestamp is from cluster execution in @BeforeAll, not from this test
        assertThat(pipeResult.getUpdatedAt()).isGreaterThan(0L);
    }

    /**
     * Another test using the same cluster - runs MUCH faster
     * since cluster is already started!
     */
    @Test
    @Timeout(10) // Very short timeout - no cluster startup overhead
    void testPipelineStatus(ClusteredCrawlContext context) {
        var pipeResult = context.getOuput().getPipeResult();

        assertThat(pipeResult).isNotNull();
        assertThat(pipeResult.getStatus()).isSameAs(PipelineStatus.COMPLETED);
    }

    /**
     * Yet another test - demonstrates cluster reuse benefits.
     */
    @Test
    @Timeout(10)
    void testPipelineStepId(ClusteredCrawlContext context) {
        var pipeResult = context.getOuput().getPipeResult();

        assertThat(pipeResult).isNotNull();
        assertThat(pipeResult.getStepId()).isEqualTo("step3");
    }
}
