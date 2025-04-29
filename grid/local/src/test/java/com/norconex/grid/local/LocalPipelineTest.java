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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute_DELETE.GridCompute.RunOn;
import com.norconex.grid.core.pipeline.GridPipelineStage;
import com.norconex.grid.core.pipeline.GridPipelineTask;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class LocalPipelineTest {

    @TempDir
    private Path tempDir;

    private Grid grid;

    @BeforeEach
    void beforeEach() {
        grid = new LocalGridConnector().connect(tempDir);
    }

    @AfterEach
    void afterEach() {
        grid.close();
    }

    @Test
    @Timeout(60)
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

        var context = new Context(grid);
        boolean success = ConcurrentUtil.get(
                grid.pipeline().run("test-pipelineA", stages, context));
        assertThat(success).isTrue();
        assertThat(context.bagInt.get("itemA")).isEqualTo(1);
        assertThat(context.bagInt.get("itemB")).isEqualTo(1);
        assertThat(context.bagInt.get("itemC")).isEqualTo(2);
        assertThat(context.bagStr.get("whatStage")).isEqualTo("stage-2");

        // Trying to run the pipeline again in this session. Only values for
        // the items of stages that are allow to re-run will be updated.
        context = new Context(grid);
        success = ConcurrentUtil.get(
                grid.pipeline().run("test-pipelineA", stages, context));
        assertThat(success).isTrue();
        // All values should be double (i.e., added again by second run
        // except for item C, which should remain unchanged (their job
        // did not run twice).
        assertThat(context.bagInt.get("itemA")).isEqualTo(2);
        assertThat(context.bagInt.get("itemB")).isEqualTo(2);
        assertThat(context.bagInt.get("itemC")).isEqualTo(2);
    }

    @Test
    @Timeout(60)
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

        var context = new Context(grid);
        boolean success = ConcurrentUtil.get(
                grid.pipeline().run("test-pipelineB", stages, context));
        assertThat(success).isFalse();
        assertThat(context.bagInt.get("itemA")).isEqualTo(1);
        assertThat(context.bagInt.get("itemB")).isNull();
        assertThat(context.bagInt.get("itemC")).isEqualTo(1);
        assertThat(context.bagInt.get("itemD")).isNull();
        assertThat(context.bagInt.get("itemE")).isEqualTo(111);
    }

    @Test
    @Timeout(60)
    void testPipelineStop() {

        var frozen = new AtomicBoolean(true);

        var sc = new StageCreator();
        List<GridPipelineStage<Context>> stages = List.of(
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // 1
                }),
                sc.onAll(ctx -> {
                    ctx.bagInt.get("count");
                    while (frozen.get()) {
                        Sleeper.sleepMillis(100);
                    }
                    ctx.addOne("count"); // 2
                }),
                // stop here
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // N/A
                }),
                sc.onAll(ctx -> {
                    ctx.addOne("count"); // N/A
                }));

        var context = new Context(grid);
        var future = grid.pipeline().run(
                "test-pipelineD", stages, context);
        var start = System.currentTimeMillis();
        while (!"stage-1"
                .equals(grid.pipeline()
                        .getActiveStageName("test-pipelineD")
                        .orElse(null))) {
            if (System.currentTimeMillis() - start > 10_000) {
                throw new IllegalStateException(
                        "Timed out waiting for stage-1 to become active");
            }
            Sleeper.sleepMillis(100);
        }
        grid.pipeline().stop(null);
        frozen.set(false);

        boolean success = ConcurrentUtil.get(
                future, 10, TimeUnit.SECONDS);

        assertThat(success).isTrue();
        assertThat(context.bagInt.get("count")).isEqualTo(2);
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

    class Context {
        private final GridMap<Integer> bagInt;
        private final GridMap<String> bagStr;
        private final Grid grid;

        public Context(Grid grid) {
            this.grid = grid;
            bagInt = grid.getStorage().getMap("bagInt", Integer.class);
            bagStr = grid.getStorage().getMap("bagStr", String.class);
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
