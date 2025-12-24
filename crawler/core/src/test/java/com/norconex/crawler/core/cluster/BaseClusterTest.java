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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.cluster.impl.hazelcast.CacheNames;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.cluster.ClusterClient;
import com.norconex.crawler.core.junit.cluster.node.CaptureFlags;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.session.CrawlMode;
import com.norconex.crawler.core.session.CrawlResumeState;
import com.norconex.crawler.core.test.CrawlTestDriver;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//@SlowTest
@Slf4j
abstract class BaseClusterTest {

    @Getter
    @TempDir
    private Path tempDir;

    protected abstract boolean isClustered();

    protected abstract void configure(CrawlConfig cfg);

    @Test
    @Timeout(120)
    void testNormalClusterExecution() {
        var numOfRefs = 10;

        var crawlCfg = longRunning(numOfRefs, 0);
        configure(crawlCfg);

        var starter = CrawlerNode
                .builder()
                .captures(new CaptureFlags()
                        .setCaches(true)
                        .setEvents(true)
                        .setStderr(true)
                        .setStdout(true))
                .appArg("start")
                .build();

        try (var cluster = new ClusterClient(crawlCfg)) {
            cluster.launch(starter, 2);

            var exitCodes =
                    cluster.waitFor(Duration.ofSeconds(40)).termination();

            cluster.printNodeLogsOrderedByNode();

            assertThat(exitCodes)
                    .as("all cluster nodes should exit successfully")
                    .isNotEmpty()
                    .allMatch(code -> code == 0);

            var eventBag = cluster.getStateDb().getEventNameBag();
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_QUEUED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_IMPORTED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END))
                            .isEqualTo(numOfRefs);

            // those are found with default cluster impl:

            var caches = cluster.getStateDb().getCachesAsMap();
            var sessionCache = caches.get(CacheNames.CRAWL_SESSION);

            assertThat(sessionCache.getInteger("status-counter-QUEUED"))
                    .isZero();
            assertThat(sessionCache.getInteger("status-counter-UNTRACKED"))
                    .isZero();
            assertThat(sessionCache.getInteger("status-counter-PROCESSING"))
                    .isZero();
            assertThat(sessionCache.getInteger("status-counter-PROCESSED"))
                    .isEqualTo(10);
        }
    }

    @Test
    @Timeout(120)
    void testStopResumeClusterExecution() {
        var numOfRefs = 100;

        // Create initial config with a fixed crawler ID
        var crawlCfg = longRunning(numOfRefs, 500);
        var crawlerId = crawlCfg.getId(); // Save the crawler ID

        var starter = CrawlerNode
                .builder()
                .captures(new CaptureFlags()
                        .setCaches(true)
                        .setEvents(true)
                        .setStderr(true)
                        .setStdout(true))
                .appArg("start")
                .build();

        try (var cluster = new ClusterClient(crawlCfg)) {
            // Use explicit node names so they can be reused on restart
            cluster.launch(starter, "node-1", "node-2");

            // wait until both nodes are active
            try {
                cluster.waitFor()
                        .allNodesToHaveFired(CrawlerEvent.DOCUMENT_IMPORTED);
            } catch (Exception e) {
                cluster.printNodeLogsOrderedByNode();
                throw e;
            }

            LOG.info("Launching stopper JVM/command...");
            CliCrawlerLauncher.launch(
                    CrawlTestDriver.create(),
                    "stop",
                    "-config", cluster.getConfigFile().toString());

            var exitCodes =
                    cluster.waitFor(Duration.ofSeconds(40)).termination();

            cluster.printNodeLogsOrderedByNode();

            assertThat(exitCodes)
                    .as("all cluster nodes should exit successfully")
                    .isNotEmpty()
                    .allMatch(code -> code == 0);

            // should not be done processing
            var stateDb = cluster.getStateDb();
            var eventBag = stateDb.getEventNameBag();
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_QUEUED)).isGreaterThan(0);
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_IMPORTED)).isLessThan(numOfRefs);

            //            // Verify via logs that the crawlDocuments step did not
            //            // complete successfully on all workers. We expect at least
            //            // one reduction to STOPPED and no reduction to COMPLETED for
            //            // crawlDocuments in this first run.
            //            var stdoutRecords = stateDb.getRecordsForTopic(
            //                    StateDbClient.TOPIC_STDOUT);
            //            var reductions = stdoutRecords.stream()
            //                    .map(StateRecord::getValue)
            //                    .filter(msg -> msg.contains(
            //                            "Step \"crawlDocuments\" reduced to"))
            //                    .toList();
            //            assertThat(reductions)
            //                    .as("Expected at least one reduction log for "
            //                            + "crawlDocuments")
            //                    .isNotEmpty();
            //            assertThat(reductions)
            //                    .noneMatch(msg -> msg.contains("reduced to COMPLETED"));
            //            assertThat(reductions)
            //                    .anyMatch(msg -> msg.contains("reduced to STOPPED"));

            var runInfo = stateDb.getCrawlRunInfo();
            assertThat(runInfo.getCrawlMode()).isSameAs(CrawlMode.FULL);
            assertThat(runInfo.getCrawlResumeState())
                    .isSameAs(CrawlResumeState.NEW);
            assertThat(cluster.activeNodeCount()).isZero();

            // Start again with SAME crawler ID, which should resume
            crawlCfg = longRunning(numOfRefs, 0);
            crawlCfg.setId(crawlerId); // Reuse the same crawler ID
            cluster.reset(crawlCfg);
            // IMPORTANT: Use the same node names to reuse persisted cache data
            cluster.launch(starter, "node-1", "node-2");
            exitCodes = cluster.waitFor(Duration.ofSeconds(40)).termination();
            cluster.printNodeLogsOrderedByNode();
            assertThat(exitCodes)
                    .as("all cluster nodes should exit successfully")
                    .isNotEmpty()
                    .allMatch(code -> code == 0);
            // should be done processing
            stateDb = cluster.getStateDb();
            eventBag = stateDb.getEventNameBag();

            runInfo = stateDb.getCrawlRunInfo();
            assertThat(runInfo.getCrawlMode()).isSameAs(CrawlMode.FULL);
            assertThat(runInfo.getCrawlResumeState())
                    .isSameAs(CrawlResumeState.RESUMED);
            assertThat(cluster.activeNodeCount()).isZero();

            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_QUEUED)).isEqualTo(numOfRefs);
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_IMPORTED)).isEqualTo(numOfRefs);
        }
    }

    //        @Test
    //        @Timeout(120)
    //        void testSingleNodeStopResumeInSameJvm() {
    //            var numOfRefs = 50;
    //
    //            // Initial config
    //            var crawlCfg = longRunning(numOfRefs, 500);
    //            var crawlerId = crawlCfg.getId();
    //
    //            // First run: start and then stop via CLI in-process
    //            var configFile = tempDir.resolve("single-node-config.yaml");
    //            StandaloneCliCrawlerLauncher.writeConfig(crawlCfg, configFile);
    //
    //            // Start
    //            StandaloneCliCrawlerLauncher.capture(
    //                    TestCrawlDriverFactory.create(),
    //                    "start",
    //                    "-config",
    //                    configFile.toString());
    //
    //            // Issue stop command
    //            StandaloneCliCrawlerLauncher.capture(
    //                    TestCrawlDriverFactory.create(),
    //                    "stop",
    //                    "-config",
    //                    configFile.toString());
    //
    //            // At this point we have a STOPPED run with partial progress.
    //            // Second run: same crawler ID, zero delay, should resume and
    //            // process all remaining documents.
    //            crawlCfg = longRunning(numOfRefs, 0);
    //            crawlCfg.setId(crawlerId);
    //            StandaloneCliCrawlerLauncher.writeConfig(crawlCfg, configFile);
    //
    //            var output = StandaloneCliCrawlerLauncher.capture(
    //                    TestCrawlDriverFactory.create(),
    //                    "start",
    //                    "-config",
    //                    configFile.toString());
    //
    //            // Basic sanity: CLI should have exited with code 0
    //            assertThat(output.getExitCode()).isZero();
    //
    //            // The in-process launcher already aggregates events; we
    //            // validate that the total queued/imported matches numOfRefs.
    //            var eventBag = output.getStateDb().getEventNameBag();
    //            assertThat(eventBag.getCount(CrawlerEvent.DOCUMENT_QUEUED))
    //                    .isEqualTo(numOfRefs);
    //            assertThat(eventBag.getCount(CrawlerEvent.DOCUMENT_IMPORTED))
    //                    .isEqualTo(numOfRefs);
    //
    //            var runInfo = output.getStateDb().getCrawlRunInfo();
    //            assertThat(runInfo.getCrawlMode()).isSameAs(CrawlMode.FULL);
    //            // This new test is mostly for debugging; we log the resume
    //            // state but do not assert on its exact value yet.
    //            // assertThat(runInfo.getCrawlResumeState())
    //            //         .isSameAs(CrawlResumeState.RESUMED);
    //        }

    private CrawlConfig longRunning(int numOfRefs, long delayMs) {
        var crawlCfg = new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir)
                .setMaxQueueBatchSize(1)
                .setStartReferences(IntStream.range(0, numOfRefs)
                        .mapToObj(i -> "ref-" + i).toList())
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ofMillis(delayMs)))))
                .setNumThreads(1);
        crawlCfg.getClusterConfig().setClustered(isClustered());
        //        ((HazelcastClusterConnector) crawlCfg.getClusterConfig().getConnector())
        //                .getConfiguration()
        //                .setPreset(Preset.CLUSTER);
        return crawlCfg;
    }
}
