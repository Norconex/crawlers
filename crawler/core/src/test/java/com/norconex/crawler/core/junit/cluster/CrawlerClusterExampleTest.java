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
package com.norconex.crawler.core.junit.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.junit.cluster.node.NodeState;
import com.norconex.crawler.core.junit.todo.usethis.annotations.ClusterTest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ClusterTest
class CrawlerClusterExampleTest {

    @Test
    void testCrawlerCluster(@TempDir Path tempDir) {
        // 1. Create crawl config
        // Note: FastLocalInfinispanClusterConnector is automatically set
        // by CrawlDriverInstrumentor in the child JVM, so we don't need
        // to configure it here (avoids serialization issues)
        var crawlConfig = new CrawlConfig();

        // 2. Set the cluster root directory on the crawler config.
        //    Will be modified by nodes to be unique when launched.
        crawlConfig.setWorkDir(tempDir);

        // 3. Apply other crawler configuration as per your test. E.g.:
        //    crawlConfig.setStartReferences(List.of("http://example.com/test"));

        // 4. Prepare crawler launcher. The "-config" argument is automatically
        //    added if the crawl config is not null and there is at least
        //    one application argument provided
        var nodeLauncher = CrawlerNode.builder()
                .exportCaches(true)
                .exportEvents(true)
                .appArg("start") // CLI argument
                .build();

        // 5. Create and launch the cluster with two nodes, making sure
        //    to close it when done.
        try (var cluster = new CrawlerCluster(crawlConfig)) {
            cluster.launch(nodeLauncher, 2);

            // 6. Wait for cluster formation before beginning runtime tests
            cluster.waitForClusterFormation(2, Duration.ofSeconds(30));

            // 7. Wait for cluster termination before running terminal tests
            cluster.waitForClusterTermination(Duration.ofSeconds(30));

            // 8. Perform your cluster/node tests
            cluster.getNodes().forEach(node -> {
                var hasErrors = node.hasErrors();
                LOG.info("{} --> exit value: {} | has errors: {}",
                        node.getWorkDir().getFileName(),
                        (node.getProcess().isAlive() ? "still running"
                                : node.getProcess().exitValue()),
                        hasErrors);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("\n=== STDOUT " + StringUtils.repeat('=', 69)
                            + "\n" + node.getStdOut());
                    LOG.debug("\n=== STDERR " + StringUtils.repeat('=', 69)
                            + "\n" + node.getStdErr());
                    LOG.debug("\n=== STATE PROPS " + StringUtils.repeat('=', 64)
                            + "\n" + node.loadStateProps());
                }
                if (hasErrors) {
                    Assertions.fail("Node \"" + node.getWorkDir().getFileName()
                            + "\" reported errors:\n"
                            + node.getFailureSummary());
                }
                var topFileNames = node.listFiles().stream()
                        .map(f -> f.getFileName().toString())
                        .toList();
                assertThat(topFileNames).contains(
                        "state.properties",
                        "stderr.log",
                        "stdout.log")
                        .anyMatch(fn -> fn
                                .startsWith(CrawlerCluster.CRAWLER_ID_PREFIX));
                var props = node.loadStateProps();
                assertThat(props.getStrings(NodeState.NODE_STARTED_AT))
                        .isNotEmpty();
                assertThat(props.getStrings(NodeState.NODE_COUNT))
                        .isNotEmpty();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }
}
