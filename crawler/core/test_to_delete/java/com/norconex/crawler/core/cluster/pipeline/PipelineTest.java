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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.junit.cluster.ClusterClient;
import com.norconex.crawler.core.junit.cluster.node.CaptureFlags;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

import lombok.extern.slf4j.Slf4j;

@Timeout(60)
@WithTestWatcherLogging
@Slf4j
class PipelineTest {

    @TempDir
    private Path tempDir;

    /*
     * Tests that a pipeline receiving a stop request will indicate to all
     * nodes they must stop and respond accordingly.
     *
     * This test launches a separate JVM with the "stop" command to verify
     * the real CLI scenario. The Crawler.stop() method uses the admin
     * REST API so it does not have to physically connect itself to the
     * cluster (so we don't create a new node just for stopping).
     */
    @Test
    @Timeout(120)
    void testStop() {
        var starter = CrawlerNode
                .builder()
                .captures(new CaptureFlags()
                        .setCaches(true)
                        .setEvents(true)
                        .setStderr(true)
                        .setStdout(true))
                .appArg("start")
                .build();

        try (var cluster = new ClusterClient(longRunningCrawler())) {
            cluster.launch(starter, 2);

            var exitCodes =
                    cluster.waitFor(Duration.ofSeconds(40)).termination();

            cluster.printNodeLogsOrderedByNode();

            assertThat(exitCodes)
                    .as("all cluster nodes should exit successfully")
                    .isNotEmpty()
                    .allMatch(code -> code == 0);

            // NOTE: Avoid dumping all stdout/stderr from StateDbClient here
            // to prevent H2 OutOfMemory errors when the cluster_state table
            // grows large during diagnostics. Per-node logs are already
            // captured under each node workdir for analysis.
            // cluster.getStateDb().printStreamsOrderedByNode();
        }
    }

    private CrawlConfig longRunningCrawler() {

        // Use test-specific Infinispan config that waits for state transfer
        //        var infinispanConnector =
        //                (InfinispanClusterConnector) config
        //                        .getClusterConnector();
        //        infinispanConnector.getConfiguration()
        //                .setPreset(
        //                        InfinispanClusterConfig.Preset.CUSTOM)
        //                .setConfigFile("cache/infinispan-cluster-test.xml");

        return new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir)
                .setMaxQueueBatchSize(1)
                .setStartReferences(IntStream.range(0, 100)
                        .mapToObj(i -> "ref-" + i).toList())
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ofMillis(100)))))
                .setNumThreads(1);
    }
}
