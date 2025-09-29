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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;
import org.apache.commons.lang3.time.StopWatch;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import com.norconex.crawler.core.util.ExecUtil;

import io.github.classgraph.ClassGraph;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts one or several Docker containers on the same network for the
 * duration of the JVM. This allows the reuse instances across tests.
 * This boosts test execution speed at the expense of pure test isolation.
 */
@Slf4j
public final class SharedCluster {

    private static final String IMAGE_NAME = "eclipse-temurin:17-jre";
    public static final String NODE_LIB_DIR = "/app/lib";

    private static final List<GenericContainer<?>> NODES = new ArrayList<>();
    private static final Network SHARED_NETWORK = Network.newNetwork();

    public static final Path HOST_LIB_DIR = prepareHostLibDir();

    static {
        // Register a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("JVM Shutdown Hook: Stopping shared containers...");
            NODES.forEach(GenericContainer::close);
            // consider not deleting and keeping files to avoid copying
            // on each test session (JVM invocation).
            FileUtils.deleteQuietly(HOST_LIB_DIR.toFile());
        }));
    }

    private SharedCluster() {
    }

    private static Path prepareHostLibDir() {
        try {
            // Stage all classpath entries into a single folder once.
            var hostLibDir = Files.createTempDirectory("cluster-libs-");
            Files.createDirectories(hostLibDir);

            var watch = StopWatch.createStarted();
            LOG.info("Copying classpath files to {} for containers to use...",
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
            return hostLibDir;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed staging classpath for SharedCluster", e);
        }
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
        var nodes = new ArrayList<GenericContainer<?>>(numOfNodes);
        GenericContainer<?> dependency = null;
        for (var i = 0; i < numOfNodes; i++) {
            var node = getOrCreateContainer(i, dependency);
            dependency = node;
            nodes.add(dependency);
        }
        try {
            LOG.info("Cluster ready ({} nodes).", numOfNodes);
            return f.apply(new SharedClusterClient(nodes, SHARED_NETWORK));
        } catch (Exception e) {
            throw new AssertionError("Execution failed on shared cluster.", e);
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
                .withCommand(
                        "sh", "-c",
                        // Emit a log so we can use a log-based wait strategy
                        // instead of waiting for a listening port.
                        "echo READY && tail -f /dev/null")
                .withNetworkAliases("node" + (listIndex + 1))
                // Avoid default HostPortWaitStrategy (which would wait for
                // exposed ports to be listening). Instead, wait for our
                // READY log line so the container can start immediately.
                .waitingFor(Wait.forLogMessage(".*READY.*", 1));
        if (ExecUtil.isDebugMode()) {
            c.withExposedPorts(5005);
        }
        if (dependency != null) {
            c.dependsOn(dependency);
        }
        return c;
    }
}