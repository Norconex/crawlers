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

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute.RunOn;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class GridPipelineTest extends AbstractGridTest {

    private static final int NUM_NODES = 3;

    @Test
    void testRunSuccess() {
        var sc = new StageCreator();
        List<GridPipelineStage<Context>> stages = List.of(
                sc.onOne(ctx -> {
                    ctx.addOne("itemA");
                }),
                sc.onAll(ctx -> {
                    ctx.addOne("itemB");
                }),
                sc.onOneOnce(ctx -> {
                    // put in store instead.
                    ctx.set("whatStage", ctx.grid.pipeline()
                            .getActiveStageName("test-pipelineA").orElse(null));
                    ctx.addOne("itemC");
                }),
                sc.onAllOnce(ctx -> {
                    ctx.addOne("itemC");
                }));

        withNewGrid(NUM_NODES, mocker -> {
            mocker.onEachNodes((grid, index) -> {
                var context = new Context(grid);
                boolean success = ConcurrentUtil.get(
                        grid.pipeline().run("test-pipelineA", stages, context));
                assertThat(success).isTrue();
                assertThat(context.bagInt.get("itemA")).isEqualTo(1);
                assertThat(context.bagInt.get("itemB")).isEqualTo(NUM_NODES);
                assertThat(context.bagInt.get("itemC"))
                        .isEqualTo(NUM_NODES + 1);
                assertThat(context.bagStr.get("whatStage"))
                        .isEqualTo("stage-2");
            });

            // Trying to run the pipeline again in this session. Only values for
            // the items of stages that are allow to re-run will be updated.
            mocker.onEachNodes((grid, index) -> {
                var context = new Context(grid);
                boolean success = ConcurrentUtil.get(
                        grid.pipeline().run("test-pipelineA", stages, context));
                assertThat(success).isTrue();
                // All values should be double (i.e., added again by second run
                // except for item C, which should remain unchanged (their job
                // did not run twice).
                assertThat(context.bagInt.get("itemA")).isEqualTo(2);
                assertThat(context.bagInt.get("itemB"))
                        .isEqualTo(NUM_NODES * 2);
                assertThat(context.bagInt.get("itemC"))
                        .isEqualTo(NUM_NODES + 1);
            });

        });
    }

    @Test
    void testRunFailureAndOnlyIfAndAlways() {
        var sc = new StageCreator();
        List<GridPipelineStage<Context>> stages = List.of(
                // Runs OK
                sc.onOne(ctx -> {
                    ctx.addOne("itemA");
                }),
                // skipped for not meeting condition
                sc.build(RunOn.ONE, ctx -> {
                    ctx.addOne("itemB");
                }, builder -> {
                    builder.onlyIf(c -> false);
                }),
                // Fails the build after setting its value:
                sc.onAll(ctx -> {
                    ctx.addOne("itemC");
                    throw new GridException("Simulating failure.");
                }),
                // should not get executed:
                sc.onOneOnce(ctx -> {
                    ctx.addOne("itemD");
                }),
                sc.build(RunOn.ALL_ONCE, ctx -> {
                    ctx.add("itemE", 111);
                }, builder -> {
                    builder.always(true);
                }));

        withNewGrid(NUM_NODES, mocker -> {
            mocker.onEachNodes((grid, index) -> {
                var context = new Context(grid);
                boolean success = ConcurrentUtil.get(
                        grid.pipeline().run("test-pipelineB", stages, context));
                assertThat(success).isFalse();
                assertThat(context.bagInt.get("itemA")).isEqualTo(1);
                assertThat(context.bagInt.get("itemB")).isNull();
                assertThat(context.bagInt.get("itemC")).isEqualTo(NUM_NODES);
                assertThat(context.bagInt.get("itemD")).isNull();
                assertThat(context.bagInt.get("itemE")).isEqualTo(333);
            });

        });
    }

    @Test
    void testJoinMidPipeline() {

        var frozen = new AtomicBoolean(true);
        var twoNodesBlocked = new CompletableFuture<Void>();

        var sc = new StageCreator();
        List<GridPipelineStage<Context>> stages = List.of(
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // 2
                }),
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // 4
                }),
                // third added here
                sc.onAll(ctx -> {
                    int countSoFar = ctx.bagInt.get("count");
                    if (countSoFar == 4) {
                        twoNodesBlocked.complete(null);
                    }
                    while (frozen.get()) {
                        Sleeper.sleepMillis(100);
                    }
                    ctx.addOne("count"); // 7
                }),
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // 10
                }));

        var future = CompletableFuture.runAsync(() -> {
            withNewGrid(2, mocker -> {
                mocker.onEachNodes((grid, index) -> {
                    var context = new Context(grid);
                    boolean success = ConcurrentUtil.get(
                            grid.pipeline().run("test-pipelineC", stages,
                                    context));
                    assertThat(success).isTrue();
                    assertThat(context.bagInt.get("count")).isEqualTo(10);
                });
            });
        });

        ConcurrentUtil.get(twoNodesBlocked, 10, TimeUnit.SECONDS);

        withNewGrid(1, mocker -> {
            mocker.onEachNodes((grid, index) -> {
                var context = new Context(grid);
                frozen.set(false);
                boolean success = ConcurrentUtil.get(
                        grid.pipeline().run("test-pipelineC", stages,
                                context));
                assertThat(success).isTrue();
                // if a 3rd node did not join on 3rd stage, total would be 12
                assertThat(context.bagInt.get("count")).isEqualTo(10);
            });
        });

        ConcurrentUtil.get(future, 10, TimeUnit.SECONDS);
    }

    @Test
    void testPipelineStop() {

        var frozen = new AtomicBoolean(true);

        var sc = new StageCreator();
        List<GridPipelineStage<Context>> stages = List.of(
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // 3
                }),
                sc.onAll(ctx -> {
                    ctx.bagInt.get("count");
                    while (frozen.get()) {
                        Sleeper.sleepMillis(100);
                    }
                    ctx.addOne("count"); // 6
                }),
                // stop here
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // N/A
                }),
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // N/A
                }));

        withNewGrid(NUM_NODES, mocker -> {
            mocker.onEachNodes((grid, index) -> {
                var context = new Context(grid);
                var future = grid.pipeline().run(
                        "test-pipelineD", stages, context);
                while (!"stage-1"
                        .equals(grid.pipeline()
                                .getActiveStageName("test-pipelineD")
                                .orElse(null))) {
                    Sleeper.sleepMillis(100);
                }
                grid.pipeline().stop(null);
                frozen.set(false);

                boolean success = ConcurrentUtil.get(
                        future, 10, TimeUnit.SECONDS);

                assertThat(success).isTrue();
                assertThat(context.bagInt.get("count")).isEqualTo(6);
            });

        });
    }

    private static class StageCreator {
        int counter = 0;

        GridPipelineStage<Context> onOne(GridPipelineTask<Context> task) {
            return build(RunOn.ONE, task);
        }

        GridPipelineStage<Context> onAll(GridPipelineTask<Context> task) {
            return build(RunOn.ALL, task);
        }

        GridPipelineStage<Context> onOneOnce(GridPipelineTask<Context> task) {
            return build(RunOn.ONE_ONCE, task);
        }

        GridPipelineStage<Context> onAllOnce(GridPipelineTask<Context> task) {
            return build(RunOn.ALL_ONCE, task);
        }

        GridPipelineStage<Context> build(
                RunOn runOn,
                GridPipelineTask<Context> task) {
            return build(runOn, task, null);
        }

        GridPipelineStage<Context> build(
                RunOn runOn,
                GridPipelineTask<Context> task,
                Consumer<GridPipelineStage.GridPipelineStageBuilder<
                        Context>> builderModifier) {
            var builder = GridPipelineStage.<Context>builder()
                    .name("stage-" + counter++)
                    .runOn(runOn)
                    .task(task);
            if (builderModifier != null) {
                builderModifier.accept(builder);
            }
            return builder.build();
        }
    }

    class Context implements Serializable {

        private static final long serialVersionUID = 1L;
        private final GridMap<Integer> bagInt;
        private final GridMap<String> bagStr;
        private final Grid grid;

        public Context(Grid grid) {
            this.grid = grid;
            bagInt = grid.storage().getMap("bagInt", Integer.class);
            bagStr = grid.storage().getMap("bagStr", String.class);
        }

        void addOne(String key) {
            add(key, 1);
        }

        void add(String key, int value) {
            bagInt.update(key, v -> v == null ? value : v + value);
        }

        void set(String key, String value) {
            bagStr.update(key, v -> v == null ? value : v + value);
        }
    }

}
