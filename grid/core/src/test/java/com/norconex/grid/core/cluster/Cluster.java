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
package com.norconex.grid.core.cluster;

import java.io.Closeable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.grid.core.BaseGridConnectionContext;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.TestTaskContext;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Local cluster used for testing.
 * @see WithCluster
 */
@Slf4j
public class Cluster implements Closeable {

    // <..., done>
    private final ListOrderedMap<Grid, Boolean> nodes = new ListOrderedMap<>();
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
        gridName = "test_grid_" + clusterCount.incrementAndGet();
    }

    @Override
    public void close() {
        // Grab the last node (likely most recent) and destroy storage used
        // for testing
        try {
            if (!nodes.isEmpty()) {
                nodes.get(nodes.size() - 1).getStorage().destroy();
            }
        } finally {
            nodes.keySet().forEach(Grid::close);
            nodes.clear();
            tempDir = null;
        }
    }

    public Grid getLastNodeCreated() {
        if (!nodes.isEmpty()) {
            return nodes.get(nodes.size() - 1);
        }
        throw new GridException("No nodes available.");
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
        var taskFailure = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(nodes.size());
        var execFutures = nodes
                .stream()
                .map(node -> CompletableFuture.runAsync(
                        () -> {
                            try {
                                nodeConsumer.accept(node);
                            } catch (Exception e) {
                                LOG.error("Test cluster error.", e);
                                taskFailure.set(e);
                            } finally {
                                // mark the node as done
                                if (Cluster.this.nodes.containsKey(node)) {
                                    Cluster.this.nodes.put(node, true);
                                }
                            }
                        }, executor))
                .toList();

        try {
            CompletableFuture.allOf(
                    execFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, ex) -> {
                        if (ex != null && taskFailure.get() == null) {
                            taskFailure.set(ex);
                        }
                    }).get(30, TimeUnit.SECONDS);
            LOG.info("All nodes ran without known issues.");
        } catch (Exception e) {
            if (e.getCause() instanceof AssertionError assError) {
                throw assError;
            }
            throw new AssertionError("Failed to execute on nodes.", e);
        } finally {
            executor.shutdown();
        }
        if (taskFailure.get() != null) {
            var e = taskFailure.get();
            if (e.getCause() instanceof AssertionError assError) {
                throw assError;
            }
            throw new AssertionError(
                    "At least one node threw an exception.",
                    taskFailure.get());
        }
    }

    /*
     * Create new nodes, connect to them, and add them to cluster-wide
     * list of nodes (in addition to returning those just created)..
     */
    private List<Grid> createNewNodes(int numNodes) {

        // if there are already nodes on this cluster that are done running,
        // close them and stop tracking them, but don't wipe out storage yet.
        for (var it = nodes.entrySet().iterator(); it.hasNext();) {
            var en = it.next();
            if (Boolean.TRUE.equals(en.getValue())) {
                en.getKey().close(); // Perform any necessary cleanup
                it.remove(); // Safe removal via iterator
            }
        }

        List<Grid> newNodes = new ArrayList<>();
        for (var i = 0; i < numNodes; i++) {
            var nodeName = gridName + "-node-" + nodeCount.incrementAndGet();
            var gridCtx = new BaseGridConnectionContext(tempDir, gridName);
            var node = connectorFactory.create(gridCtx, nodeName).connect(
                    gridCtx);
            node.init(Map.of("default", grid -> new TestTaskContext()));
            newNodes.add(node);
            nodes.put(node, false);
        }
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
            LOG.debug("Quorum reached across all nodes. Test can proceed.");
        } catch (Exception ex) {
            LOG.error("Failed to reach quorum", ex);
            throw new AssertionError("Failed to reach quorum", ex);
        }
    }
}
