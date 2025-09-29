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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts a crawler cluster for the duration of the JVM and reuse instances
 * across tests. A new network is created for each test, but instances
 * remain the same (e.g., node 1 remains node 1).
 */
@Slf4j
@Getter
public final class SharedClusterClient {

    private final Network network;
    private final List<GenericContainer<?>> nodes;

    public SharedClusterClient(
            @NonNull List<GenericContainer<?>> nodes,
            @NonNull Network network) {
        this.network = network;
        this.nodes = nodes;
    }

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

}