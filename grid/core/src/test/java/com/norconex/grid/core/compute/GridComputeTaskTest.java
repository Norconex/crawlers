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

import org.jgroups.util.UUID;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.cluster.Cluster;
import com.norconex.grid.core.cluster.ClusterTest;
import com.norconex.grid.core.compute.BaseGridTask.AllNodesOnceTask;
import com.norconex.grid.core.compute.BaseGridTask.AllNodesTask;
import com.norconex.grid.core.compute.BaseGridTask.SingleNodeOnceTask;
import com.norconex.grid.core.compute.BaseGridTask.SingleNodeTask;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridSet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Timeout(60)
public abstract class GridComputeTaskTest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ClusterTest
    void runOnOneTest(Cluster cluster) {
        cluster.onThreeNewNodes(grid -> {
            LOG.debug("Starting task 1/2 on node {}", grid.getNodeName());
            grid.getCompute().executeTask(new SingleNodeTask("testJob") {
                private static final long serialVersionUID = 1L;

                @Override
                public Serializable execute(Grid grid) {
                    return fillFive(grid);
                }
            });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(5);
            LOG.debug("Finished task 1/2 on node {}", grid.getNodeName());
        });

        // we are allowed to run it again so the numbers should add up.
        cluster.onThreeNewNodes(grid -> {
            LOG.debug("Starting task 2/2 on node {}", grid.getNodeName());
            grid.getCompute().executeTask(new SingleNodeTask("testJob") {
                private static final long serialVersionUID = 1L;

                @Override
                public Serializable execute(Grid grid) {
                    return fillFive(grid);
                }
            });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(10);
            LOG.debug("Finished task 2/2 on node {}", grid.getNodeName());
        });

    }

    @ClusterTest
    void runOnOneOnceTest(Cluster cluster) {
        cluster.onThreeNewNodes(grid -> {
            LOG.trace("Running 'runOnOneOnceTest' part 1 of 2");
            var status = grid.getCompute().executeTask(
                    new SingleNodeOnceTask("testJob") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public Serializable execute(Grid grid) {
                            return fillFive(grid);
                        }
                    });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(5);
            assertThat(status.getState()).isSameAs(TaskState.COMPLETED);
        });

        cluster.onThreeNewNodes(grid -> {
            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnOneOnceTest' part 2 of 2");
            var status = grid.getCompute().executeTask(
                    new SingleNodeOnceTask("testJob") {

                        private static final long serialVersionUID = 1L;

                        @Override
                        public Serializable execute(Grid grid) {
                            return fillFive(grid);
                        }
                    });

            var set = getGridSet(grid);

            assertThat(set.size()).isEqualTo(5);
            assertThat(status.getState()).isSameAs(TaskState.FAILED);
        });

    }

    @ClusterTest
    void runOnAllTest(Cluster cluster) {
        cluster.onThreeNewNodes(grid -> {
            LOG.trace("Running 'runOnAllTest' part 1 of 2");
            var status = grid.getCompute().executeTask(
                    new AllNodesTask("testJob") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public Serializable execute(Grid grid) {
                            return fillFive(grid);
                        }
                    });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(15);
            assertThat(status.getState()).isSameAs(TaskState.COMPLETED);
        });

        cluster.onThreeNewNodes(grid -> {
            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnAllTest' part 2 of 2");
            var status = grid.getCompute().executeTask(
                    new AllNodesTask("testJob") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public Serializable execute(Grid grid) {
                            return fillFive(grid);
                        }
                    });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(30);
            assertThat(status.getState()).isSameAs(TaskState.COMPLETED);
        });
    }

    @ClusterTest
    void runOnAllOnceTest(Cluster cluster) {
        cluster.onThreeNewNodes(grid -> {
            LOG.trace("Running 'runOnAllOnceTest' part 1 of 2");
            var status = grid.getCompute().executeTask(
                    new AllNodesOnceTask("testJob") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public Serializable execute(Grid grid) {
                            return fillFive(grid);
                        }
                    });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(15);
            assertThat(status.getState()).isSameAs(TaskState.COMPLETED);
        });

        cluster.onThreeNewNodes(grid -> {
            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnAllOnceTest' part 2 of 2");
            var status = grid.getCompute().executeTask(
                    new AllNodesOnceTask("testJob") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public Serializable execute(Grid grid) {
                            return fillFive(grid);
                        }
                    });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(15);
            assertThat(status.getState()).isSameAs(TaskState.FAILED);
        });
    }

    @ClusterTest
    void testRequestStop(Cluster cluster) {
        // if the stop request is not received, the test will timeout
        assertThatNoException().isThrownBy(() -> {
            cluster.onThreeNewNodes(grid -> {
                var job = new StoppableTask("testJob", ExecutionMode.ALL_NODES);
                grid.getCompute().executeTask(job);
            });
        });
        assertThatNoException().isThrownBy(() -> {
            cluster.onThreeNewNodes(grid -> {
                var job = new StoppableTask(
                        "testJob", ExecutionMode.SINGLE_NODE);
                grid.getCompute().executeTask(job);
            });
        });
    }

    //--- Private --------------------------------------------------------------

    private static final class StoppableTask extends BaseGridTask {
        private static final long serialVersionUID = 1L;
        private boolean stopRequested;
        private volatile GridMap<Integer> map;

        public StoppableTask(String id, ExecutionMode executionMode) {
            super(id, executionMode);
        }

        @Override
        public Serializable execute(Grid grid) {
            map = grid.getStorage().getMap("intMap", Integer.class);
            map.update("numStarted", v -> v == null ? 1 : v + 1);
            while (getNumStarted() < 3) {
                Sleeper.sleepMillis(100);
            }

            grid.getCompute().stopTask("testJob");
            while (!stopRequested) {
                Sleeper.sleepMillis(100);
            }
            LOG.debug("Test StoppableTask received stop request and ended.");
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

    private static GridSet getGridSet(Grid grid) {
        return grid.getStorage().getSet("testSet");
    }

    private static Serializable fillFive(Grid grid) {
        var set = getGridSet(grid);
        for (var i = 0; i < 5; i++) {
            set.add(UUID.randomUUID().toString());
        }
        return null;
    }
}
