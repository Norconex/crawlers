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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hazelcast.core.Hazelcast;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.cluster.admin.ClusterAdminClient;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConfig.Preset;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.cluster.impl.hazelcast.RocksDBMapStore;
import com.norconex.crawler.core.cluster.impl.hazelcast.RocksDBQueueStore;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.cluster.node.CaptureFlags;
import com.norconex.crawler.core.junit.cluster.node.DriverInstrumentor;
import com.norconex.crawler.core.junit.cluster.state.StateDbServer;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class ClusterResumeTest {

    private @TempDir Path tempDir;
    private CountDownLatch latch;

    @AfterEach
    void cleanup() {
        LOG.info("=== Cleaning up after test ===");

        // Shutdown all Hazelcast instances
        Hazelcast.shutdownAll();

        // Give OS time to release file locks
        Sleeper.sleepSeconds(1);

        LOG.info("=== Cleanup complete ===");
    }

    @Timeout(120)
    @ParameterizedTest
    @ValueSource(ints = { 2 })
    void testNodeStopResumeInSameJvm(int numNodes) {
        latch = new CountDownLatch(numNodes);
        var numOfRefs = 10;

        new StateDbServer(tempDir, "resume-db").withStateDb(stateDb -> {
            List<Future<?>> futures = new ArrayList<>();

            var exec = Executors.newFixedThreadPool(numNodes);
            //                    .submit(() -> crawler.crawl());

            var crawlerRef = new AtomicReference<Crawler>();

            for (var i = 0; i < numNodes; i++) {
                var idx = i;
                futures.add(exec.submit(() -> {
                    var crawlCfg = crawlConfig(numNodes, idx, numOfRefs, 1000);
                    var crawler = new Crawler(driver(idx), crawlCfg);
                    if (crawlerRef.get() == null) {
                        crawlerRef.set(crawler);
                    }
                    crawler.crawl();
                }));
                // Give more time between node starts to ensure proper
                // cluster formation and worker registration
                Sleeper.sleepMillis(1000);
            }

            System.err.println("XXX latch count is : " + latch.getCount());

            latch.await(30, TimeUnit.SECONDS);

            // Give a moment for any in-flight queue operations to complete
            // before stopping, ensuring all queue items are persisted
            Sleeper.sleepSeconds(2);

            LOG.info("Stop running crawler(s).");
            //            crawlerRef.get().stop(null);
            //            CrawlSession.
            //            int adminPort = crawlerRef.get().
            crawlerRef.get().stop(ClusterAdminClient.DEFAULT_NODE_URL);
            crawlerRef.set(null);
            futures.forEach(f -> {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.clear();

            var eventCounts = stateDb.getEventNameBag();
            assertThat(eventCounts.getCount(CrawlerEvent.DOCUMENT_IMPORTED))
                    .isBetween(1, numOfRefs - 1);
            Sleeper.sleepSeconds(2);

            LOG.info("=== Cleanup between runs ===");
            // Close all RocksDB instances to release file locks
            // This is critical for the second run to reopen the same RocksDB paths
            RocksDBQueueStore.closeAll();
            RocksDBMapStore.closeAll();

            // Give OS time to release file locks
            Sleeper.sleepSeconds(1);
            LOG.info("=== Cleanup complete, ready for resume ===");

            //            if (true)
            //                return;

            //--- Second run ---------------------------------------------------
            // same crawler ID, zero delay, should resume and
            // process all remaining documents.

            var exec2 = Executors.newFixedThreadPool(numNodes);
            for (var i = 0; i < numNodes; i++) {
                var idx = i;
                futures.add(exec2.submit(() -> {
                    var crawlCfg = crawlConfig(numNodes, idx, numOfRefs, 0);
                    var crawler = new Crawler(driver(idx), crawlCfg);
                    crawler.crawl();
                }));
            }
            futures.forEach(f -> {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            eventCounts = stateDb.getEventNameBag();
            assertThat(eventCounts.getCount(CrawlerEvent.DOCUMENT_IMPORTED))
                    .isEqualTo(numOfRefs);
        });
    }

    private CrawlDriver driver(int nodeIndex) {
        return DriverInstrumentor.from(
                tempDir.resolve("node-" + nodeIndex),
                new CaptureFlags().setEvents(true))
                .instrument(new TestCrawlDriverFactory().get());
    }

    private CrawlConfig crawlConfig(
            int numNodes, int idx, int numOfRefs, long delayMs) {
        var importedOne = new AtomicBoolean();
        //NOTE: workdir is instrumented
        var crawlCfg = new CrawlConfig()
                .setId("testCrawler-" + numNodes + "nodes")
                .setMaxQueueBatchSize(1)
                .setEventListeners(List.of(ev -> {
                    if (ev.is(CrawlerEvent.DOCUMENT_IMPORTED)
                            && !importedOne.get()) {
                        LOG.info("At least 1 document imported by node-" + idx);
                        importedOne.set(true);
                        latch.countDown();
                    }
                }))
                .setStartReferences(IntStream.range(0, numOfRefs)
                        .mapToObj(i -> "ref-" + i).toList())
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ofMillis(delayMs)))))
                .setNumThreads(1);
        crawlCfg.getClusterConfig().setClustered(true);
        ((HazelcastClusterConnector) crawlCfg.getClusterConfig().getConnector())
                .getConfiguration()
                .setPreset(Preset.CLUSTER);
        return crawlCfg;
    }
}
