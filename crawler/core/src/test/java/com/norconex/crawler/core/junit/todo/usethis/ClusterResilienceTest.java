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
import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core._DELETE.junit.cluster_old.CrawlerCluster;

import lombok.extern.slf4j.Slf4j;

/**
 * Tests cluster resilience scenarios including coordinator failure,
 * node drops, and dynamic node joining. These tests verify that the
 * cluster can handle failures and topology changes gracefully.
 */
@Slf4j
class ClusterResilienceTest extends AbstractClusterTest {

    /**
     * Test that when the coordinator node is killed, another node
     * takes over and the crawl continues.
     */
    @Test
    @Disabled("TODO: Implement mechanism to identify and kill coordinator")
    void testCoordinatorFailoverAndReelection(@TempDir Path tempDir)
            throws InterruptedException {

        var crawlConfig = createMinimalClusterConfig(tempDir);
        // Set longer crawl so we have time to kill coordinator
        // crawlConfig.setMaxDocuments(1000);

        var nodeLauncher = createNodeLauncher();

        try (var cluster = new CrawlerCluster(crawlConfig)) {
            // Launch 3 nodes so we can kill one and still have quorum
            cluster.launch(nodeLauncher, 3);

            cluster.waitForClusterFormation(3, getClusterFormationTimeout());

            // TODO: Give cluster time to start crawling
            Sleeper.sleepSeconds(5);

            // TODO: Identify coordinator node from logs/state
            var coordinatorNode = cluster.getNodes().stream()
                    .filter(node -> node.getStdOut().contains("COORDINATOR"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No coordinator found"));

            LOG.info("Killing coordinator node: {}",
                    coordinatorNode.getWorkDir().getFileName());

            // Kill the coordinator process
            coordinatorNode.getProcess().destroyForcibly();

            // Wait for coordinator re-election and continued crawling
            Sleeper.sleepSeconds(10);

            // Remaining nodes should re-elect coordinator and continue
            // TODO: Verify in logs that re-election happened

            cluster.waitForClusterTermination(
                    getClusterTerminationTimeout());

            // Verify remaining nodes completed successfully
            var successfulNodes = cluster.getNodes().stream()
                    .filter(node -> !node.getProcess().isAlive()
                            && node.getProcess().exitValue() == 0)
                    .count();

            assertThat(successfulNodes)
                    .as("At least 2 nodes should complete successfully")
                    .isGreaterThanOrEqualTo(2);
        }
    }

    /**
     * Test that a node can join a running cluster and participate
     * in the crawl.
     */
    @Test
    @Disabled("TODO: Implement delayed node launch mechanism")
    void testDynamicNodeJoining(@TempDir Path tempDir)
            throws InterruptedException {

        var crawlConfig = createMinimalClusterConfig(tempDir);
        // Set longer crawl so node can join mid-crawl
        // crawlConfig.setMaxDocuments(500);

        var nodeLauncher = createNodeLauncher();

        try (var cluster = new CrawlerCluster(crawlConfig)) {
            // Start with 2 nodes
            cluster.launch(nodeLauncher, 2);

            cluster.waitForClusterFormation(3, getClusterFormationTimeout());

            // Let crawl start
            Sleeper.sleepSeconds(5);

            LOG.info("Launching additional node mid-crawl...");

            // Add a 3rd node while crawling
            cluster.launch(nodeLauncher, 1);

            // Give new node time to join and sync
            Sleeper.sleepSeconds(5);

            // TODO: Verify the new node joined and received work
            // Check logs for "Node count changed: 2 -> 3"

            cluster.waitForClusterTermination(
                    getClusterTerminationTimeout());

            verifyClusterSuccess(cluster);

            // All 3 nodes should show they saw the 3-node cluster
            cluster.getNodes().forEach(node -> {
                assertThat(node.getStdOut())
                        .as("Node should have observed 3-node cluster")
                        .contains("Node count: 3");
            });
        }
    }

    /**
     * Test that when a worker node fails, the coordinator detects it
     * and the cluster continues.
     */
    @Test
    @Disabled("TODO: Implement mechanism to identify and kill worker")
    void testWorkerNodeFailure(@TempDir Path tempDir)
            throws InterruptedException {

        var crawlConfig = createMinimalClusterConfig(tempDir);
        var nodeLauncher = createNodeLauncher();

        try (var cluster = new CrawlerCluster(crawlConfig)) {
            // Launch 3 nodes
            cluster.launch(nodeLauncher, 3);

            cluster.waitForClusterFormation(3, getClusterFormationTimeout());

            // Let crawl start
            Sleeper.sleepSeconds(5);

            // TODO: Kill a non-coordinator node
            var workerNode = cluster.getNodes().stream()
                    .filter(node -> !node.getStdOut()
                            .contains("Starting pipeline coordinator"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No worker node found"));

            LOG.info("Killing worker node: {}",
                    workerNode.getWorkDir().getFileName());

            workerNode.getProcess().destroyForcibly();

            // Coordinator should detect failure and continue with 2 nodes
            cluster.waitForClusterTermination(
                    Duration.ofMinutes(2)); // Longer timeout for recovery

            // At least 2 nodes should complete successfully
            var successfulNodes = cluster.getNodes().stream()
                    .filter(node -> !node.getProcess().isAlive()
                            && node.getProcess().exitValue() == 0)
                    .count();

            assertThat(successfulNodes)
                    .as("At least 2 nodes should complete successfully")
                    .isGreaterThanOrEqualTo(2);
        }
    }

    /**
     * Test that the cluster can handle a network partition scenario
     * (simulated by killing/restarting nodes rapidly).
     */
    @Test
    @Disabled("TODO: Implement partition simulation")
    void testNetworkPartitionRecovery(@TempDir Path tempDir)
            throws InterruptedException {

        var crawlConfig = createMinimalClusterConfig(tempDir);
        var nodeLauncher = createNodeLauncher();

        try (var cluster = new CrawlerCluster(crawlConfig)) {
            cluster.launch(nodeLauncher, 3);

            cluster.waitForClusterFormation(3, getClusterFormationTimeout());

            // Simulate partition by killing 1 node
            var isolatedNode = cluster.getNodes().get(2);
            isolatedNode.getProcess().destroyForcibly();

            Sleeper.sleepSeconds(5);

            // TODO: Verify the 2-node cluster continued working
            // TODO: Could restart the killed node and verify it rejoins

            cluster.waitForClusterTermination(
                    getClusterTerminationTimeout());

            // Verify at least majority completed
            var successfulNodes = cluster.getNodes().stream()
                    .filter(node -> !node.getProcess().isAlive()
                            && node.getProcess().exitValue() == 0)
                    .count();

            assertThat(successfulNodes)
                    .as("Majority of nodes should complete")
                    .isGreaterThanOrEqualTo(2);
        }
    }
}
