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
package com.norconex.crawler.core.junit.clusternew;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.clusternew.node.CrawlerNodeLauncher;

class CrawlerClusterExampleTest {

    @Test
    void testCrawlerCluster(@TempDir Path tempDir) {
        // 1. Create crawl config
        // Note: FastLocalInfinispanClusterConnector is automatically set
        // by CrawlDriverInstrumentor in the child JVM, so we don't need
        // to configure it here (avoids serialization issues)
        var crawlConfig = new CrawlConfig();

        // Explicitly set cluster connector to null to prevent serialization
        // of the default connector. CrawlDriverInstrumentor will set the
        // FastLocalInfinispanClusterConnector in the child JVM.
        //        crawlConfig.setClusterConnector(null);

        // 2. Set the cluster root directory on the crawler config.
        //    Will be modified by nodes to be unique when launched.
        crawlConfig.setWorkDir(tempDir);

        // 3. Apply other crawler configuration as per your test
        crawlConfig.setStartReferences(
                List.of("http://example.com/test"));

        // 4. Prepare crawler launcher. The "-config" argument is automatically
        //    added if the crawl config is not null and there is at least
        //    one application argument provided
        var nodeLauncher = CrawlerNodeLauncher.builder()
                .exportCaches(true)
                .exportEvents(true)
                .appArg("start") // CLI argument
                .build();

        // 5. Create and launch the cluster with two nodes, making sure
        //    to close it when done.
        try (var cluster = new CrawlerCluster(nodeLauncher, crawlConfig)) {
            cluster.launch(2);

            // 6. Wait for cluster formation before beginning tests
            cluster.waitForClusterFormation(Duration.ofSeconds(15));

            // 7. Give nodes a moment to process and flush logs
            //            Thread.sleep(2000);

            // 8. Verify nodes started successfully without errors
            cluster.getNodes().forEach(node -> {
                System.err.println("Checking node: "
                        + node.getWorkDir().getFileName());
                System.err.println("  Process alive: "
                        + node.getProcess().isAlive());
                System.err.println("  Has errors: " + node.hasErrors());

                if (node.hasErrors()) {
                    Assertions.fail("Node " + node.getWorkDir().getFileName()
                            + " reported errors:\n" + node.getErrorSummary());
                }
            });

            // 8. Test that exported data is available
            cluster.getNodes().forEach(node -> {
                System.err.println("XXX Process exit value: "
                        + (node.getProcess().isAlive() ? "still running"
                                : node.getProcess().exitValue()));
                System.err.println("XXX STDOUT: " + node.getStdout());
                System.err.println("XXX STDERR: " + node.getStderr());
                System.err.println("XXX WorkDir files:\n  "
                        + String.join("\n  ", node.listFiles().stream()
                                .map(f -> f.getFileName().toString())
                                .toList()));
                System.err.println("XXX STATE: " + node.loadStateProps());
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }

        //        // 4. Get client for cache access
        //        var client = cluster.getClusterClient();
        //
        //        // Verify all nodes joined
        //        assertThat(client.getClusterNodeCount())
        //                .as("Should have 3 crawler nodes + 1 client")
        //                .isEqualTo(4);
        //
        //        // 5. Query caches in real-time
        //        var ledgerCache = client.getCache(
        //                "crawlEntryLedger_indexed",
        //                Object.class);
        //
        //        LOG.info("Ledger cache size: {}", ledgerCache.size());
        //
        //        // You can query any cache created by the crawler
        //        var metricsCache = client.getCache(
        //                "crawlEventCounts",
        //                Long.class);
        //
        //        metricsCache
        //                .forEach((key, value) -> LOG.info("Metric {}: {}", key, value));

        // No need to export caches - you're reading them live!

        // Clean up
        //        client.close();
    }
}
