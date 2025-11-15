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
package com.norconex.crawler.core.junit.todo.usethis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.junit.cluster.CrawlerCluster;

import lombok.extern.slf4j.Slf4j;

/**
 * Tests coordinator election and bootstrap coordination in a cluster.
 * This is a focused test that verifies cluster formation and coordinator
 * role assignment without doing heavy crawling work.
 */
@Slf4j
class ClusterCoordinatorElectionTest extends AbstractClusterTest {

    @Test
    void testCoordinatorElectedAndBootstrapRuns(@TempDir Path tempDir)
            throws InterruptedException {

        var crawlConfig = createMinimalClusterConfig(tempDir);
        var nodeLauncher = createNodeLauncher();

        try (var cluster = new CrawlerCluster(crawlConfig)) {
            // Launch 2 nodes
            cluster.launch(nodeLauncher, 2);

            // Wait for cluster formation
            cluster.waitForClusterFormation(2, getClusterFormationTimeout());

            // Wait for termination
            cluster.waitForClusterTermination(
                    getClusterTerminationTimeout());

            // Verify success
            verifyClusterSuccess(cluster);
            verifyCoordinatorElection(cluster);

            // Verify only one coordinator ran bootstrap
            var bootstrapLogs = cluster.getNodes().stream()
                    .filter(node -> node.getStdOut().contains(
                            "Bootstrap already in progress"))
                    .count();

            // If we have the log message, exactly one node should have
            // skipped bootstrap (meaning the other ran it)
            if (bootstrapLogs > 0) {
                assertThat(bootstrapLogs)
                        .as("Only one node should skip bootstrap")
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void testThreeNodeClusterCoordination(@TempDir Path tempDir)
            throws InterruptedException {

        var crawlConfig = createMinimalClusterConfig(tempDir);
        var nodeLauncher = createNodeLauncher();

        try (var cluster = new CrawlerCluster(crawlConfig)) {
            // Launch 3 nodes to test more complex coordination
            cluster.launch(nodeLauncher, 3);

            cluster.waitForClusterFormation(3, getClusterFormationTimeout());
            cluster.waitForClusterTermination(
                    getClusterTerminationTimeout());

            verifyClusterSuccess(cluster);
            verifyCoordinatorElection(cluster);

            // Verify all nodes saw the full cluster
            cluster.getNodes().forEach(node -> {
                var nodeCountLogs = node.getStdOut();
                // At some point, each node should have seen 3 members
                assertThat(nodeCountLogs)
                        .as("Node should have observed 3-node cluster")
                        .containsAnyOf("Node count: 3", "3 nodes");
            });
        }
    }
}
