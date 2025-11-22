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
    private StateDbServer stateDbServer;

    public ClusterClient(CrawlConfig crawlConfig) {
        this.crawlConfig = crawlConfig;

        // at this point the crawl work dir is considered the cluster root.
        // each node will overwrite (append) to the root dir to have isolation
        clusterRootDir = Objects.requireNonNull(
                crawlConfig.getWorkDir(), "Working directory is missing.");
        configFile = clusterRootDir.resolve("config.yaml");
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
        var nodeNames = new String[numOfNodes];
        for (var i = 0; i < numOfNodes; i++) {
            nodeNames[i] = "node-" + nodeCounter.incrementAndGet();
        }
        return launch(node, nodeNames);
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

        ensureServerInit();

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
            LOG.debug("Launched \"{}\".", nodeName);
        }
        nodes.addAll(launchedNodes);
        return launchedNodes;
    }

    public WaitFor waitFor() {
        return waitFor(null);
    }

    public WaitFor waitFor(Duration timeout) {
        return new WaitFor(
                ofNullable(timeout).orElseGet(() -> Duration.ofSeconds(30)),
                this);
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
        Sleeper.sleepMillis(250);

        for (Process process : nodes) {
            try {
                if (process != null && process.isAlive()) {
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
    }

    private void ensureServerInit() {
        if (stateDbServer == null) {

            // Set a crawler ID one if none was passed
            if (crawlConfig != null) {
                if (StringUtils.isBlank(crawlConfig.getId())) {
                    crawlerId =
                            CRAWLER_ID_PREFIX
                                    + clusterCounter.incrementAndGet();
                    crawlConfig.setId(crawlerId);
                } else {
                    crawlerId = crawlConfig.getId();
                }
            }

            // Write configuration
            CoreTestUtil.writeConfigToFile(crawlConfig, configFile);

            // Start state server
            var dbName = "testdb_" + TimeIdGenerator.next();
            stateDbServer = new StateDbServer(clusterRootDir, dbName);
            stateDbServer.start();
        }
    }
}
