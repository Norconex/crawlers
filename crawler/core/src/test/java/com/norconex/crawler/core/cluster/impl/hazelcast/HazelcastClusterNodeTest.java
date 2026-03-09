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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.junit.annotations.SlowTest;

/**
 * Component tests for {@link HazelcastClusterNode}.
 *
 * <p>Each test method starts (at most) two {@link HazelcastInstance}s and the
 * {@link AfterEach} method calls {@link Hazelcast#shutdownAll()} so the JVM
 * port range is fully released before the next test.</p>
 *
 * <p>Tests that spin up multi-node clusters wait for cluster formation using
 * a simple polling loop (Awaitility is not a project dependency).</p>
 */
@SlowTest
@Timeout(30)
class HazelcastClusterNodeTest {

    /**
     * Tear down all instances unconditionally so no port conflicts bleed
     * from one test into the next.
     */
    @AfterEach
    void tearDownAll() {
        HazelcastTestSupport.shutdownAll();
    }

    // -----------------------------------------------------------------
    // Standalone flag
    // -----------------------------------------------------------------

    @Test
    @Timeout(15)
    void testStandaloneNode_isAlwaysCoordinator() {
        var hz = HazelcastTestSupport.startNode();
        var node = new HazelcastClusterNode(hz, /* standalone= */ true);

        assertThat(node.isStandaloneNode()).isTrue();
        assertThat(node.isCoordinator()).isTrue();
    }

    // -----------------------------------------------------------------
    // Single clustered node (not standalone)
    // -----------------------------------------------------------------

    @Test
    @Timeout(15)
    void testSingleClusteredNode_isCoordinator() {
        var hz = HazelcastTestSupport.startNode();
        var node = new HazelcastClusterNode(hz, /* standalone= */ false);

        // A single-member cluster: the only member must be the oldest
        assertThat(node.isCoordinator()).isTrue();
    }

    @Test
    @Timeout(15)
    void testGetNodeName_returnsUuidString() {
        var hz = HazelcastTestSupport.startNode();
        var node = new HazelcastClusterNode(hz, /* standalone= */ false);

        var name = node.getNodeName();
        assertThat(name).isNotNull();
        assertThatCode(() -> UUID.fromString(name))
                .as("node name should be parseable as UUID")
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------
    // Closed / shutdown node
    // -----------------------------------------------------------------

    @Test
    @Timeout(15)
    void testClose_shutsDownHazelcastInstance() {
        var hz = HazelcastTestSupport.startNode();
        var node = new HazelcastClusterNode(hz, /* standalone= */ false);

        node.close();

        assertThat(hz.getLifecycleService().isRunning()).isFalse();
    }

    @Test
    @Timeout(15)
    void testGetNodeName_afterShutdown_returnsNull() {
        var hz = HazelcastTestSupport.startNode();
        var node = new HazelcastClusterNode(hz, /* standalone= */ false);

        // Shut down the underlying instance directly (simulates external stop)
        hz.shutdown();

        assertThat(node.getNodeName()).isNull();
    }

    @Test
    @Timeout(15)
    void testIsCoordinator_afterShutdown_returnsFalse() {
        var hz = HazelcastTestSupport.startNode();
        var node = new HazelcastClusterNode(hz, /* standalone= */ false);

        hz.shutdown();

        assertThat(node.isCoordinator()).isFalse();
    }

    // -----------------------------------------------------------------
    // Two-node cluster: coordinator election
    // -----------------------------------------------------------------

    @Test
    @Timeout(30)
    void testTwoNodeCluster_oldestMemberIsCoordinator() throws Exception {
        var clusterName = "two-node-" + UUID.randomUUID();
        var hz0 = HazelcastTestSupport.startNode(clusterName);
        var hz1 = HazelcastTestSupport.startNode(clusterName);

        var node0 = new HazelcastClusterNode(hz0, /* standalone= */ false);
        var node1 = new HazelcastClusterNode(hz1, /* standalone= */ false);

        // Wait until both nodes have joined the same cluster
        waitUntilClusterSize(hz0, 2);
        waitUntilClusterSize(hz1, 2);

        // The first-started member (oldest) is the coordinator
        assertThat(node0.isCoordinator()).isTrue();
        assertThat(node1.isCoordinator()).isFalse();
    }

    @Test
    @Timeout(30)
    void testCoordinatorFailover_secondNodeBecomesCoordinatorAfterFirstLeaves()
            throws Exception {
        var clusterName = "failover-" + UUID.randomUUID();
        var hz0 = HazelcastTestSupport.startNode(clusterName);
        var hz1 = HazelcastTestSupport.startNode(clusterName);

        var node1 = new HazelcastClusterNode(hz1, /* standalone= */ false);

        // Wait for full two-member cluster
        waitUntilClusterSize(hz0, 2);
        waitUntilClusterSize(hz1, 2);

        // Sanity: node1 is NOT the coordinator yet
        assertThat(node1.isCoordinator()).isFalse();

        // Remove the oldest member
        hz0.shutdown();

        // Wait for node1 to see itself as the sole surviving member
        waitUntilClusterSize(hz1, 1);
        // Give Hazelcast a moment to elect a new coordinator
        waitUntil(() -> node1.isCoordinator(), 15_000);

        assertThat(node1.isCoordinator()).isTrue();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Polls until the given {@link HazelcastInstance} sees the expected number
     * of cluster members, or until {@code timeoutMs} milliseconds pass.
     */
    private static void waitUntilClusterSize(
            HazelcastInstance hz, int expectedSize)
            throws Exception {
        waitUntil(
                () -> hz.getLifecycleService().isRunning()
                        && hz.getCluster().getMembers().size() == expectedSize,
                15_000);
    }

    /**
     * Polls {@code condition} every 100 ms until it returns {@code true} or
     * {@code timeoutMs} elapses.
     *
     * @throws Exception if polling is interrupted or condition throws
     * @throws AssertionError if the condition never becomes {@code true}
     */
    private static void waitUntil(
            BooleanSupplier condition, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError(
                        "Condition did not become true within " + timeoutMs
                                + "ms");
            }
            Thread.sleep(100);
        }
    }

    /**
     * A simple boolean supplier for the polling helpers.
     */
    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
