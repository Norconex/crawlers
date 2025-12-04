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
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConfig.Preset;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.cluster.ClusterClient;
import com.norconex.crawler.core.junit.cluster.node.CaptureFlags;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.junit.todo.usethis.annotations.SlowTest;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.session.CrawlMode;
import com.norconex.crawler.core.session.CrawlResumeState;

import lombok.extern.slf4j.Slf4j;

@SlowTest
@Slf4j
class ClusterTest {

    @TempDir
    private Path tempDir;

    @Test
    @Timeout(120)
    void testNormalClusterExecution() {
        var numOfRefs = 10;

        var crawlCfg = longRunning(numOfRefs, 0);

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

        var crawlCfg = longRunning(numOfRefs, 500);

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
                    TestCrawlDriverFactory.create(),
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

            var runInfo = stateDb.getCrawlRunInfo();
            assertThat(runInfo.getCrawlMode()).isSameAs(CrawlMode.FULL);
            assertThat(runInfo.getCrawlResumeState())
                    .isSameAs(CrawlResumeState.NEW);
            assertThat(cluster.activeNodeCount()).isZero();

            // Start again, which should resume (but don't wait)
            crawlCfg = longRunning(numOfRefs, 0);
            cluster.reset(crawlCfg);
            cluster.launch(starter, 2);
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
        crawlCfg.getClusterConfig().setClustered(true);
        ((HazelcastClusterConnector) crawlCfg.getClusterConfig().getConnector())
                .getConfiguration().setPreset(Preset.CLUSTER);
        return crawlCfg;
    }
}
