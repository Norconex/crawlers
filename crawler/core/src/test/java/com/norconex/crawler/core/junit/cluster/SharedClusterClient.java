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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.function.FailableConsumer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import com.healthmarketscience.jackcess.RuntimeIOException;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts a crawler cluster for the duration of the JVM and reuse instances
 * across tests. A new network is created for each test, but instances
 * remain the same (e.g., node 1 remains node 1).
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public final class SharedClusterClient {

    @NonNull
    private final Network network;
    @NonNull
    private final List<GenericContainer<?>> nodes;
    @NonNull
    private final Path nodeWorkdir;

    public GenericContainer<?> getNode1() {
        return nodes.size() > 0 ? nodes.get(0) : null;
    }

    public GenericContainer<?> getNode2() {
        return nodes.size() > 1 ? nodes.get(1) : null;
    }

    public GenericContainer<?> getNode3() {
        return nodes.size() > 2 ? nodes.get(2) : null;
    }

    public List<CompletableFuture<ExecResult>> execOnCluster(String... args) {
        var futures = new ArrayList<CompletableFuture<ExecResult>>();
        for (var node : nodes) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return node.execInContainer(args);
                } catch (UnsupportedOperationException
                        | IOException
                        | InterruptedException e) {
                    LOG.error("Failed to run on node %s (container: %s): %s"
                            .formatted(
                                    node.getNetworkAliases().get(0),
                                    node.getContainerName(),
                                    String.join(" ", args)));
                    throw ConcurrentUtil.wrapAsCompletionException(e);
                }
            }));
        }
        return futures;
    }

    /**
     * Copy a file on each nodes of this cluster, in a sand box for isolation
     * and return the target full path, which is the same on each node.
     * @param file file to copy
     * @return path to file on remote nodes
     */
    public Path copyFileToCluster(Path file) {
        return copyFileToCluster(file, null);
    }

    /**
     * Copy a file on each nodes of this cluster, in a sand box for isolation
     * and return the target full path, which is the same on each node.
     * @param file file to copy
     * @param filename optional file name (otherwise random)
     * @return path to file on remote nodes
     */
    public Path copyFileToCluster(Path file, String filename) {
        var targetFile = nodeWorkdir.resolve(
                filename != null ? filename : "" + TimeIdGenerator.next());
        forEachNode(node -> {
            node.copyFileToContainer(
                    MountableFile.forHostPath(file),
                    targetFile.toString().replace('\\', '/'));
        });
        return targetFile;
    }

    /**
     * Copy a string into a file on each nodes of this cluster, in a sand box
     * for isolation and return the target full path, which is the same on
     * each node.
     * @param content string to save in a remote file
     * @return path to file on remote nodes
     */
    public Path copyStringToClusterFile(String content) {
        return copyStringToClusterFile(content, null);
    }

    /**
     * Copy a string into a file on each nodes of this cluster, in a sand box
     * for isolation and return the target full path, which is the same on
     * each node.
     * @param content string to save in a remote file
     * @param filename optional file name (otherwise random)
     * @return path to file on remote nodes
     */
    public Path copyStringToClusterFile(String content, String filename) {
        Path tmpFile = null;
        try {
            tmpFile = Files.writeString(
                    Files.createTempFile(null, null), content);
            return copyFileToCluster(tmpFile, filename);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        } finally {
            var f = tmpFile;
            if (f != null) {
                ExceptionSwallower.swallow(() -> Files.deleteIfExists(f));
            }
        }
    }

    private void forEachNode(
            FailableConsumer<GenericContainer<?>, Exception> c) {

        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var node : nodes) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    c.accept(node);
                } catch (Exception e) {
                    throw ConcurrentUtil.wrapAsCompletionException(e);
                }
            }));
        }
        try {
            ConcurrentUtil.allOf(futures).get();
        } catch (Exception e) {
            throw ConcurrentUtil.wrapAsCompletionException(e);
        }

    }
}
