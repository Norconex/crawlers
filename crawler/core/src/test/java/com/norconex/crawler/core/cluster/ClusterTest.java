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
import com.norconex.crawler.core.junit.todo.usethis.annotations.SlowTest;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

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

        var crawlCfg = new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir)
                .setMaxQueueBatchSize(1)
                .setStartReferences(IntStream.range(0, numOfRefs)
                        .mapToObj(i -> "ref-" + i).toList())
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ofMillis(0)))))
                .setNumThreads(1);

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
            var counters = caches.get(CacheNames.COUNTERS);
            assertThat(counters.getInteger("status-counter-QUEUED"))
                    .isZero();
            assertThat(counters.getInteger("status-counter-UNTRACKED"))
                    .isZero();
            assertThat(counters.getInteger("status-counter-PROCESSING"))
                    .isZero();
            assertThat(counters.getInteger("status-counter-PROCESSED"))
                    .isEqualTo(10);
        }
    }

    @Test
    @Timeout(120)
    void testStopResumeClusterExecution() {
        var numOfRefs = 100;

        var crawlCfg = new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir)
                .setMaxQueueBatchSize(1)
                .setStartReferences(IntStream.range(0, numOfRefs)
                        .mapToObj(i -> "ref-" + i).toList())
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ofMillis(500)))))
                .setNumThreads(1);

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
                System.err.println("=== CACHES ===");
                cluster.getStateDb().getCacheRecords()
                        .forEach(rec -> System.err.println("REC: " + rec));
                System.err.println("=== LOGS ===");
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
            var eventBag = cluster.getStateDb().getEventNameBag();
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_QUEUED)).isGreaterThan(0);
            assertThat(eventBag.getCount(
                    CrawlerEvent.DOCUMENT_IMPORTED)).isLessThan(numOfRefs);

            cluster.getStateDb().getCacheRecords()
                    .forEach(rec -> System.err.println("REC: " + rec));
        }
    }

}
