package com.norconex.crawler.core.junit.cluster;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.junit.cluster.state.StateDbClient;
import com.norconex.crawler.core.junit.cluster.state.StateDbServer;
import com.norconex.crawler.core.util.CoreTestUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClusterClient implements AutoCloseable {

    public static final String CRAWLER_ID_PREFIX = "test-crawl-";
    // To ensure cluster uniqueness
    private static final AtomicInteger clusterCounter = new AtomicInteger();
    // To ensure node uniqueness within this client instance
    private final AtomicInteger nodeCounter = new AtomicInteger();

    private String crawlerId;
    private final CrawlConfig crawlConfig;
    private final Path clusterRootDir;
    @Getter
    private final Path configFile;
    @Getter
    private final List<Process> nodes = new ArrayList<>();
    //    private final List<NodeExecutionResult> nodes = new ArrayList<>();
    private final StateDbServer stateDbServer;

    public ClusterClient(CrawlConfig crawlConfig) {
        //        state = new ClusterState(this);
        this.crawlConfig = crawlConfig;

        // at this point the crawl work dir is considered the cluster root.
        // each node will overwrite (append) to the root dir to have isolation
        clusterRootDir = Objects.requireNonNull(
                crawlConfig.getWorkDir(), "Working directory is missing.");
        configFile = clusterRootDir.resolve("config.yaml");

        initCrawlerId();

        CoreTestUtil.writeConfigToFile(crawlConfig, configFile);

        var dbName = "testdb_" + TimeIdGenerator.next();
        stateDbServer = new StateDbServer(clusterRootDir, dbName);
        stateDbServer.start();
    }

    public StateDbClient getStateDb() {
        return StateDbClient.get();
    }

    /**
     * Launches a single new crawler node. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     * A node name will be assigned internally as "node-&lg;counter&gt;", where
     * counter is an incremented value starting at 1. If you
     * need to refer to nodes by explicit names, use
     * {@link #launch(CrawlerNode, String...)} instead.
     * @param node instructions for launching the node
     * @return the added nodes
     */
    public List<Process> launch(CrawlerNode node) {
        return launch(node, 1);
    }

    /**
     * Launches the specified amount of new crawler nodes. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     * A node name will be assigned internally as "node-&lg;counter&gt;", where
     * counter is an incremented value starting at 1. If you
     * need to refer to nodes by explicit names, use
     * {@link #launch(CrawlerNode, String...)} instead.
     * @param node instructions for launching each nodes
     * @param numOfNodes number of nodes to launch/add
     * @return the added nodes
     */
    public List<Process> launch(CrawlerNode node, int numOfNodes) {
        List<Process> launchedNodes = new ArrayList<>();
        for (var i = 0; i < numOfNodes; i++) {
            var nodeIndex = nodeCounter.incrementAndGet();
            var nodeName = "node-" + nodeIndex;
            launchedNodes.addAll(launch(node, nodeName));
        }
        nodes.addAll(launchedNodes);
        return launchedNodes;
    }

    /**
     * Launches a new crawler node for each node names specified. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     * @param node instructions for launching each nodes
     * @param nodeNames names of each nodes (must be unique and non-blank)
     * @return the added nodes
     */
    public List<Process> launch(
            @NonNull CrawlerNode node, @NonNull String... nodeNames) {

        if (Arrays.stream(nodeNames).anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Node name must not be null.");
        }
        if (Arrays.stream(nodeNames).distinct().count() != nodeNames.length) {
            throw new IllegalArgumentException("Node names must be unique.");
        }

        List<Process> launchedNodes = new ArrayList<>();
        for (String nodeName : nodeNames) {
            launchedNodes.add(node.launch(
                    nodeName,
                    clusterRootDir.resolve(nodeName),
                    configFile));
        }
        nodes.addAll(launchedNodes);
        return launchedNodes;
    }

    public WaitFor waitFor() {
        return waitFor(null);
    }

    public WaitFor waitFor(Duration timeout) {
        return new WaitFor(
                ofNullable(timeout).orElseGet(() -> Duration.ofMinutes(10)),
                this);
    }

    /**
     * Returns the exit values of all launched nodes. If a process is still
     * alive, it will first wait up to the given timeout for it to exit.
     * <p>
     * This is intended for tests to assert that all nodes terminated
     * successfully (exit code 0) once the cluster run is complete.
     * </p>
     * @param timeout how long to wait for each alive process
     * @return list of exit values in the same order as {@link #getNodes()}
     */
    public List<Integer> getNodeExitValues(Duration timeout) {
        var wait = ofNullable(timeout)
                .orElseGet(() -> Duration.ofSeconds(5));
        var values = new ArrayList<Integer>(nodes.size());
        for (var process : nodes) {
            if (process == null) {
                values.add(-1);
                continue;
            }
            if (process.isAlive()) {
                try {
                    process.waitFor(wait.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while waiting for node to exit.",
                            e);
                }
            }
            values.add(process.exitValue());
        }
        return values;
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

        //        for (NodeExecutionResult node : nodes) {
        //            try {
        //                node.close();
        //            } catch (Exception e) {
        //                LOG.warn("Error closing node at: {}", node.getWorkDir(), e);
        //            }
        //        }
        for (Process process : nodes) {
            try {
                if (process != null && process.isAlive()) {
                    // LOG.debug("Stopping crawler node at: {}", workDir);
                    process.destroy();
                    try {
                        // Wait up to 10 seconds for graceful shutdown
                        if (!process.waitFor(10, TimeUnit.SECONDS)) {
                            LOG.warn("Node did not stop gracefully, "
                                    + "forcing termination");
                            process.destroyForcibly();
                            // Wait for forcible termination to complete
                            process.waitFor(5, TimeUnit.SECONDS);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Interrupted while waiting for node to stop",
                                e);
                        process.destroyForcibly();
                    }
                }
                // Close all streams to release file handles
                // (important on Windows for file deletion)
                if (process != null)

                {
                    ExceptionSwallower.closeQuietly(
                            process.getOutputStream(),
                            process.getInputStream(),
                            process.getErrorStream());
                }

            } catch (Exception e) {
                LOG.warn("Error closing node.", e);
            }
        }
        nodes.clear();

        stateDbServer.stop();

        // Wait for file locks to be released by checking if we can
        // access critical files. This is more reliable than arbitrary
        // delays.
        //        waitForFileLocksToBeReleased();
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
}
