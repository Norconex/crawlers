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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core._DELETE.crawler.ClusteredCrawlOuput;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNodeLauncher;
import com.norconex.crawler.core.junit.cluster.node.NodeState;
import com.norconex.crawler.core.util.CoreTestUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlerCluster implements AutoCloseable {

    public static final String CLUSTER_NODE_COUNT = "clusterNodeCount";
    // Reduced from 200ms to 50ms for faster test execution
    public static final int NODE_COUNT_SYNC_MS = 50;
    public static final String CRAWLER_ID_PREFIX = "test-crawl-";

    private static final AtomicInteger clusterCounter = new AtomicInteger();

    @Getter
    private final ClusterState state;
    @Getter
    private final List<CrawlerNode> nodes = new ArrayList<>();
    // Nodes may be added/removed so we use a separate counter to avoid
    // overlaps. Used in creating unique workDirs
    private final AtomicInteger nodeCounter = new AtomicInteger();
    // Number of clusters created within this JVM, to avoid collision
    // Used in creating unique crawler id
    private String crawlerId;
    private int expectedNodeCount = 0;

    private final CrawlerNodeLauncher nodeLauncher;
    private final CrawlConfig crawlConfig;
    private final Path clusterRootDir;
    private final Path configFile;

    public CrawlerCluster(
            CrawlerNodeLauncher nodeLauncher, CrawlConfig crawlConfig) {
        state = new ClusterState(this);
        this.nodeLauncher = nodeLauncher;
        this.crawlConfig = crawlConfig;

        // at this point the crawl work dir is considered the cluster root.
        // each node will overwrite (append) to the root dir to have isolation
        clusterRootDir = Objects.requireNonNull(
                crawlConfig.getWorkDir(), "Working directory is missing.");
        configFile = clusterRootDir.resolve("config.yaml");

        initCrawlerId();
        CoreTestUtil.writeConfigToDir(crawlConfig, configFile);
    }

    /**
     * Launches the specified amount of new crawler nodes. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     * @param numOfNodes number of nodes to launch/add
     * @return the added nodes
     */
    public List<CrawlerNode> launch(int numOfNodes) {
        List<CrawlerNode> launchedNodes = new ArrayList<>();
        for (var i = 0; i < numOfNodes; i++) {
            var nodeIndex = nodeCounter.incrementAndGet();
            launchedNodes.add(nodeLauncher.launch(
                    clusterRootDir.resolve("node-" + nodeIndex),
                    configFile));
        }
        expectedNodeCount += numOfNodes;
        nodes.addAll(launchedNodes);
        return launchedNodes;
    }

    /**
     * Wait for nodes to initialize. Currently uses a fixed delay.
     * @param timeout maximum time to wait
     * @throws InterruptedException if interrupted while waiting
     */
    public void waitForClusterFormation(@NonNull Duration timeout)
            throws InterruptedException {
        var totalWaitMs = timeout.toMillis();
        LOG.info("Waiting {} max for {} nodes to initialize...",
                DurationFormatter.FULL.format(timeout), expectedNodeCount);
        var elapsed = 0L;
        while (elapsed < totalWaitMs) {
            var nodeCount = state.highestNodeIntOrZero(NodeState.NODE_COUNT);
            if (nodeCount >= expectedNodeCount) {
                LOG.info("Expected number of nodes ({}) reached after {}.",
                        expectedNodeCount,
                        DurationFormatter.FULL.format(elapsed));
                return; // Exit early when nodes have joined
            }

            // Check if any nodes have crashed or reported errors
            Sleeper.sleepMillis(NODE_COUNT_SYNC_MS);
            elapsed += NODE_COUNT_SYNC_MS;
        }

        // Only log timeout if we actually timed out
        LOG.error("Timed out after {} waiting for {} nodes to initialize. "
                + "Current node count: {}",
                DurationFormatter.FULL.format(timeout),
                expectedNodeCount,
                state.highestNodeIntOrZero(NodeState.NODE_COUNT));
    }

    public ClusteredCrawlOuput
            waitForClusterTermination(@NonNull Duration timeout) {
        // wait for all processes to be done and return compiled output
        var totalWaitMs = timeout.toMillis();
        LOG.info("Waiting {} max for all nodes to terminate...",
                DurationFormatter.FULL.format(timeout));
        var elapsed = 0L;
        List<CrawlerNode> remainingNodes = new ArrayList<>(nodes);
        while (elapsed < totalWaitMs) {
            remainingNodes.removeIf(n -> !n.getProcess().isAlive());
            if (remainingNodes.isEmpty()) {
                LOG.info("All nodes terminated after {}.",
                        DurationFormatter.FULL.format(elapsed));
                return null; //TODO
            }

            // Check if any nodes have crashed or reported errors
            Sleeper.sleepMillis(NODE_COUNT_SYNC_MS);
            elapsed += NODE_COUNT_SYNC_MS;
        }
        // Only log timeout if we actually timed out
        LOG.error("Timed out after {} waiting for {} remaining nodes to "
                + "terminate.",
                DurationFormatter.FULL.format(timeout),
                state.lowestNodeIntOrZero(NodeState.NODE_COUNT));
        return null; //TODO
    }

    private void initCrawlerId() {
        // Set one if none was passed
        if (crawlConfig != null) {
            if (StringUtils.isBlank(crawlConfig.getId())) {
                crawlerId =
                        CRAWLER_ID_PREFIX + clusterCounter.incrementAndGet();
                crawlConfig.setId(crawlerId);
            } else {
                crawlerId = crawlConfig.getId();
            }
        }
    }

    /**
     * Stops all crawler nodes and releases resources.
     * This should be called when done with the cluster to ensure
     * proper cleanup, especially on Windows where file handles
     * must be released before directories can be deleted.
     */
    @Override
    public void close() {
        LOG.info("Shutting down cluster with {} nodes", nodes.size());

        // Brief delay to allow any in-flight JGroups messages to be processed
        // This reduces the likelihood of TpHeader NPE during shutdown
        Sleeper.sleepMillis(500);

        for (CrawlerNode node : nodes) {
            try {
                node.close();
            } catch (Exception e) {
                LOG.warn("Error closing node at: {}", node.getWorkDir(), e);
            }
        }
        nodes.clear();

        // Wait for file locks to be released by checking if we can
        // access critical files. This is more reliable than arbitrary
        // delays.
        waitForFileLocksToBeReleased();
    }

    /**
     * Actively waits for file locks to be released by attempting to
     * access lock files. This is much more reliable than arbitrary delays.
     * On Windows, Infinispan cache files may remain locked briefly even
     * after process termination.
     */
    private void waitForFileLocksToBeReleased() {
        var maxWaitMs = 5000; // 5 seconds max
        var checkIntervalMs = 100;
        var elapsed = 0L;
        var lockedFiles = findPotentiallyLockedFiles();

        if (lockedFiles.isEmpty()) {
            return; // No problematic files found
        }

        LOG.debug(
                "Waiting for {} potentially locked files to be released",
                lockedFiles.size());

        while (elapsed < maxWaitMs) {
            var stillLocked = checkForLockedFiles(lockedFiles);

            if (stillLocked.isEmpty()) {
                LOG.debug(
                        "All file locks released after {}ms",
                        elapsed);
                return;
            }

            try {
                Thread.sleep(checkIntervalMs); //NOSONAR
                elapsed += checkIntervalMs;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn(
                        "Interrupted while waiting for file locks "
                                + "to be released");
                return;
            }
        }

        var stillLocked = checkForLockedFiles(lockedFiles);
        if (!stillLocked.isEmpty()) {
            LOG.warn(
                    "Timed out waiting for file locks to be released. "
                            + "{} files may still be locked: {}",
                    stillLocked.size(),
                    stillLocked.stream()
                            .map(Path::toString)
                            .limit(5)
                            .toList());
        }
    }

    /**
     * Find files that are typically locked by Infinispan
     * (lock files and index files).
     */
    private List<Path> findPotentiallyLockedFiles() {
        var lockedFiles = new ArrayList<Path>();
        try {
            Files.walk(clusterRootDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        var name = p.getFileName().toString();
                        return name.endsWith(".lck")
                                || name.startsWith("index.");
                    })
                    .forEach(lockedFiles::add);
        } catch (IOException e) {
            LOG.debug("Could not scan for locked files", e);
        }
        return lockedFiles;
    }

    /**
     * Check which files are still locked by trying to open them.
     * Returns the list of files that are still locked.
     */
    private List<Path> checkForLockedFiles(List<Path> filesToCheck) {
        var stillLocked = new ArrayList<Path>();

        for (Path file : filesToCheck) {
            if (!Files.exists(file)) {
                continue; // File was deleted, no longer an issue
            }

            // Try to open the file exclusively to check if it's locked
            try {
                Files.newByteChannel(
                        file,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE).close();
            } catch (IOException e) {
                // File is still locked or inaccessible
                stillLocked.add(file);
            }
        }

        return stillLocked;
    }
}
