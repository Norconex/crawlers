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
package com.norconex.crawler.core2.stubs;

import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterConnector;
import com.norconex.crawler.core2.mocks.cluster.MockMultiNodesConnector;
import com.norconex.crawler.core2.mocks.cluster.MockSingleNodeConnector;

public final class ClusterStubber {
    private ClusterStubber() {
    }

    public static ClusterConnector singleMemoryNodeClusterConnector() {
        return new MockSingleNodeConnector();
    }

    public static Cluster singleMemoryNodeCluster() {
        return singleMemoryNodeClusterConnector().connect();
    }

    public static ClusterConnector multiMemoryNodesClusterConnector() {
        return new MockMultiNodesConnector();
    }

    public static Cluster multiMemoryNodesCluster() {
        return multiMemoryNodesClusterConnector().connect();
    }

    public static void withSingleMemoryNodeCluster(
            FailableConsumer<Cluster, Exception> c) {
        try (var cluster = singleMemoryNodeCluster()) {
            c.accept(cluster);
        } catch (Exception e) {
            fail("Unexpected exception thrown.", e);
        }
    }

    public static void withMultiMemoryNodesCluster(
            int nodeCount, FailableBiConsumer<Cluster, Integer, Exception> c) {
        try {
            doWithMultiMemoryNodesCluster(nodeCount, c);
        } catch (Exception e) {
            fail("Unexpected exception thrown.", e);
        }
    }

    private static void doWithMultiMemoryNodesCluster(
            int nodeCount, FailableBiConsumer<Cluster, Integer, Exception> c)
            throws Exception {
        var executor = Executors.newFixedThreadPool(nodeCount);
        List<Future<Cluster>> futures = new ArrayList<>();

        for (var i = 0; i < nodeCount; i++) {
            futures.add(executor.submit((Callable<
                    Cluster>) ClusterStubber::multiMemoryNodesCluster));
        }

        List<Cluster> clusters = new ArrayList<>();
        for (Future<Cluster> future : futures) {
            clusters.add(future.get(15, TimeUnit.SECONDS));
        }

        var readyLatch = new CountDownLatch(nodeCount);
        for (var i = 0; i < nodeCount; i++) {
            final var index = i;
            executor.submit(() -> {
                try {
                    c.accept(clusters.get(index), index);
                } catch (Exception e) {
                    fail("Cluster consumer failed to execute.", e);
                } finally {
                    readyLatch.countDown();
                }
            });
        }

        readyLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        for (Cluster cluster : clusters) {
            cluster.close();
        }
    }
}
