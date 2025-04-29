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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.jgroups.util.UUID;
import org.junit.jupiter.api.Timeout;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridContext;
import com.norconex.grid.core.cluster.Cluster;
import com.norconex.grid.core.cluster.ClusterTest;
import com.norconex.grid.core.compute.BaseGridTask.SingleNodeTask;
import com.norconex.grid.core.storage.GridSet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Timeout(60)
public abstract class GridComputeTest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ClusterTest
    void runOnOneTest(Cluster cluster) {
        cluster.onThreeNewNodes(grid -> {
            LOG.debug("Starting task 1/2 on node {}", grid.getNodeName());
            grid.getCompute().executeTask(new SingleNodeTask("testJob") {
                private static final long serialVersionUID = 1L;

                @Override
                public String execute(GridContext ctx) {
                    var set = getGridSet(ctx);
                    fill(set, 5);
                    return null;
                }
            });
            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(5);
            LOG.debug("Finished task 1/2 on node {}", grid.getNodeName());
        });

        // we are allowed to run it again so the numbers should add up.
        LOG.trace("Running 'runOnOneTest' part 2 of 2");
        cluster.onThreeNewNodes(grid -> {
            LOG.debug("Starting task 2/2 on node {}", grid.getNodeName());
            grid.getCompute().executeTask(new SingleNodeTask("testJob") {
                private static final long serialVersionUID = 1L;

                @Override
                public String execute(GridContext ctx) {
                    var set = getGridSet(ctx);
                    fill(set, 5);
                    return null;
                }
            });

            var set = getGridSet(grid);
            assertThat(set.size()).isEqualTo(10);
            LOG.debug("Finished task 2/2 on node {}", grid.getNodeName());
        });
    }

    private static GridSet getGridSet(GridContext ctx) {
        return getGridSet(ctx.getGrid());
    }

    private static GridSet getGridSet(Grid grid) {
        return grid.getStorage().getSet("testSet");
    }

    private static void fill(GridSet set, int numEntries) {
        for (var i = 0; i < numEntries; i++) {
            set.add(UUID.randomUUID().toString());
        }
    }

    //    static class SetFillExecutor implements GridTaskExecutor {
    //        @Override
    //        public Object execute(Grid grid) {
    //            var set = grid.storage().getSet("testSet");
    //                for (var i = 0; i < numEntries; i++) {
    //                    set.add(UUID.randomUUID().toString());
    //                }
    //            return null;
    //        }
    //    }

}
