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
package com.norconex.grid.core.compute_DELETE;

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
import com.norconex.grid.core.compute_DELETE.GridComputeTask;
import com.norconex.grid.core.impl_DELETE.CoreGrid_ORIG;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.util.ConcurrentUtil;
import com.norconex.grid.core.util.ThreadRenamer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Timeout(60)
public abstract class GridComputeTest extends AbstractGridTest {

    private static final int NUM_NODES = 3;

    @Test
    void runOnOneTest() {

        withNewGrid(cluster -> {
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                LOG.info("Starting task 1/2 on node {}", grid.getNodeName());
                var set = grid.getStorage().getSet("testSet");
                grid.getCompute().runOnOne("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(5);
                LOG.info("Finished task 1/2 on node {}", grid.getNodeName());
            });
            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnOneTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                LOG.info("Starting task 2/2 on node {}", grid.getNodeName());
                here(grid, 1);
                var set = grid.getStorage().getSet("testSet");
                here(grid, 2);
                grid.getCompute().runOnOne("testJob", () -> {
                    here(grid, 3);
                    System.err.println("XXX ONLY ME RUNS 2/2 FILL 5: "
                            + (((CoreGrid_ORIG) grid).isCoordinator() ? "Coordinator"
                                    : "NOT COORDINATOR!!!!!!!!!"));
                    fill(set, 5);

                    System.err.println("XXX COORD FILLED SIZE: " + set.size());
                    here(grid, 4);
                    return null;
                });
                System.err.println(
                        "XXX AFTER RUN ONCE FILLED SIZE: " + set.size());
                here(grid, 5);
                //                //XXX remove this line:
                //                Sleeper.sleepSeconds(4);
                assertThat(set.size()).isEqualTo(10);
                here(grid, 6);
                //                set.clear();
                LOG.info("Finished task 2/2 on node {}", grid.getNodeName());
                here(grid, 7);
            });

        });
    }

    private static void here(Grid grid, int cnt) {
        System.err.println(grid.getNodeName() + " HERE GridComputeTest " + cnt
                + (((CoreGrid_ORIG) grid).isCoordinator() ? " <-- COORD" : ""));
        System.err.flush();
    }

    @Test
    void runOnOneOnceTest() {
        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnOneOnceTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.getStorage().getSet("testSet");
                grid.getCompute().runOnOneOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(5);
            });

            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnOneOnceTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.getStorage().getSet("testSet");
                grid.getCompute().runOnOneOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(5);
                //                set.clear();
            });
        });
    }

    @Test
    void runOnAllTest() {
        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnAllTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.getStorage().getSet("testSet");
                grid.getCompute().runOnAll("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(15);
            });

            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnAllTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {

                var set = grid.getStorage().getSet("testSet");

                grid.getCompute().runOnAll("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(30);
                //                set.clear();
            });
        });
    }

    @Test
    void runOnAllOnceTest() {
        withNewGrid(cluster -> {
            LOG.trace("Running 'runOnAllOnceTest' part 1 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.getStorage().getSet("testSet");
                grid.getCompute().runOnAllOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(15);
            });

            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnAllOnceTest' part 2 of 2");
            cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                var set = grid.getStorage().getSet("testSet");
                grid.getCompute().runOnAllOnce("testJob", () -> {
                    fill(set, 5);
                    return null;
                });
                assertThat(set.size()).isEqualTo(15);
                //                set.clear();
            });
        });
    }

    @Test
    void testRequestStop() {
        assertThatNoException().isThrownBy(() -> {
            withNewGrid(cluster -> {
                cluster.onNewNodes(NUM_NODES, (grid, index) -> {
                    var job = new StoppableJob(grid,
                            () -> grid.getCompute().stop("testJob"));
                    var executor = Executors.newFixedThreadPool(
                            1, grid.getNodeExecutors()
                                    .threadFactory("test-stop-" + index));
                    var future = CompletableFuture.runAsync(
                            ThreadRenamer.set("test-stop", () -> {
                                grid.getCompute().runOnAll("testJob", job);
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
            map = grid.getStorage().getMap("intMap", Integer.class);
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
