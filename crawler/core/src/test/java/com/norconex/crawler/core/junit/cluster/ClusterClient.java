package com.norconex.crawler.core.junit.cluster;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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

    //    private final String h2Url;
    //    private final String dbName;
    //    private final int port;
    //
    //    public ClusterClient(String h2Url, String dbName, int port) {
    //        this.h2Url = h2Url;
    //        this.dbName = dbName;
    //        this.port = port;
    //    }
    //
    //    public String getH2Url() {
    //        return h2Url;
    //    }
    //
    //    public String getDbName() {
    //        return dbName;
    //    }
    //
    //    public int getPort() {
    //        return port;
    //    }
    //
    //    // Example: Launch a JVM node, passing H2 URL and node name
    //    public Process launchNode(String nodeName, File jarFile, String mainClass,
    //            List<String> args) throws Exception {
    //        List<String> cmd = new ArrayList<>();
    //        cmd.add(System.getProperty("java.home") + File.separator + "bin"
    //                + File.separator + "java");
    //        cmd.add("-Dh2.url=" + h2Url);
    //        cmd.add("-Dnode.name=" + nodeName);
    //        cmd.add("-cp");
    //        cmd.add(jarFile.getAbsolutePath());
    //        cmd.add(mainClass);
    //        cmd.addAll(args);
    //        var pb = new ProcessBuilder(cmd);
    //        pb.inheritIO();
    //        return pb.start();
    //    }
    //

    /**
     * Launches a single new crawler node. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     * @param node instructions for launching the node
     * @return the added nodes
     */
    public List<Process> launch(CrawlerNode node) {
        return launch(1, node);
    }

    /**
     * Launches the specified amount of new crawler nodes. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     * @param numOfNodes number of nodes to launch/add
     * @param node instructions for launching each nodes
     * @return the added nodes
     */
    public List<Process> launch(int numOfNodes, CrawlerNode node) {
        List<Process> launchedNodes = new ArrayList<>();
        for (var i = 0; i < numOfNodes; i++) {
            var nodeIndex = nodeCounter.incrementAndGet();
            var nodeName = "node-" + nodeIndex;
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
