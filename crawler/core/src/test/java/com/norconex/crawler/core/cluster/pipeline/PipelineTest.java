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

            cluster.waitFor().termination();

            cluster.getStateDb().printStreamsOrderedByNode();

            var exitValues = cluster.getNodeExitValues(
                    Duration.ofSeconds(10));
            LOG.info("Node exit values: {}", exitValues);
            assertThat(exitValues)
                    .as("all cluster nodes should exit successfully")
                    .isNotEmpty()
                    .allMatch(code -> code == 0);
        }
    }

    private CrawlConfig longRunningCrawler() {
        return new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir)
                .setMaxQueueBatchSize(1)
                .setStartReferences(List.of("ref-1",
                        "ref-2", "ref-3"))
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ofSeconds(1)))))
                .setNumThreads(1);
    }
}
