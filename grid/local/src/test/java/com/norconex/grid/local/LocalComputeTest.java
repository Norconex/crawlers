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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridComputeResult;
import com.norconex.grid.core.compute.GridComputeState;
import com.norconex.grid.core.compute.GridComputeTask;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class LocalComputeTest {

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
    void testCanRunMultipleTimes() {
        //NOTE: runOnOne and runOnAll are the same for LocalCompute.

        var set = grid.storage().getSet("store");

        GridComputeResult<?> result;

        result = grid.compute().runOnOne("myjob", () -> {
            set.add("123");
            return null;
        });

        assertThat(result.getState()).isSameAs(GridComputeState.COMPLETED);
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.contains("123")).isTrue();

        // we are allowed to run it again so the entries should add up.
        result = grid.compute().runOnAll("myjob", () -> {
            set.add("456");
            return null;
        });
        assertThat(result.getState()).isSameAs(GridComputeState.COMPLETED);
        assertThat(set.size()).isEqualTo(2);
        assertThat(set.contains("123")).isTrue();
        assertThat(set.contains("456")).isTrue();

        set.clear();
    }

    @Test
    @Timeout(60)
    void testCanRunOnlyOnce() {
        //NOTE: runOnOneOnce and runOnAllOnce are the same for LocalCompute.

        var set = grid.storage().getSet("store");

        GridComputeResult<?> result;

        result = grid.compute().runOnOneOnce("myjob", () -> {
            set.add("123");
            return null;
        });

        assertThat(result.getState()).isSameAs(GridComputeState.COMPLETED);
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.contains("123")).isTrue();

        // we can't run the same job twice. set shall be unchanged
        result = grid.compute().runOnAllOnce("myjob", () -> {
            set.add("456");
            return null;
        });
        assertThat(result.getState()).isSameAs(GridComputeState.COMPLETED);
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.contains("123")).isTrue();
        assertThat(set.contains("456")).isFalse();

        set.clear();
    }

    @Test
    @Timeout(60)
    void testStop() {
        assertThatNoException().isThrownBy(() -> {

            var stoppableTask = new StoppableTask();
            var executor = Executors.newCachedThreadPool();

            var taskFuture = CompletableFuture.runAsync(() -> {
                grid.compute().runOnAll("test", stoppableTask);
            }, executor);

            ConcurrentUtil.get(CompletableFuture.runAsync(() -> {
                LOG.debug("Waiting for task to be marked as running...");
                var start = System.currentTimeMillis();
                while (!stoppableTask.getRunning().get()) {
                    if (System.currentTimeMillis() - start > 60000) {
                        throw new IllegalStateException(
                                "Task did not start in time.");
                    }
                    Sleeper.sleepMillis(100);
                }
                LOG.debug("Task marked as running.");
            }, executor), 60, TimeUnit.SECONDS);

            grid.compute().stop("test");
            ConcurrentUtil.get(taskFuture, 30, TimeUnit.SECONDS);
            executor.shutdownNow();
        });
    }

    private static final class StoppableTask implements GridComputeTask<Void> {
        private CompletableFuture<Void> pendingStop = new CompletableFuture<>();
        @Getter
        private AtomicBoolean running = new AtomicBoolean();

        @Override
        public Void execute() {
            running.set(true);
            LOG.debug("Stoppable task RUNNING.");
            ConcurrentUtil.get(pendingStop, 20, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public void stop() {
            pendingStop.complete(null);
        }
    }
}
