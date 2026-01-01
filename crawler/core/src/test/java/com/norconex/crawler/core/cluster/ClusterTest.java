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
package com.norconex.crawler.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.test.CrawlTestHarness;
import com.norconex.crawler.core.test.CrawlTestInstrument;
import com.norconex.crawler.core.test.CrawlTestNodeOutput;
import com.norconex.crawler.core.test.TestCrawler;

import lombok.extern.slf4j.Slf4j;

//@SlowTest
// Run single node in JVM
@Slf4j
@WithTestWatcherLogging
class ClusterTest {
    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = { 1, 2 })
    @Timeout(120)
    void testNormalExecution(int numNodes) throws IOException {
        var numOfRefs = 10;

        var nodes = new String[numNodes];
        for (var i = 0; i < nodes.length; i++) {
            nodes[i] = "node-" + (i + 1);
        }

        try (var harness = new CrawlTestHarness(
                new CrawlTestInstrument()
                        .setRecordEvents(true)
                        .setRecordCaches(true)
                        .setRecordInterval(Duration.ofSeconds(1))
                        .setConfigModifier(configModifier(numOfRefs, 0))
                        .setWorkDir(tempDir)
                        .setNewJvm(numNodes > 1)
                        .setClustered(numNodes > 1))) {
            var results = harness.launchSync(nodes);

            var eventBag = results.getAllNodesEventNameBag();
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_QUEUED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_IMPORTED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END))
                            .isEqualTo(numOfRefs);

            if (Boolean.getBoolean("norconex.tests.dumpNodeOutputs")) {
                results.getNodeOutputs().forEach((name, output) -> {
                    System.err.println(name + " -> " + summarize(output));
                });
            }

            // Find the coordinator node (the one with cache data)
            var coordinatorOutput = results.getNodeOutputs().values().stream()
                    .filter(output -> !output.getCaches().isEmpty()
                            && output.getCaches().containsKey("crawlSession"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No coordinator node found with cache data"));

            var statusCounts = coordinatorOutput.getLedgerStatusCounts();
            assertThat(statusCounts.getQueued()).isZero();
            assertThat(statusCounts.getUntracked()).isZero();
            assertThat(statusCounts.getProcessing()).isZero();
            assertThat(statusCounts.getProcessed()).isEqualTo(numOfRefs);

        }
    }

    private static String summarize(CrawlTestNodeOutput output) {
        if (output == null) {
            return "<null>";
        }
        var cacheNames = output.getCaches() == null
                ? List.<String>of()
                : output.getCaches().keySet().stream().sorted().toList();
        return "CrawlTestNodeOutput(eventNames=" + output.getEventNames().size()
                + ", logLines=" + output.getLogLines().size()
                + ", caches=" + cacheNames + ")";
    }

    @Test
    @Timeout(60)
    @Disabled
    void testNormalClusterExecution() throws IOException {
        var numOfRefs = 10;

        try (var crawler = new TestCrawler(new CrawlTestInstrument()
                .setRecordEvents(true)
                .setRecordCaches(true)
                .setConfigModifier(configModifier(numOfRefs, 0))
                .setWorkDir(tempDir)
                .setNodeName("node-1")
                .setNewJvm(false)
                .setClustered(false))) {
            var result = crawler.crawl();

            var eventBag = result.getEventNameBag();
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_QUEUED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_IMPORTED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END))
                            .isEqualTo(numOfRefs);

            var statusCounts = result.getLedgerStatusCounts();
            assertThat(statusCounts.getQueued()).isZero();
            assertThat(statusCounts.getUntracked()).isZero();
            assertThat(statusCounts.getProcessing()).isZero();
            assertThat(statusCounts.getProcessed()).isEqualTo(numOfRefs);
        }
    }

    //    @Test
    //    @Timeout(60)
    //    void testStopResumeClusterExecution() throws Exception {
    //        var numOfRefs = 30;
    //        var crawlerId = "stop-resume-crawl";
    //
    //        var harness1 = CrawlTestHarness.standalone(tempDir)
    //                .recordEvents(true)
    //                .configModifier(cfg -> {
    //                    cfg.setId(crawlerId);
    //                    configModifier(numOfRefs, 500).accept(cfg);
    //                })
    //                .build();
    //
    //        var future = harness1.launchAsync();
    //        harness1.waitFor().allNodesToHaveFired(CrawlerEvent.DOCUMENT_IMPORTED);
    //
    //        new Crawler(CrawlTestDriver.create(), harness1.getCrawlConfig()).stop();
    //        var result = future.get(30, TimeUnit.SECONDS);
    //
    //        var firstRunCount = result.getEventNameBag().getCount(
    //                CrawlerEvent.DOCUMENT_IMPORTED);
    //
    //        assertThat(firstRunCount).isBetween(1, numOfRefs - 1);
    //
    //        // Crawler ran and stopped, now resume...
    //
    //        var harness2 = CrawlTestHarness.standalone(tempDir)
    //                .recordEvents(true)
    //                .recordCaches(true)
    //                .configModifier(cfg -> {
    //                    cfg.setId(crawlerId);
    //                    configModifier(numOfRefs, 0).accept(cfg);
    //                })
    //                .build();
    //
    //        result = harness2.launch();
    //
    //        var secondRunCount = result.getEventNameBag().getCount(
    //                CrawlerEvent.DOCUMENT_IMPORTED);
    //
    //        assertThat(firstRunCount + secondRunCount).isEqualTo(numOfRefs);
    //
    //        var runInfo = result.getCrawlRunInfo();
    //        assertThat(runInfo.getCrawlMode()).isSameAs(CrawlMode.FULL);
    //        assertThat(runInfo.getCrawlResumeState())
    //                .isSameAs(CrawlResumeState.RESUMED);
    //    }

    private Consumer<CrawlConfig> configModifier(int numOfRefs, long delayMs) {
        return cfg -> cfg.setStartReferences(IntStream.range(0, numOfRefs)
                .mapToObj(i -> "ref-" + i).toList())
                //                .setId("someId")
                .setMaxQueueBatchSize(10)
                .setNumThreads(2)
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        fcfg -> fcfg.setDelay(Duration.ofMillis(delayMs)))));
    }
}
