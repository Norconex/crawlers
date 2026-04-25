/* Copyright 2025-2026 Norconex Inc.
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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
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
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.session.CrawlMode;
import com.norconex.crawler.core.session.CrawlResumeState;
import com.norconex.crawler.core.test.CoreTestUtil;
import com.norconex.crawler.core.test.CrawlTestDriver;
import com.norconex.crawler.core.test.CrawlTestHarness;
import com.norconex.crawler.core.test.CrawlTestInstrument;
import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@WithTestWatcherLogging
@Timeout(30)
@SlowTest
class ClusterScenarioTest {

    private static final Duration CLUSTER_JOIN_WAIT =
            Duration.ofSeconds(120);
    private static final Duration NODE_DIE_WAIT = Duration.ofSeconds(8);
    private static final Duration RESULT_RECORD_INTERVAL =
            Duration.ofMillis(200);
    private static final Duration TEST_IDLE_TIMEOUT = Duration.ofSeconds(1);

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = { 1, 2 })
    @Timeout(180)
    void normalExecutionCompletes(int numNodes) throws Exception {
        var timing = ScenarioTiming.start("normalExecutionCompletes",
                numNodes);
        var numOfRefs = 8;
        var nodes = CoreTestUtil.nodeNames(numNodes);

        try (var harness = newHarness(numNodes > 1,
                instrument -> instrument
                        .setRecordEvents(true)
                        .setRecordCaches(true)
                        .setConfigModifier(cfg -> {
                            baseConfig(numOfRefs, 0)
                                    .accept(cfg);
                            cfg.setId("scenario-normal-"
                                    + numOfRefs
                                    + "-n"
                                    + numNodes);
                        }))) {
            var result = timing.measure("execution",
                    () -> harness.launchSync(nodes));

            timing.measure("verification", () -> {
                var eventBag = result.getAllNodesEventNameBag();
                assertThat(eventBag.getCount(
                        CrawlerEvent.DOCUMENT_QUEUED))
                                .isEqualTo(numOfRefs);
                assertThat(eventBag.getCount(
                        CrawlerEvent.DOCUMENT_IMPORTED))
                                .isEqualTo(numOfRefs);
                assertThat(eventBag.getCount(
                        CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END))
                                .isEqualTo(numOfRefs);

                var coordinatorOutput = result.getNodeOutputs()
                        .values()
                        .stream()
                        .filter(output -> !output
                                .getCaches()
                                .isEmpty()
                                && output.getCaches()
                                        .containsKey(CacheNames.CRAWL_SESSION))
                        .findFirst()
                        .orElseThrow();
                var statusCounts = coordinatorOutput
                        .getLedgerStatusCounts();
                assertThat(statusCounts.getQueued()).isZero();
                assertThat(statusCounts.getUntracked())
                        .isZero();
                assertThat(statusCounts.getProcessed()
                        + statusCounts.getProcessing())
                                .isEqualTo(numOfRefs);
            });
        } finally {
            timing.finish();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 2 })
    @Timeout(240)
    void stopResumeCompletes(int numNodes) throws Exception {
        var timing = ScenarioTiming.start("stopResumeCompletes",
                numNodes);
        var numOfRefs = 12;
        var nodeNames = CoreTestUtil.nodeNames(numNodes);

        try (var harness = newHarness(numNodes > 1,
                instrument -> instrument
                        .setRecordEvents(true)
                        .setRecordCaches(false)
                        .setConfigModifier(cfg -> {
                            baseConfig(numOfRefs,
                                    25).accept(cfg);
                            cfg.setId("scenario-stop-resume-"
                                    + numOfRefs
                                    + "-n"
                                    + numNodes);
                            cfg.setMaxQueueBatchSize(
                                    1);
                        }))) {
            var futureResult = timing.measure("first-run-launch",
                    () -> harness.launchAsync(nodeNames));
            timing.measure("wait-for-processing-begin",
                    () -> harness.waitFor(CLUSTER_JOIN_WAIT)
                            .anyNodeToHaveFired(
                                    CrawlerEvent.DOCUMENT_PROCESSING_BEGIN));
            timing.measure("stop-request",
                    () -> new Crawler(CrawlTestDriver
                            .create(),
                            harness.getFirstNodeConfig())
                                    .stop());
            var firstResult = timing.measure("await-first-run",
                    () -> futureResult.get(30,
                            TimeUnit.SECONDS));
            assertThat(firstResult.getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED))
                            .isBetween(0, numOfRefs);

            harness.getInstrumentTemplate()
                    .setRecordCaches(true)
                    .setConfigModifier(cfg -> {
                        baseConfig(numOfRefs, 0)
                                .accept(cfg);
                        cfg.setId("scenario-stop-resume-"
                                + numOfRefs
                                + "-n"
                                + numNodes);
                        cfg.setMaxQueueBatchSize(50);
                    });

            var secondResult = timing.measure("resume-run",
                    () -> harness.launchSync(nodeNames));
            var coordinatorOutput =
                    requireCoordinator(secondResult);
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
        } finally {
            timing.finish();
        }
    }

    @Test
    @Timeout(240)
    void workerCrashThenResumeCompletes() throws Exception {
        var timing = ScenarioTiming
                .start("workerCrashThenResumeCompletes", 2);
        var numOfRefs = 10;
        var nodeNames = CoreTestUtil.nodeNames(2);

        try (var harness = newHarness(true,
                instrument -> instrument
                        .setRecordEvents(true)
                        .setRecordCaches(false)
                        .setConfigModifier(cfg -> {
                            baseConfig(numOfRefs,
                                    25).accept(cfg);
                            cfg.setId("scenario-worker-crash-"
                                    + numOfRefs);
                            cfg.setMaxQueueBatchSize(
                                    1);
                        }))) {
            var futureResult = timing.measure("first-run-launch",
                    () -> harness.launchAsync(nodeNames));
            timing.measure("wait-for-processing-begin",
                    () -> harness.waitFor(CLUSTER_JOIN_WAIT)
                            .anyNodeToHaveFired(
                                    CrawlerEvent.DOCUMENT_PROCESSING_BEGIN));
            timing.measure("crash-node-2",
                    () -> harness.crashNode("node-2"));
            var firstResult = timing.measure("await-first-run",
                    () -> futureResult.get(120,
                            TimeUnit.SECONDS));
            assertThat(firstResult.getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED))
                            .isGreaterThan(0);

            harness.getInstrumentTemplate()
                    .setRecordCaches(true)
                    .setConfigModifier(cfg -> {
                        baseConfig(numOfRefs, 0)
                                .accept(cfg);
                        cfg.setId("scenario-worker-crash-"
                                + numOfRefs);
                        cfg.setMaxQueueBatchSize(50);
                    });

            var secondResult = timing.measure("resume-run",
                    () -> harness.launchSync(nodeNames));
            var coordinatorOutput =
                    requireCoordinator(secondResult);
            var statusCounts = coordinatorOutput
                    .getLedgerStatusCounts();
            assertThat(statusCounts.getQueued()).isZero();
            assertThat(statusCounts.getUntracked()).isZero();
            assertThat(
                    statusCounts.getProcessed()
                            + statusCounts.getProcessing())
                                    .isEqualTo(numOfRefs);
        } finally {
            timing.finish();
        }
    }

    @Test
    @Timeout(300)
    void coordinatorCrashThenResumeCompletes() throws Exception {
        var timing = ScenarioTiming.start(
                "coordinatorCrashThenResumeCompletes", 2);
        var numOfRefs = 10;
        var nodeNames = CoreTestUtil.nodeNames(2);

        try (var harness = newHarness(true,
                instrument -> instrument
                        .setRecordEvents(true)
                        .setRecordCaches(false)
                        .setConfigModifier(cfg -> {
                            baseConfig(numOfRefs,
                                    25).accept(cfg);
                            cfg.setId(
                                    "scenario-coordinator-crash-"
                                            + numOfRefs);
                            cfg.setMaxQueueBatchSize(
                                    1);
                        }))) {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futureResult = timing.measure("first-run-launch",
                        () -> CompletableFuture.supplyAsync(
                                () -> harness.launchSync(nodeNames), executor));
                timing.measure("wait-for-processing-begin",
                        () -> harness.waitFor(CLUSTER_JOIN_WAIT)
                                .anyNodeToHaveFired(
                                        CrawlerEvent.DOCUMENT_PROCESSING_BEGIN));
                timing.measure("crash-node-1",
                        () -> harness.crashNode("node-1"));
                timing.measure("wait-for-node-1-death",
                        () -> harness.waitFor(NODE_DIE_WAIT)
                                .nodeToHaveDied("node-1"));

                var firstResult = timing.measure("await-first-run",
                        () -> futureResult.get(120,
                                TimeUnit.SECONDS));
                assertThat(firstResult.getNodeOutput("node-2")
                        .getEventNames())
                                .contains(CrawlerEvent.CRAWLER_CRAWL_END);

                harness.getInstrumentTemplate()
                        .setRecordCaches(true)
                        .setConfigModifier(cfg -> {
                            baseConfig(numOfRefs, 0)
                                    .accept(cfg);
                            cfg.setId("scenario-coordinator-crash-"
                                    + numOfRefs);
                            cfg.setMaxQueueBatchSize(50);
                        });

                var secondResult = timing.measure("resume-run",
                        () -> harness.launchSync(nodeNames));
                var coordinatorOutput =
                        requireCoordinator(secondResult);
                var statusCounts = coordinatorOutput
                        .getLedgerStatusCounts();
                assertThat(statusCounts.getQueued()).isZero();
                assertThat(statusCounts.getUntracked()).isZero();
                assertThat(
                        statusCounts.getProcessed()
                                + statusCounts.getProcessing())
                                        .isEqualTo(numOfRefs);
            }
        } finally {
            timing.finish();
        }
    }

    @Test
    @Timeout(420)
    void lateJoiningNodeContinuesCurrentStep() throws Exception {
        var timing = ScenarioTiming.start(
                "lateJoiningNodeContinuesCurrentStep", 2);
        var numOfRefs = 360;
        var initialNodeNames = new String[] { "node-1" };
        var lateNodeName = "node-2";

        try (var harness = newHarness(true,
                instrument -> instrument
                        .setRecordEvents(true)
                        .setRecordCaches(false)
                        .setConfigModifier(cfg -> {
                            baseConfig(numOfRefs,
                                    60).accept(cfg);
                            cfg.setId("scenario-late-join-"
                                    + numOfRefs);
                            cfg.setMaxQueueBatchSize(
                                    1);

                            var connector = (HazelcastClusterConnector) cfg
                                    .getClusterConfig()
                                    .getConnector();
                            var hzConfig = connector
                                    .getConfiguration();
                            var configurer = (JdbcHazelcastConfigurer) hzConfig
                                    .getConfigurer();
                            configurer.setAutoDiscoveryEnabled(
                                    true);
                        }))) {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var initialFuture = timing.measure(
                        "initial-node-launch",
                        () -> CompletableFuture.supplyAsync(
                                () -> harness.launchSync(initialNodeNames),
                                executor));
                timing.measure("wait-for-initial-processing-begin",
                        () -> harness.waitFor(CLUSTER_JOIN_WAIT)
                                .anyNodeToHaveFired(
                                        CrawlerEvent.DOCUMENT_PROCESSING_BEGIN));
                var lateFuture = timing.measure("late-node-launch",
                        () -> CompletableFuture.supplyAsync(
                                () -> harness.launchSync(lateNodeName),
                                executor));
                timing.measure("wait-for-late-node-processing-begin",
                        () -> ConcurrentUtil.waitUntil(
                                () -> harness.getNodeOutput(
                                        lateNodeName)
                                        .map(output -> output
                                                .getEventNames()
                                                .contains(
                                                        CrawlerEvent.DOCUMENT_PROCESSING_BEGIN))
                                        .orElse(false),
                                CLUSTER_JOIN_WAIT,
                                Duration.ofMillis(
                                        100)));

                var initialResult = timing.measure(
                        "await-initial-node-complete",
                        () -> initialFuture.get(120,
                                TimeUnit.SECONDS));
                var lateResult = timing.measure(
                        "await-late-node-complete",
                        () -> lateFuture.get(120,
                                TimeUnit.SECONDS));

                var lateOutput = lateResult.getNodeOutput(lateNodeName);
                assertThat(lateOutput).isNotNull();
                assertThat(lateOutput.getEventNames())
                        .contains(CrawlerEvent.DOCUMENT_PROCESSING_BEGIN);
                assertThat(lateOutput.getEventNameBag()
                        .getCount(CrawlerEvent.DOCUMENT_IMPORTED))
                                .isGreaterThan(0);
                assertThat(initialResult.getNodeOutput("node-1")
                        .getEventNameBag()
                        .getCount(CrawlerEvent.DOCUMENT_IMPORTED))
                                .isGreaterThan(0);
            }
        } finally {
            timing.finish();
        }
    }

    private CrawlTestHarness newHarness(boolean clustered,
            Consumer<CrawlTestInstrument> instrumentModifier) {
        var instrument = new CrawlTestInstrument()
                .setRecordInterval(RESULT_RECORD_INTERVAL)
                .setWorkDir(tempDir)
                .setNewJvm(false)
                .setClustered(clustered);
        instrumentModifier.accept(instrument);
        return new CrawlTestHarness(instrument);
    }

    private static Consumer<CrawlConfig> baseConfig(int numOfRefs,
            long delayMs) {
        return cfg -> cfg
                .setStartReferences(IntStream
                        .range(0, numOfRefs)
                        .mapToObj(i -> "ref-" + i)
                        .toList())
                .setId("scenario-crawler-" + numOfRefs)
                .setMaxQueueBatchSize(10)
                .setNumThreads(2)
                .setOrphansStrategy(CrawlConfig.OrphansStrategy.IGNORE)
                .setIdleTimeout(TEST_IDLE_TIMEOUT)
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        fcfg -> fcfg.setDelay(Duration
                                .ofMillis(delayMs)))));
    }

    private static com.norconex.crawler.core.test.CrawlTestNodeOutput
            requireCoordinator(
                    com.norconex.crawler.core.test.CrawlTestHarnessResult result) {
        return result.getNodeOutputs().values().stream()
                .filter(output -> !output.getCaches().isEmpty()
                        && output.getCaches()
                                .containsKey(CacheNames.CRAWL_SESSION))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No coordinator node found with CRAWL_SESSION cache"));
    }

    private static final class ScenarioTiming {
        private final String testName;
        private final long startNanos;

        private ScenarioTiming(String testName) {
            this.testName = testName;
            startNanos = System.nanoTime();
            LOG.info("[SCENARIO-TIMING] {} start={}", testName,
                    Instant.now());
        }

        static ScenarioTiming start(String testName, int numNodes) {
            return new ScenarioTiming(
                    testName + "[nodes=" + numNodes + "]");
        }

        <T> T measure(String phase, CheckedSupplier<T> supplier)
                throws Exception {
            var phaseStartNanos = System.nanoTime();
            var phaseStart = Instant.now();
            LOG.info("[SCENARIO-TIMING] {} phase={} start={}",
                    testName, phase, phaseStart);
            try {
                return supplier.get();
            } finally {
                LOG.info(
                        "[SCENARIO-TIMING] {} phase={} end={} elapsedMs={} totalMs={}",
                        testName,
                        phase,
                        Instant.now(),
                        Duration.ofNanos(System
                                .nanoTime()
                                - phaseStartNanos)
                                .toMillis(),
                        Duration.ofNanos(System
                                .nanoTime()
                                - startNanos)
                                .toMillis());
            }
        }

        void measure(String phase, CheckedRunnable runnable)
                throws Exception {
            measure(phase, () -> {
                runnable.run();
                return null;
            });
        }

        void finish() {
            LOG.info("[SCENARIO-TIMING] {} finish={} elapsedMs={}",
                    testName,
                    Instant.now(),
                    Duration.ofNanos(System.nanoTime()
                            - startNanos)
                            .toMillis());
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
