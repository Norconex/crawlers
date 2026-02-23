///* Copyright 2025 Norconex Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.norconex.crawler.core.junit.todo.usethis;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.nio.file.Path;
//import java.time.Duration;
//import java.util.List;
//
//import org.junit.jupiter.api.Assertions;
//
//import com.norconex.crawler.core.CrawlConfig;
//import com.norconex.crawler.core._DELETE.junit.cluster_old.CrawlerCluster;
//import com.norconex.crawler.core._DELETE.junit.cluster_old.node.CrawlerNode;
//import com.norconex.crawler.core._DELETE.junit.cluster_old.node.NodeState;
//
//import lombok.extern.slf4j.Slf4j;
//
///**
// * Base class for cluster tests providing common setup and utilities.
// * Subclasses can configure the crawl behavior and test specific
// * cluster scenarios.
// */
//@Slf4j
//public abstract class AbstractClusterTest {
//
//    /**
//     * Create a minimal crawl config for cluster testing.
//     * Uses fast, lightweight references that don't require actual
//     * HTTP fetching.
//     * @param tempDir temporary directory for this test
//     * @return configured CrawlConfig
//     */
//    protected CrawlConfig createMinimalClusterConfig(Path tempDir) {
//        var crawlConfig = new CrawlConfig();
//        crawlConfig.setWorkDir(tempDir);
//
//        // Use minimal references for fast cluster testing.
//        // These will bootstrap the queue but won't do heavy crawling.
//        crawlConfig.setStartReferences(List.of(
//                "http://example.com/test"));
//
//        // Keep thread count low for cluster coordination tests
//        crawlConfig.setNumThreads(1);
//
//        // Optionally: set max documents low to finish quickly
//        // crawlConfig.setMaxDocuments(10);
//
//        return crawlConfig;
//    }
//
//    /**
//     * Create a node launcher with standard test settings.
//     * @return configured launcher
//     */
//    protected CrawlerNode createNodeLauncher() {
//        return CrawlerNode.builder()
//                .exportCaches(true)
//                .exportEvents(true)
//                .appArg("start")
//                .build();
//    }
//
//    /**
//     * Standard cluster formation wait time for tests.
//     */
//    protected Duration getClusterFormationTimeout() {
//        return Duration.ofSeconds(30);
//    }
//
//    /**
//     * Standard cluster termination wait time for tests.
//     */
//    protected Duration getClusterTerminationTimeout() {
//        return Duration.ofSeconds(30);
//    }
//
//    /**
//     * Verify that all nodes in the cluster completed successfully
//     * without errors.
//     * @param cluster the cluster to verify
//     */
//    protected void verifyClusterSuccess(CrawlerCluster cluster) {
//        cluster.getNodes().forEach(node -> {
//            var hasErrors = node.hasErrors();
//            LOG.info("{} --> exit value: {} | has errors: {}",
//                    node.getWorkDir().getFileName(),
//                    (node.getProcess().isAlive() ? "still running"
//                            : node.getProcess().exitValue()),
//                    hasErrors);
//
//            if (hasErrors) {
//                LOG.error("Node error details:\n{}",
//                        node.getFailureSummary());
//                Assertions.fail("Node \""
//                        + node.getWorkDir().getFileName()
//                        + "\" reported errors:\n"
//                        + node.getFailureSummary());
//            }
//
//            var topFileNames = node.listFiles().stream()
//                    .map(f -> f.getFileName().toString())
//                    .toList();
//            assertThat(topFileNames).contains(
//                    "state.properties",
//                    "stderr.log",
//                    "stdout.log");
//
//            var props = node.loadStateProps();
//            assertThat(props.getStrings(NodeState.NODE_STARTED_AT))
//                    .isNotEmpty();
//            assertThat(props.getStrings(NodeState.NODE_COUNT))
//                    .isNotEmpty();
//        });
//    }
//
//    /**
//     * Verify coordinator election happened correctly.
//     * @param cluster the cluster to verify
//     */
//    protected void verifyCoordinatorElection(CrawlerCluster cluster) {
//        // Verify that at least one node has coordinator logs/state
//        var hadCoordinator = cluster.getNodes().stream()
//                .anyMatch(node -> node.getStdOut()
//                        .contains("COORDINATOR"));
//
//        assertThat(hadCoordinator)
//                .as("At least one node should have been coordinator")
//                .isTrue();
//    }
//}
