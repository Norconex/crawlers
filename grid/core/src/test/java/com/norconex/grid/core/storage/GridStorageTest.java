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
package com.norconex.grid.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.GridConnector;

/**
 * Main test class to implement for grid implementations.
 */
public abstract class GridStorageTest extends AbstractGridTest {

    @Nested
    class MapTest extends GridMapTest {
        @Override
        protected GridConnector getGridConnector(String gridName) {
            return GridStorageTest.this.getGridConnector(gridName);
        }
    }

    @Nested
    class QueueTest extends GridQueueTest {
        @Override
        protected GridConnector getGridConnector(String gridName) {
            return GridStorageTest.this.getGridConnector(gridName);
        }
    }

    @Nested
    class SetTest extends GridSetTest {
        @Override
        protected GridConnector getGridConnector(String gridName) {
            return GridStorageTest.this.getGridConnector(gridName);
        }
    }

    @Test
    void testRunInTransaction() {
        withNewGrid(cluster -> {
            cluster.onNewNode(grid -> {
                var storage = grid.storage();
                var map = storage.getMap("transactTestMap", Integer.class);

                var numThreads = 5;
                var executor = Executors.newFixedThreadPool(numThreads);
                var startLatch = new CountDownLatch(1);
                var doneLatch = new CountDownLatch(numThreads);
                var successfulTransactions = new AtomicInteger(0);

                for (var i = 0; i < numThreads; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            storage.runInTransaction(() -> {
                                map.update("count", v -> v == null ? 1 : v + 1);
                                return null;
                            });
                            successfulTransactions.incrementAndGet();
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                doneLatch.await();
                executor.shutdown();

                // Check if the final value is correct (ensuring atomic
                // increments)
                assertThat(map.get("count")).isEqualTo(numThreads);
            });
        });
    }

    @Test
    void testRunInTransactionAsync() {
        withNewGrid(cluster -> {
            cluster.onNewNode(grid -> {
                var storage = grid.storage();
                var map = storage.getMap("transactAsyncTestMap", Integer.class);

                var numThreads = 5;
                var executor = Executors.newFixedThreadPool(numThreads);
                var startLatch = new CountDownLatch(1);
                var doneLatch = new CountDownLatch(numThreads);
                new AtomicInteger(0);

                List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();

                for (var i = 0; i < numThreads; i++) {
                    Future<Boolean> future = executor.submit(() -> {
                        try {
                            // Ensure all threads start together
                            startLatch.await();
                            return storage.runInTransactionAsync(() -> {
                                map.update("count", v -> v == null ? 1 : v + 1);
                                return true;
                            }).get(); // Wait for async completion
                        } catch (Exception e) {
                            e.printStackTrace();
                            return false;
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                    futures.add(future);
                }

                startLatch.countDown();
                doneLatch.await(); // Wait for all transactions to complete
                executor.shutdown();

                // Ensure all async transactions completed successfully
                for (Future<Boolean> future : futures) {
                    // Ensure the transaction didn't fail
                    assertThat(future.get()).isTrue();
                }

                // Check final value correctness
                assertThat(map.get("count")).isEqualTo(numThreads);
            });
        });
    }
}
