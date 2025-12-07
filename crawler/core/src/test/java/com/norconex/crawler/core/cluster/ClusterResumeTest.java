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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.cluster.admin.ClusterAdminClient;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConfig.Preset;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class ClusterResumeTest {

    private @TempDir Path tempDir;
    private CountDownLatch latch;

    @Test
    @Timeout(120)
    void testSingleNodeStopResumeInSameJvm()
            throws InterruptedException, ExecutionException, TimeoutException {

        var numOfRefs = 20;
        latch = new CountDownLatch(1);

        // Initial config
        var crawlCfg = crawlConfig(numOfRefs, 500);
        var crawlerId = crawlCfg.getId();
        var driver = new TestCrawlDriverFactory();

        // First run: start and then stop via CLI in-process
        var crawler = new Crawler(driver.get(), crawlCfg);

        // Start
        var future = Executors.newSingleThreadExecutor().submit(() -> {
            crawler.crawl();
        });

        latch.await(30, TimeUnit.SECONDS);

        System.err.println("XXX stop it!");
        crawler.stop(ClusterAdminClient.DEFAULT_NODE_URL);

        future.get(30, TimeUnit.SECONDS);

        // Second run: same crawler ID, zero delay, should resume and
        // process all remaining documents.
        crawlCfg = crawlConfig(numOfRefs, 0);
        crawlCfg.setId(crawlerId);

        var crawler2 = new Crawler(driver.get(), crawlCfg);

        // Start second run
        crawler2.crawl();

        // After the second run completes, create a read-only session to
        // inspect final metrics.
        //        crawler2.withCrawlSession((CrawlSession session) -> {
        //            var crawlContext = session.getCrawlContext();
        //            CrawlerMetrics metrics = crawlContext.getMetrics();
        //
        //            var summary =
        //                    new com.norconex.crawler.core.cmd.crawl.CrawlProgressLogger(
        //                            crawlContext)
        //                                    .getExecutionSummary();
        //
        //            LOG.info("Execution summary after resume:\n{}", summary);
        //
        //            assertThat(summary)
        //                    .contains("Total processed:   " + numOfRefs);
        //
        //            var eventCounts = metrics.getEventCounts();
        //            assertThat(eventCounts.get(CrawlerEvent.DOCUMENT_IMPORTED))
        //                    .isEqualTo(numOfRefs);
        //        });
    }

    private CrawlConfig crawlConfig(int numOfRefs, long delayMs) {
        var crawlCfg = new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir)
                .setMaxQueueBatchSize(1)
                .setEventListeners(List.of(ev -> {
                    if (ev.is(CrawlerEvent.DOCUMENT_IMPORTED)
                            && latch.getCount() > 0) {
                        LOG.info("At least 1 document imported.");
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
