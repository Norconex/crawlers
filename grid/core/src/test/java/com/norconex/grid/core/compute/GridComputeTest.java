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
package com.norconex.grid.core.compute;

import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.util.ConcurrentUtil;
import com.norconex.grid.core.util.ThreadRenamer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class GridComputeTest extends AbstractGridTest {

    private static final int NUM_NODES = 3;

    @Test
    @Timeout(20)
    void runOnOneTest() {

        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnOneTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnOne("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(5);
            });

            cluster.disconnect();

            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnOneTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnOne("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(10);
                set.clear();
            });

        });
    }

    @Test
    @Timeout(20)
    void runOnOneOnceTest() {
        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnOneOnceTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnOneOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(5);
            });

            cluster.disconnect();

            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnOneOnceTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnOneOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(5);
                set.clear();
            });
        });
    }

    @Test
    @Timeout(20)
    void runOnAllTest() {
        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnAllTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnAll("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(15);
            });

            cluster.disconnect();

            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnAllTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnAll("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(30);
                set.clear();
            });
        });
    }

    @Test
    @Timeout(20)
    void runOnAllOnceTest() {
        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnAllOnceTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnAllOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(15);
            });

            cluster.disconnect();

            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnAllOnceTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.storage().getSet("testSet");
                grid.compute().runOnAllOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(15);
                set.clear();
            });
        });
    }

    @Test
    @Timeout(20)
    void testRequestStop() {
        assertThatNoException().isThrownBy(() -> {
            withNewGrid(cluster -> {
                cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                    var job = new StoppableJob(grid,
                            () -> grid.compute().stop("testJob"));
                    var executor = Executors.newFixedThreadPool(
                            1, grid.getNodeExecutors()
                                    .threadFactory("test-stop-" + index));
                    var future = CompletableFuture.runAsync(
                            ThreadRenamer.set("test-stop", () -> {
                                grid.compute().runOnAll("testJob", job);
                            }), executor);
                    ConcurrentUtil.getUnderSecs(future, 10);
                });
            });
        });
    }

    private static final class StoppableJob
            implements GridComputeTask<Serializable> {
        private final GridMap<Integer> map;
        private final Runnable onAllStarted;
        private boolean stopRequested;

        public StoppableJob(Grid grid, Runnable onAllStarted) {
            map = grid.storage().getMap("intMap", Integer.class);
            this.onAllStarted = onAllStarted;
        }

        @Override
        public Serializable execute() {
            map.update("numStarted", v -> v == null ? 1 : v + 1);
            while (getNumStarted() < 3) {
                Sleeper.sleepMillis(100);
            }
            onAllStarted.run();
            while (!stopRequested) {
                Sleeper.sleepMillis(100);
            }
            return null;
        }

        private int getNumStarted() {
            return ofNullable(map.get("numStarted")).orElse(0);
        }

        @Override
        public void stop() {
            stopRequested = true;
        }
    }

    private void fill(GridSet set, int numEntries) {
        for (var i = 0; i < numEntries; i++) {
            set.add(UUID.randomUUID().toString());
        }
    }
}
