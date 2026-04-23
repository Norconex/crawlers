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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.test.CrawlTestHarness;
import com.norconex.crawler.core.test.CrawlTestInstrument;

@WithTestWatcherLogging
@Timeout(30)
@SlowTest
class ClusterLateJoinPrototypeTest {

    private static final Duration CLUSTER_JOIN_WAIT = Duration.ofSeconds(60);
    private static final Duration INITIAL_NODE_HEAD_START =
            Duration.ofSeconds(1);
    private static final Duration RESULT_RECORD_INTERVAL =
            Duration.ofMillis(200);
    private static final Duration TEST_IDLE_TIMEOUT = Duration.ofSeconds(1);

    @TempDir
    private Path tempDir;

    @Test
    @Timeout(180)
    void lateJoinUsingClusterJoinGateFetcher() throws Exception {
        var numOfRefs = 48;
        var initialNodeNames = new String[] { "node-1" };
        var lateNodeName = "node-2";

        try (var harness = newHarness(instrument -> instrument
                .setRecordEvents(true)
                .setRecordCaches(false)
                .setConfigModifier(cfg -> {
                    baseConfig(numOfRefs, 0).accept(cfg);
                    cfg.setId("prototype-late-join-" + numOfRefs);
                    cfg.setMaxQueueBatchSize(1);

                    var connector = (HazelcastClusterConnector) cfg
                            .getClusterConfig().getConnector();
                    var hzConfig = connector.getConfiguration();
                    var configurer = (JdbcHazelcastConfigurer) hzConfig
                            .getConfigurer();
                    configurer.setAutoDiscoveryEnabled(true);
                })
                .setNodeConfigModifier((nodeName, cfg) -> {
                    if ("node-1".equals(nodeName)) {
                        cfg.setNumThreads(1);
                        cfg.setFetchers(List.of(new ClusterJoinGateFetcher()
                                .setRequiredNodeCount(2)
                                .setGatedRefs(List.of("ref-0"))));
                        return;
                    }
                    cfg.setNumThreads(2);
                    cfg.setFetchers(List.of(Configurable.configure(
                            new MockFetcher(),
                            fcfg -> fcfg.setDelay(Duration.ZERO))));
                }))) {
            var initialFuture = harness.launchAsync(initialNodeNames);
            Thread.sleep(INITIAL_NODE_HEAD_START.toMillis());

            var lateFuture = harness.launchAsync(lateNodeName);

            var initialResult = initialFuture.get(
                    CLUSTER_JOIN_WAIT.toSeconds(), TimeUnit.SECONDS);
            var lateResult = lateFuture.get(
                    CLUSTER_JOIN_WAIT.toSeconds(), TimeUnit.SECONDS);

            var lateOutput = lateResult.getNodeOutput(lateNodeName);
            assertThat(lateOutput).isNotNull();
            assertThat(lateOutput.getEventNames())
                    .contains(CrawlerEvent.DOCUMENT_PROCESSING_BEGIN);
            assertThat(lateOutput.getEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED)).isGreaterThan(0);
            assertThat(initialResult.getNodeOutput("node-1").getEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED)).isGreaterThan(0);
        }
    }

    private CrawlTestHarness newHarness(
            Consumer<CrawlTestInstrument> instrumentModifier) {
        var instrument = new CrawlTestInstrument()
                .setRecordInterval(RESULT_RECORD_INTERVAL)
                .setWorkDir(tempDir)
                .setNewJvm(true)
                .setClustered(true);
        instrumentModifier.accept(instrument);
        return new CrawlTestHarness(instrument);
    }

    private static Consumer<CrawlConfig> baseConfig(int numOfRefs,
            long delayMs) {
        return cfg -> cfg
                .setStartReferences(IntStream.range(0, numOfRefs)
                        .mapToObj(i -> "ref-" + i)
                        .toList())
                .setId("prototype-crawler-" + numOfRefs)
                .setMaxQueueBatchSize(10)
                .setNumThreads(2)
                .setIdleTimeout(TEST_IDLE_TIMEOUT)
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        fcfg -> fcfg.setDelay(Duration.ofMillis(delayMs)))));
    }
}
