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
package com.norconex.crawler.core2.cluster.impl.infinispan;

import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.commons.lang.config.Configurable;

public final class InfinispanTestUtil {
    private InfinispanTestUtil() {
    }

    public static InfinispanCluster singleMemoryNodeCluster() {
        return Configurable.configure(new InfinispanCluster(),
                c -> c.setInfinispan(InfinispanUtil.configBuilderHolder(
                        "/cache/infinispan-single-test.xml")));
    }

    public static InfinispanCluster multiMemoryNodesCluster() {
        return Configurable.configure(new InfinispanCluster(),
                c -> c.setInfinispan(InfinispanUtil.configBuilderHolder(
                        "/cache/infinispan-cluster-test.xml")));
    }

    public static void withSingleMemoryNodeCluster(
            FailableConsumer<InfinispanCluster, Exception> c) {
        try (var cluster = singleMemoryNodeCluster()) {
            c.accept(cluster);
        } catch (Exception e) {
            fail("Unexpected exception thrown.", e);
        }
    }

    public static void withMultiMemoryNodesCluster(
            int nodeCount,
            FailableBiConsumer<InfinispanCluster, Integer, Exception> c) {
        try {
            doWithMultiMemoryNodesCluster(nodeCount, c);
        } catch (Exception e) {
            fail("Unexpected exception thrown.", e);
        }
    }

    private static void doWithMultiMemoryNodesCluster(
            int nodeCount,
            FailableBiConsumer<InfinispanCluster, Integer, Exception> c)
            throws Exception {
        var executor = Executors.newFixedThreadPool(nodeCount);
        List<Future<InfinispanCluster>> futures = new ArrayList<>();

        for (var i = 0; i < nodeCount; i++) {
            futures.add(executor.submit(() -> {
                var cluster = multiMemoryNodesCluster();
                cluster.init(null);
                return cluster;
            }));
        }

        List<InfinispanCluster> clusters = new ArrayList<>();
        for (Future<InfinispanCluster> future : futures) {
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

        for (InfinispanCluster cluster : clusters) {
            cluster.close();
        }
    }
}
