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
package com.norconex.grid.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridCompute.RunOn;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class GridPipelineTest extends AbstractGridTest {

    @Test
    void testRun() {
        withNewGrid(3, mocker -> {
            mocker.onEachNodes((grid, index) -> {
                var context = new Context(grid);

                var sc = new StageCreator();
                List<GridPipelineStage<Context>> stages = List.of(
                        sc.onOne(ctx -> {
                            System.err.println("STAGE 1 RUNNING");
                            ctx.addOne("stage-1");
                            return true;
                        }),
                        sc.onAll(ctx -> {
                            System.err.println("STAGE 2 RUNNING");
                            ctx.addOne("stage-2");
                            return true;
                        }));

                boolean success = ConcurrentUtil.get(
                        grid.pipeline().run("test-pipeline", stages, context));
                assertThat(success).isTrue();
                assertThat(context.bag.get("stage-1")).isEqualTo(1);
                assertThat(context.bag.get("stage-2")).isEqualTo(3);
            });
        });
    }

    private static class StageCreator {
        int counter = 0;

        GridPipelineStage<Context> onOne(GridPipelineTask<Context> task) {
            return GridPipelineStage.<Context>builder()
                    .name("stage-" + counter++)
                    .runOn(RunOn.ONE)
                    .task(task)
                    .build();
        }

        GridPipelineStage<Context> onAll(GridPipelineTask<Context> task) {
            return GridPipelineStage.<Context>builder()
                    .name("stage-" + counter++)
                    .runOn(RunOn.ALL)
                    .task(task)
                    .build();
        }
    }

    @Test
    void testGetActiveStageName() {
        fail("Not yet implemented");
    }

    @Test
    void testGetState() {
        fail("Not yet implemented");
    }

    class Context {
        private final GridMap<Integer> bag;

        public Context(Grid grid) {
            bag = grid.storage().getMap("test-store", Integer.class);
        }

        void addOne(String key) {
            add(key, 1);
        }

        void add(String key, int value) {
            bag.update(key, v -> v == null ? value : v + value);
        }
    }

}
