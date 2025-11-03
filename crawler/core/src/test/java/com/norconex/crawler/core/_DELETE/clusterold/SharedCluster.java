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
package com.norconex.crawler.core._DELETE.clusterold;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;
import org.apache.commons.lang3.time.StopWatch;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;
import com.norconex.crawler.core.util.ExecUtil;

import io.github.classgraph.ClassGraph;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts one or several Docker containers on the same network for the
 * duration of the JVM. This allows the reuse of a cluster across tests.
 * This boosts test execution speed at the expense of pure test isolation.
 * Isolation is attempted by ensuring unique crawler id and working directories
 * between tests.
 */
@Slf4j
public final class SharedCluster {

    private static final String IMAGE_NAME = "eclipse-temurin:17-jre";
    public static final String NODE_LIB_DIR = "/app/lib";
    public static final String NODE_BASE_WORKDIR = "/app/work";

    private static final List<GenericContainer<?>> NODES = new ArrayList<>();
    private static final Network SHARED_NETWORK = Network.newNetwork();

    private static volatile Path cachedHostLibDir;
    public static final Path HOST_LIB_DIR = getOrPrepareHostLibDir();

    static {
        // Register a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("JVM Shutdown Hook: Stopping shared containers...");
            NODES.forEach(GenericContainer::close);
            // consider not deleting and keeping files to avoid copying
            // on each test session (JVM invocation).
            if (cachedHostLibDir != null) {
                FileUtils.deleteQuietly(cachedHostLibDir.toFile());
            }
        }));
    }

    private SharedCluster() {
    }

    /**
     * Builds the classpath that must be used inside the container.
     * It contains all staged directories and a wildcard for all jars.
     * @return ready to use classpath
     */
    public static String buildNodeClasspath() {
        try {
            var entries = Files.list(HOST_LIB_DIR)
                    .filter(Files::isDirectory)
                    .map(p -> NODE_LIB_DIR + "/" + p.getFileName())
                    .toList();
            var cp = new StringBuilder(NODE_LIB_DIR + "/*");
            for (var e : entries) {
                cp.append(":").append(e);
            }
            return cp.toString();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed building node classpath.", e);
        }
    }

    public static synchronized void withNodes(
            int numOfNodes,
            @NonNull FailableConsumer<SharedClusterClient, Exception> c) {
        withNodesAndGet(numOfNodes, client -> {
            c.accept(client);
            return null;
        });
    }

    public static synchronized <T> T withNodesAndGet(
            int numOfNodes,
            @NonNull FailableFunction<SharedClusterClient, T, Exception> f) {

        var workdir = Path.of(NODE_BASE_WORKDIR, "" + TimeIdGenerator.next());
        var nodes = new ArrayList<GenericContainer<?>>(numOfNodes);
        GenericContainer<?> dependency = null;
        for (var i = 0; i < numOfNodes; i++) {
            var node = getOrCreateContainer(i, dependency);
            dependency = node;
            // Clean any previous workdir remnants before creating new one
            execInContainer(node, "rm", "-rf", workdir);
            execInContainer(node, "mkdir", "-p", workdir);
            nodes.add(node);
        }
        for (var n : nodes) {
            LOG.info("SharedCluster node idx={} containerId={} name={} "
                    + "createdWorkdir={}",
                    nodes.indexOf(n), n.getContainerId(), n.getContainerName(),
                    workdir.toString().replace('\\', '/'));
        }
        try {
            LOG.info("Cluster ready ({} nodes).", numOfNodes);
            var watch = StopWatch.createStarted();
            var t = f.apply(
                    new SharedClusterClient(SHARED_NETWORK, nodes, workdir));
            LOG.info("Cluster execution ran for: {}", watch.formatTime());
            return t;
        } catch (Exception e) {
            throw new AssertionError("Execution failed on shared cluster.", e);
        } finally {
            nodes.forEach(node -> ExceptionSwallower.swallow(
                    () -> execInContainer(node, "rm", "-fr", workdir),
                    "Could not delete node workdir:" + workdir));
        }
    }

    private static Path getOrPrepareHostLibDir() {
        // Return cached directory if already prepared
        if (cachedHostLibDir != null && Files.exists(cachedHostLibDir)) {
            LOG.info("Reusing existing classpath directory: {}",
                    cachedHostLibDir);
            return cachedHostLibDir;
        }

        synchronized (SharedCluster.class) {
            // Double-check after acquiring lock
            if (cachedHostLibDir != null && Files.exists(cachedHostLibDir)) {
                return cachedHostLibDir;
            }

            try {
                // Stage all classpath entries into a single folder once.
                var hostLibDir = Files.createTempDirectory("cluster-libs-");
                Files.createDirectories(hostLibDir);

                var watch = StopWatch.createStarted();
                LOG.info(
                        "Copying classpath files to {} for containers to use...",
                        hostLibDir);
                var scan = new ClassGraph().scan();
                var files = scan.getClasspathFiles();
                var idx = 0;
                for (var f : files) {
                    var src = f.toPath();
                    var baseName = src.getFileName().toString();
                    var uniqueName = Files.isDirectory(src)
                            ? ("cp-" + (idx++) + "-" + baseName)
                            : baseName;
                    var dest = hostLibDir.resolve(uniqueName);
                    if (Files.isDirectory(src)) {
                        // Recursively copy directories (e.g., classes, test-classes)
                        if (Files.exists(dest)) {
                            FileUtils.deleteQuietly(dest.toFile());
                        }
                        FileUtils.copyDirectory(src.toFile(), dest.toFile());
                    } else // Only copy if missing or changed in size (quick heuristic)
                    if (!Files.exists(dest) ||
                            Files.size(dest) != Files.size(src)) {
                        Files.copy(src, dest,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                LOG.info("Copied classpath files in {}", watch.formatTime());
                cachedHostLibDir = hostLibDir;
                return hostLibDir;
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed staging classpath for SharedCluster", e);
            }
        }
    }

    private static GenericContainer<?> getOrCreateContainer(
            int idx, GenericContainer<?> dependency) {
        if (NODES.size() > idx) {
            var existing = NODES.get(idx);
            if (existing != null && existing.isRunning()) {
                return existing;
            }
            try {
                if (existing != null) {
                    LOG.info("Node{} not running; recreating it.", idx + 1);
                    existing.close();
                }
            } catch (Exception e) {
                LOG.warn("Failed closing existing node{}: {}",
                        idx + 1, e.getMessage());
            }
            var repl = newNodeContainer(idx, dependency);
            repl.start();
            NODES.set(idx, repl);
            return repl;
        }
        var container = newNodeContainer(idx, dependency);
        container.start();
        NODES.add(container);
        return container;
    }

    private static GenericContainer<?> newNodeContainer(
            int listIndex, GenericContainer<?> dependency) {
        @SuppressWarnings("resource")
        var c = new GenericContainer<>(IMAGE_NAME)
                .withNetwork(SHARED_NETWORK)
                .withFileSystemBind(
                        HOST_LIB_DIR.toAbsolutePath().toString(),
                        NODE_LIB_DIR,
                        BindMode.READ_ONLY)
                .withEnv("INFINISPAN_NODE_NAME", "node" + (listIndex + 1))
                .withCommand(
                        "sh", "-c",
                        "echo READY && tail -f /dev/null")
                .withNetworkAliases("node" + (listIndex + 1))
                .waitingFor(Wait.forLogMessage(".*READY.*", 1))
                // Enable log following to see container output in real-time
                .withLogConsumer(outputFrame -> {
                    var nodeName = "node" + (listIndex + 1);
                    var logLine = outputFrame.getUtf8String().trim();
                    if (!logLine.isEmpty()) {
                        // Prefix each log line with node name for identification
                        System.out.println("[" + nodeName + "] " + logLine);
                    }
                });
        if (ExecUtil.isDebugMode()) {
            // Map container port 5005 to host port 5005 + node index
            var hostDebugPort = 5005 + listIndex;
            c.withExposedPorts(5005);
            c.setPortBindings(List.of(hostDebugPort + ":5005"));
            LOG.warn("Node {} debug port mapped to localhost:{}",
                    listIndex + 1, hostDebugPort);
        }
        if (dependency != null) {
            c.dependsOn(dependency);
        }
        return c;
    }

    private static void execInContainer(
            GenericContainer<?> node, Object... args) {
        try {
            node.execInContainer(
                    Stream.of(args).map(arg -> Objects.toString(arg, null))
                            .toArray(String[]::new));
        } catch (UnsupportedOperationException | IOException
                | InterruptedException e) {
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }
    }
}
