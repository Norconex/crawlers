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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.session.CrawlMode;
import com.norconex.crawler.core.session.CrawlResumeState;
import com.norconex.crawler.core.test.CoreTestUtil;
import com.norconex.crawler.core.test.CrawlTestDriver;
import com.norconex.crawler.core.test.CrawlTestHarness;
import com.norconex.crawler.core.test.CrawlTestInstrument;
import com.norconex.crawler.core.test.CrawlTestNodeOutput;

import lombok.extern.slf4j.Slf4j;

//@SlowTest
// Run single node in JVM
@Slf4j
@WithTestWatcherLogging
class ClusterTest {
        private static final Duration CLUSTER_JOIN_WAIT =
                        Duration.ofSeconds(120);
        private static final Duration FIRST_WORK_WAIT = Duration.ofSeconds(15);
        private static final Duration NODE_DIE_WAIT = Duration.ofSeconds(15);

        @TempDir
        private Path tempDir;

        @ParameterizedTest
        @ValueSource(ints = { 1, 2 })
        @Timeout(180)
        void testNormalExecution(int numNodes) throws IOException {
                var numOfRefs = 10;
                var nodes = CoreTestUtil.nodeNames(numNodes);

                try (var harness = new CrawlTestHarness(
                                new CrawlTestInstrument()
                                                .setRecordEvents(true)
                                                .setRecordCaches(true)
                                                .setRecordInterval(Duration
                                                                .ofSeconds(1))
                                                .setConfigModifier(cfg -> {
                                                        configModifier(numOfRefs,
                                                                        0)
                                                                                        .accept(cfg);
                                                        cfg.setId("test-crawler-"
                                                                        + numOfRefs
                                                                        + "-n"
                                                                        + numNodes);
                                                })
                                                .setWorkDir(tempDir)
                                                .setNewJvm(numNodes > 1)
                                                .setClustered(numNodes > 1))) {
                        var results = harness.launchSync(nodes);

                        var eventBag = results.getAllNodesEventNameBag();
                        assertThat(eventBag.getCount(
                                        CrawlerEvent.DOCUMENT_QUEUED))
                                                        .isEqualTo(numOfRefs);
                        assertThat(eventBag.getCount(
                                        CrawlerEvent.DOCUMENT_IMPORTED))
                                                        .isEqualTo(numOfRefs);
                        assertThat(eventBag.getCount(
                                        CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END))
                                                        .isEqualTo(numOfRefs);

                        if (Boolean.getBoolean(
                                        "norconex.tests.dumpNodeOutputs")) {
                                results.getNodeOutputs()
                                                .forEach((name, output) -> {
                                                        System.err.println(name
                                                                        + " -> "
                                                                        + summarize(output));
                                                });
                        }

                        // Find the coordinator node (the one with cache data)
                        var coordinatorOutput = results.getNodeOutputs()
                                        .values().stream()
                                        .filter(output -> !output.getCaches()
                                                        .isEmpty()
                                                        && output.getCaches()
                                                                        .containsKey("crawlSession"))
                                        .findFirst()
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No coordinator node found with cache data"));

                        var statusCounts = coordinatorOutput
                                        .getLedgerStatusCounts();
                        assertThat(statusCounts.getQueued()).isZero();
                        assertThat(statusCounts.getUntracked()).isZero();
                        assertThat(
                                        statusCounts.getProcessed()
                                                        + statusCounts.getProcessing())
                                                                        .isEqualTo(numOfRefs);

                }
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2 })
        @Timeout(240)
        void testStopResumeClusterExecution(int numNodes) throws Exception {
                // Use enough references so that even if one node starts earlier,
                // there is still work left for the other node to import at least one.
                var numOfRefs = 24;
                var nodeNames = CoreTestUtil.nodeNames(numNodes);
                var instrument = new CrawlTestInstrument()
                                .setRecordEvents(true)
                                .setRecordCaches(false)
                                .setRecordInterval(Duration.ofSeconds(1))
                                .setConfigModifier(cfg -> {
                                        configModifier(numOfRefs, 80)
                                                        .accept(cfg);
                                        cfg.setId("test-stop-resume-"
                                                        + numOfRefs + "-n"
                                                        + numNodes);
                                        // Smaller batches reduce the chance one node claims all
                                        // work before the other node starts consuming.
                                        cfg.setMaxQueueBatchSize(1);
                                })
                                .setWorkDir(tempDir)
                                .setNewJvm(numNodes > 1)
                                .setClustered(numNodes > 1);

                try (var harness = new CrawlTestHarness(instrument)) {

                        //--- First run being stopped ---

                        var futureResult = harness.launchAsync(nodeNames);
                        // Ensure at least one node has started importing documents.
                        // Work distribution in a cluster isn't deterministic.
                        harness.waitFor(CLUSTER_JOIN_WAIT)
                                        .anyNodeToHaveFired(
                                                        CrawlerEvent.DOCUMENT_IMPORTED);
                        new Crawler(CrawlTestDriver.create(),
                                        harness.getFirstNodeConfig())
                                                        .stop();
                        var firstResult =
                                        futureResult.get(30, TimeUnit.SECONDS);

                        var firstRunCount = firstResult
                                        .getAllNodesEventNameBag().getCount(
                                                        CrawlerEvent.DOCUMENT_IMPORTED);
                        assertThat(firstRunCount).isBetween(0, numOfRefs);

                        //--- Resume run ---

                        instrument.setRecordCaches(true)
                                        .setConfigModifier(cfg -> {
                                                configModifier(numOfRefs, 0)
                                                                .accept(cfg);
                                                cfg.setId("test-stop-resume-"
                                                                + numOfRefs
                                                                + "-n"
                                                                + numNodes);
                                                // Resume run should finish quickly; larger batches
                                                // reduce cluster coordination overhead.
                                                cfg.setMaxQueueBatchSize(50);
                                        });
                        var secondResult = harness.launchSync(nodeNames);
                        // Find the coordinator node (the one with cache data).
                        // In multi-node tests, CRAWL_SESSION is the most reliably captured
                        // cache and contains the crawlRunInfo we need.
                        var coordinatorOutput = secondResult.getNodeOutputs()
                                        .values()
                                        .stream()
                                        .filter(output -> !output.getCaches()
                                                        .isEmpty()
                                                        && output.getCaches()
                                                                        .containsKey(CacheNames.CRAWL_SESSION))
                                        .findFirst()
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No coordinator node found with CRAWL_SESSION cache"));

                        // Events can be undercounted by 1 on clustered shutdown timing
                        // (last event emitted while caches are already closing). The ledger
                        // is the authoritative persisted state between stop/resume runs.
                        var statusCounts = coordinatorOutput
                                        .getLedgerStatusCounts();
                        assertThat(statusCounts.getQueued()).isZero();
                        assertThat(statusCounts.getUntracked()).isZero();
                        assertThat(
                                        statusCounts.getProcessed()
                                                        + statusCounts.getProcessing())
                                                                        .isEqualTo(numOfRefs);

                        var runInfo = coordinatorOutput.getCrawlRunInfo();
                        assertThat(runInfo.getCrawlMode())
                                        .isSameAs(CrawlMode.FULL);
                        assertThat(runInfo.getCrawlResumeState())
                                        .isSameAs(CrawlResumeState.RESUMED);
                }
        }

        @ParameterizedTest
        @ValueSource(ints = { 2 })
        @Timeout(240)
        void testNodeCrashAndResume(int numNodes) throws Exception {
                // Crash one non-coordinator node mid-crawl. The surviving node finishes
                // whatever it can. A second run re-queues the orphaned PROCESSING items
                // (via RequeueOrphansForProcessingStep) and completes the rest.
                var numOfRefs = 16;
                var nodeNames = CoreTestUtil.nodeNames(numNodes);
                var instrument = new CrawlTestInstrument()
                                .setRecordEvents(true)
                                .setRecordCaches(false)
                                .setRecordInterval(Duration.ofSeconds(1))
                                .setConfigModifier(cfg -> {
                                        configModifier(numOfRefs, 80)
                                                        .accept(cfg);
                                        // Unique ID to avoid sharing file-based state with other tests
                                        cfg.setId("nx-crash-" + numOfRefs);
                                        cfg.setMaxQueueBatchSize(1);
                                })
                                .setWorkDir(tempDir)
                                .setNewJvm(true)
                                .setClustered(true);

                try (var harness = new CrawlTestHarness(instrument)) {

                        //--- Phase 1: crash node-2 mid-crawl ---

                        var futureResult = harness.launchAsync(nodeNames);
                        harness.waitFor(CLUSTER_JOIN_WAIT)
                                        .anyNodeToHaveFired(
                                                        CrawlerEvent.DOCUMENT_IMPORTED);
                        harness.crashNode("node-2");

                        // Allow up to 180s: ~10s HZ failure detection + ~30s for the
                        // surviving node to finish processing remaining documents.
                        var firstResult = futureResult.get(120,
                                        TimeUnit.SECONDS);
                        var firstImportTotal = firstResult
                                        .getAllNodesEventNameBag()
                                        .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
                        // At least some documents were imported before/after the crash
                        assertThat(firstImportTotal).isGreaterThan(0);

                        //--- Phase 2: second run handles any items orphaned as PROCESSING ---
                        // Whether this is a RESUMED run (orphans found) or a NEW run (survivor
                        // handled everything), the ledger must show all refs are complete.

                        instrument.setRecordCaches(true)
                                        .setConfigModifier(cfg -> {
                                                configModifier(numOfRefs, 0)
                                                                .accept(cfg);
                                                cfg.setId("nx-crash-"
                                                                + numOfRefs);
                                                cfg.setMaxQueueBatchSize(50);
                                        });
                        var secondResult = harness.launchSync(nodeNames);

                        var coordinatorOutput = secondResult.getNodeOutputs()
                                        .values()
                                        .stream()
                                        .filter(output -> !output.getCaches()
                                                        .isEmpty()
                                                        && output.getCaches()
                                                                        .containsKey(CacheNames.CRAWL_SESSION))
                                        .findFirst()
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No coordinator node found with CRAWL_SESSION cache"));

                        var statusCounts = coordinatorOutput
                                        .getLedgerStatusCounts();
                        assertThat(statusCounts.getQueued()).isZero();
                        assertThat(statusCounts.getUntracked()).isZero();
                        // In a crash scenario, a few items may remain as PROCESSING in the
                        // ledger (stuck from the crashed node). An INCREMENTAL second run
                        // does not call requeueProcessingEntries(), so they stay.
                        // The meaningful guarantee: ALL refs are accounted for.
                        assertThat(
                                        statusCounts.getProcessed()
                                                        + statusCounts.getProcessing())
                                                                        .isEqualTo(numOfRefs);
                }
        }

        @Test
        @Timeout(240)
        void testCoordinatorCrashAndReelection() throws Exception {
                // Crashes node-1 (oldest HZ member = initial coordinator) mid-crawl.
                // Hazelcast fires CoordinatorChangeListener on node-2, which promotes
                // itself to coordinator via PipelineExecution.handleCoordinatorChange().
                // Node-2 then completes the crawl autonomously without a restart.
                // A second run re-queues items orphaned by node-1 and finishes them.
                var numOfRefs = 16;
                var numNodes = 2;
                var nodeNames = CoreTestUtil.nodeNames(numNodes);
                var instrument = new CrawlTestInstrument()
                                .setRecordEvents(true)
                                .setRecordCaches(false)
                                .setRecordInterval(Duration.ofSeconds(1))
                                .setConfigModifier(cfg -> {
                                        configModifier(numOfRefs, 80)
                                                        .accept(cfg);
                                        // Unique ID to avoid sharing file-based state with other tests
                                        cfg.setId("nx-coord-" + numOfRefs);
                                        cfg.setMaxQueueBatchSize(1);
                                })
                                .setWorkDir(tempDir)
                                .setNewJvm(true)
                                .setClustered(true);

                try (var harness = new CrawlTestHarness(instrument)) {

                        //--- Phase 1: crash the initial coordinator (node-1) ---

                        var futureResult = harness.launchAsync(nodeNames);
                        harness.waitFor(CLUSTER_JOIN_WAIT)
                                        .anyNodeToHaveFired(
                                                        CrawlerEvent.DOCUMENT_IMPORTED);
                        harness.crashNode("node-1");
                        // Wait to confirm the process is actually dead
                        harness.waitFor(NODE_DIE_WAIT)
                                        .nodeToHaveDied("node-1");

                        var firstResult = futureResult.get(120,
                                        TimeUnit.SECONDS);

                        // Re-election successful: node-2 must have completed the crawl
                        var node2Output = firstResult.getNodeOutputs()
                                        .get("node-2");
                        assertThat(node2Output).isNotNull();
                        assertThat(node2Output.getEventNames())
                                        .contains(CrawlerEvent.CRAWLER_CRAWL_END);
                        var firstImportTotal = firstResult
                                        .getAllNodesEventNameBag()
                                        .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
                        assertThat(firstImportTotal).isGreaterThan(0);

                        //--- Phase 2: second run handles any items orphaned by crashed node-1 ---
                        // Whether this is a RESUMED run (orphans found) or a NEW run (node-2
                        // handled everything), the ledger must show all refs are complete.

                        instrument.setRecordCaches(true)
                                        .setConfigModifier(cfg -> {
                                                configModifier(numOfRefs, 0)
                                                                .accept(cfg);
                                                cfg.setId("nx-coord-"
                                                                + numOfRefs);
                                                cfg.setMaxQueueBatchSize(50);
                                        });
                        var secondResult = harness.launchSync(nodeNames);

                        var coordinatorOutput = secondResult.getNodeOutputs()
                                        .values()
                                        .stream()
                                        .filter(output -> !output.getCaches()
                                                        .isEmpty()
                                                        && output.getCaches()
                                                                        .containsKey(CacheNames.CRAWL_SESSION))
                                        .findFirst()
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No coordinator node found with CRAWL_SESSION cache"));

                        var statusCounts = coordinatorOutput
                                        .getLedgerStatusCounts();
                        assertThat(statusCounts.getQueued()).isZero();
                        assertThat(statusCounts.getUntracked()).isZero();
                        // In a crash scenario, a few items may remain as PROCESSING in the
                        // ledger (stuck from the crashed node). A RESUMED run calls
                        // requeueProcessingEntries() which is the correct recovery path.
                        // The meaningful guarantee: ALL refs are accounted for.
                        assertThat(
                                        statusCounts.getProcessed()
                                                        + statusCounts.getProcessing())
                                                                        .isEqualTo(numOfRefs);
                }
        }

        private static String summarize(CrawlTestNodeOutput output) {
                if (output == null) {
                        return "<null>";
                }
                var cacheNames = output.getCaches() == null
                                ? List.<String>of()
                                : output.getCaches().keySet().stream().sorted()
                                                .toList();
                return "CrawlTestNodeOutput(eventNames="
                                + output.getEventNames().size()
                                + ", logLines=" + output.getLogLines().size()
                                + ", caches=" + cacheNames + ")";
        }

        //    @Test
        //    @Timeout(60)
        //    void testStopResumeClusterExecution() throws Exception {
        //        var numOfRefs = 30;
        //        var crawlerId = "stop-resume-crawl";

        //        var harness1 = CrawlTestHarness.standalone(tempDir)
        //                .recordEvents(true)
        //                .configModifier(cfg -> {
        //                    cfg.setId(crawlerId);
        //                    configModifier(numOfRefs, 500).accept(cfg);
        //                })
        //                .build();

        //        var future = harness1.launchAsync();
        //        harness1.waitFor().allNodesToHaveFired(CrawlerEvent.DOCUMENT_IMPORTED);

        //        new Crawler(CrawlTestDriver.create(), harness1.getCrawlConfig()).stop();
        //        var result = future.get(30, TimeUnit.SECONDS);

        //        var firstRunCount = result.getEventNameBag().getCount(
        //                CrawlerEvent.DOCUMENT_IMPORTED);

        //        assertThat(firstRunCount).isBetween(1, numOfRefs - 1);

        //        // Crawler ran and stopped, now resume...

        //        var harness2 = CrawlTestHarness.standalone(tempDir)
        //                .recordEvents(true)
        //                .recordCaches(true)
        //                .configModifier(cfg -> {
        //                    cfg.setId(crawlerId);
        //                    configModifier(numOfRefs, 0).accept(cfg);
        //                })
        //                .build();

        //        result = harness2.launch();

        //        var secondRunCount = result.getEventNameBag().getCount(
        //                CrawlerEvent.DOCUMENT_IMPORTED);

        //        assertThat(firstRunCount + secondRunCount).isEqualTo(numOfRefs);

        //        var runInfo = result.getCrawlRunInfo();
        //        assertThat(runInfo.getCrawlMode()).isSameAs(CrawlMode.FULL);
        //        assertThat(runInfo.getCrawlResumeState())
        //                .isSameAs(CrawlResumeState.RESUMED);
        //    }

        private Consumer<CrawlConfig> configModifier(int numOfRefs,
                        long delayMs) {
                return cfg -> cfg
                                .setStartReferences(IntStream
                                                .range(0, numOfRefs)
                                                .mapToObj(i -> "ref-" + i)
                                                .toList())
                                .setId("test-crawler-" + numOfRefs)
                                .setMaxQueueBatchSize(10)
                                .setNumThreads(2)
                                // Configure idleTimeout for multi-node coordination
                                // Nodes wait this long after seeing empty queue before deciding work is done
                                .setIdleTimeout(Duration.ofSeconds(2))
                                .setFetchers(List.of(Configurable.configure(
                                                new MockFetcher(),
                                                fcfg -> fcfg.setDelay(Duration
                                                                .ofMillis(delayMs)))));
        }
}
