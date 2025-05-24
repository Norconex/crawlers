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
package com.norconex.grid.local;

import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.cluster.Cluster;
import com.norconex.grid.core.cluster.ClusterTest;
import com.norconex.grid.core.cluster.WithCluster;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.compute.Stage;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Timeout(60)
@WithCluster(connectorFactory = LocalClusterTestConnectorFactory.class)
public class LocalComputePipelineTest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ClusterTest
    void testRunSuccess(Cluster cluster) {
        var pipeline = GridPipeline.of("test-pipelineA",
                new Stage(GridTaskBuilder.create("task1")
                        .singleNode()
                        .processor(g -> new Store(g).addOne("itemA"))
                        .build()),
                new Stage(GridTaskBuilder.create("task2")
                        .allNodes()
                        .processor(g -> new Store(g).addOne("itemB"))
                        .build()),
                new Stage(GridTaskBuilder.create("task3")
                        .singleNode()
                        .once()
                        .processor(g -> new Store(g)
                                .set("whatStage", g
                                        .getCompute()
                                        .getPipelineActiveStageIndex(
                                                "test-pipelineA"))
                                .addOne("itemC"))
                        .build()),
                new Stage(GridTaskBuilder.create("task4")
                        .allNodes()
                        .once()
                        .processor(g -> new Store(g).addOne("itemC"))
                        .build()));

        cluster.onOneNewNode(grid -> {
            LOG.info("Starting task 1/2 on node {}", grid.getNodeName());
            var wrapper = new Store(grid);
            grid.getCompute().executePipeline(pipeline);
            assertThat(wrapper.bagInt.get("itemA")).isEqualTo(1);
            assertThat(wrapper.bagInt.get("itemB")).isEqualTo(1);
            assertThat(wrapper.bagInt.get("itemC")).isEqualTo(2);
            assertThat(wrapper.bagInt.get("whatStage")).isEqualTo(2);
            LOG.info("Finished task 1/2 on node {}", grid.getNodeName());
        });

        cluster.onOneNewNode(grid -> {
            // Trying to run the pipeline again in this session. Only values for
            // the items of stages that are allow to re-run will be updated.
            LOG.info("Starting task 2/2 on node {}", grid.getNodeName());
            var wrapper = new Store(grid);
            grid.getCompute().executePipeline(pipeline);
            // All values should be double (i.e., added again by second run
            // except for item C, which should remain unchanged (their job
            // did not run twice).
            assertThat(wrapper.bagInt.get("itemA")).isEqualTo(2);
            assertThat(wrapper.bagInt.get("itemB")).isEqualTo(2);
            assertThat(wrapper.bagInt.get("itemC")).isEqualTo(2);
            LOG.info("Finished task 2/2 on node {}", grid.getNodeName());
        });
    }

    @ClusterTest
    void testRunFailureAndOnlyIfAndAlways(Cluster cluster) {
        var pipeline = GridPipeline.of("test-pipelineB",
                // Runs OK
                new Stage(GridTaskBuilder.create("task1")
                        .singleNode()
                        .processor(g -> new Store(g).addOne("itemA"))
                        .build()),

                // skipped for not meeting condition
                new Stage((g, prevResult) -> null),

                // Fails the build after setting its value:
                new Stage(GridTaskBuilder.create("task3")
                        .allNodes()
                        .processor(g -> {
                            new Store(g).addOne("itemC");
                            throw new GridException("Simulating failure.");
                        })
                        .build()),
                // should not get executed:
                new Stage(GridTaskBuilder.create("task4")
                        .singleNode()
                        .once()
                        .processor(g -> new Store(g).addOne("itemD"))
                        .build()),
                // should "always" get executed:
                new Stage(GridTaskBuilder.create("task5")
                        .allNodes()
                        .once()
                        .processor(g -> new Store(g).add("itemE", 111))
                        .build()).withAlways(true));

        cluster.onOneNewNode(grid -> {
            LOG.info("Starting task on node {}", grid.getNodeName());
            var wrapper = new Store(grid);
            grid.getCompute().executePipeline(pipeline);
            assertThat(wrapper.bagInt.get("itemA")).isEqualTo(1);
            assertThat(wrapper.bagInt.get("itemB")).isNull();
            assertThat(wrapper.bagInt.get("itemC")).isEqualTo(1);
            assertThat(wrapper.bagInt.get("itemD")).isNull();
            assertThat(wrapper.bagInt.get("itemE")).isEqualTo(111);
            LOG.info("Finished task on node {}", grid.getNodeName());
        });
    }

    @ClusterTest
    void testPipelineStop(Cluster cluster) {
        var unblockedKey = "unblocked";
        var countKey = "count";
        var pipeline = GridPipeline.of("test-pipelineD",
                new Stage(GridTaskBuilder.create("task1")
                        .allNodes()
                        .processor(g -> new Store(g).addOne(countKey)) // 1
                        .build()),
                new Stage(GridTaskBuilder.create("task2")
                        .allNodes()
                        .processor(g -> {
                            var store = new Store(g);
                            LOG.debug("Waiting for unblocking...");
                            while (!store.getBool(unblockedKey)) {
                                Sleeper.sleepMillis(100);
                            }
                            store.addOne("count"); // 2

                            // give enough time for the stop request to
                            // propagate
                            Sleeper.sleepSeconds(2);
                        })
                        .build()),
                // stop here
                new Stage(GridTaskBuilder.create("task3")
                        .allNodes()
                        .processor(g -> new Store(g).addOne(countKey)) //N/A
                        .build()),
                new Stage(GridTaskBuilder.create("task4")
                        .allNodes()
                        .processor(g -> new Store(g).addOne(countKey)) //N/A
                        .build()));

        var nodeCreated = new AtomicBoolean();
        var taskFuture = CompletableFuture.runAsync(() -> {
            cluster.onOneNewNode(grid -> {
                nodeCreated.set(true);
                grid.getCompute().executePipeline(pipeline);
            });
        });

        ConcurrentUtil.waitUntil(nodeCreated::get);
        var grid = cluster.getLastNodeCreated();
        while (grid.getCompute()
                .getPipelineActiveStageIndex("test-pipelineD") < 1) {
            Sleeper.sleepMillis(100);
        }

        grid.getCompute().stopPipeline("test-pipelineD");
        var store = new Store(grid);
        store.set(unblockedKey, true);

        ConcurrentUtil.get(taskFuture);
        assertThat(store.bagInt.get("count")).isEqualTo(2);
    }

    //--- Private --------------------------------------------------------------

    static class Store implements Serializable {

        private static final long serialVersionUID = 1L;

        private final GridMap<Integer> bagInt;
        private final GridMap<String> bagStr;
        private final GridMap<Boolean> bagBool;

        public Store(Grid grid) {
            bagInt = grid.getStorage().getMap("bagInt", Integer.class);
            bagStr = grid.getStorage().getMap("bagStr", String.class);
            bagBool = grid.getStorage().getMap("bagBool", Boolean.class);
        }

        Store addOne(String key) {
            add(key, 1);
            return this;
        }

        int getInt(String key) {
            return ofNullable(bagInt.get(key)).orElse(0);
        }

        boolean getBool(String key) {
            return ofNullable(bagBool.get(key)).orElse(false);
        }

        Store add(String key, int value) {
            bagInt.update(key, v -> v == null ? value : v + value);
            return this;
        }

        Store set(String key, boolean value) {
            bagBool.put(key, value);
            return this;
        }

        Store set(String key, int value) {
            bagInt.put(key, value);
            return this;
        }

        Store set(String key, String value) {
            bagStr.update(key, v -> v == null ? value : v + value);
            return this;
        }
    }
}
