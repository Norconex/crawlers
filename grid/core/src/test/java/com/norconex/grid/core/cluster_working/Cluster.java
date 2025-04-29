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
package com.norconex.grid.core.cluster_working;

import java.io.Closeable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Local cluster used for testing.
 * @see ClusterTest
 */
@Slf4j
public class Cluster implements Closeable {

    private final List<Grid> nodes = new ArrayList<>();
    private Path tempDir;

    // Count of cluster created on this JVM to ensure unique names
    private static final AtomicInteger clusterCount = new AtomicInteger();
    // Count of nodes for the current cluster
    private final AtomicInteger nodeCount = new AtomicInteger();

    private final ClusterConnectorFactory connectorFactory;
    private final String gridName;

    public Cluster(
            ClusterConnectorFactory connectorFactory,
            Path tempDir) {
        this.connectorFactory = connectorFactory;
        this.tempDir = tempDir;
        gridName = "test-grid-" + clusterCount.incrementAndGet();
    }

    @Override
    public void close() {
        nodes.forEach(Grid::close);
        nodes.clear();
        tempDir = null;
    }

    public void onOneNewNode(FailableConsumer<Grid, Exception> nodeConsumer) {
        onNewNodes(1, nodeConsumer);
    }

    public void onTwoNewNodes(FailableConsumer<Grid, Exception> nodeConsumer) {
        onNewNodes(2, nodeConsumer);
    }

    public void onThreeNewNodes(
            FailableConsumer<Grid, Exception> nodeConsumer) {
        onNewNodes(3, nodeConsumer);
    }

    public void onNewNodes(
            int numNodes, FailableConsumer<Grid, Exception> nodeConsumer) {
        runOnNodes(newNodes(numNodes), nodeConsumer);
    }

    public Grid oneNewNode() {
        return newNodes(1).get(0);
    }

    public List<Grid> twoNewNodes() {
        return newNodes(2);
    }

    public List<Grid> threeNewNodes() {
        return newNodes(3);
    }

    public List<Grid> newNodes(int numNodes) {
        var newNodes = createNewNodes(numNodes);
        waitForQuorum(newNodes);
        return newNodes;
    }

    private void runOnNodes(
            List<Grid> nodes,
            FailableConsumer<Grid, Exception> nodeConsumer) {
        var taskFailure =
                new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(nodes.size());
        var execFutures = nodes
                .stream()
                .map(node -> CompletableFuture.runAsync(
                        () -> {
                            try {
                                nodeConsumer.accept(node);
                            } catch (Exception e) {
                                taskFailure.set(e);
                            }
                        }, executor))
                .toList();

        try {
            ConcurrentUtil.get(CompletableFuture.allOf(
                    execFutures.toArray(new CompletableFuture[0])));
            if (taskFailure.get() != null) {
                LOG.error("At least one node threw an exception.",
                        taskFailure.get());
                throw new AssertionError(
                        "At least one node threw an exception.",
                        taskFailure.get());
            }
            LOG.info("All nodes ran without known issues.");
        } catch (Exception e) {
            LOG.error("Failed to execute on nodes.", e);
            throw new AssertionError("Failed to execute on nodes.", e);
        } finally {
            executor.shutdown();
        }
    }

    /*
     * Create new nodes, connect to them, and add them to cluster-wide
     * list of nodes (in addition to returning those just created)..
     */
    private List<Grid> createNewNodes(int numNodes) {
        List<Grid> newNodes = new ArrayList<>();
        for (var i = 0; i < numNodes; i++) {
            var nodeName = gridName + "-node-" + nodeCount.incrementAndGet();
            var node = connectorFactory.create(
                    gridName, nodeName).connect(tempDir);
            newNodes.add(node);
        }
        nodes.addAll(newNodes);
        return newNodes;
    }

    /*
     * Wait until all supplied nodes are active on the cluster.
     */
    private void waitForQuorum(List<Grid> nodes) {
        var quorumFutures = nodes
                .stream()
                .map(node -> node.awaitMinimumNodes(
                        nodes.size(), Duration.ofSeconds(20)))
                .toList();
        try {
            ConcurrentUtil.get(CompletableFuture.allOf(
                    quorumFutures.toArray(new CompletableFuture[0])));
            LOG.info("Quorum reached across all nodes. Test can proceed.");
        } catch (Exception ex) {
            LOG.error("Failed to reach quorum", ex);
            throw new AssertionError("Failed to reach quorum", ex);
        }
    }
}
