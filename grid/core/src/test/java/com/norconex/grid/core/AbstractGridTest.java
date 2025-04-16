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
package com.norconex.grid.core;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.grid.core.util.ConcurrentUtil;
import com.norconex.grid.core.util.ThreadRenamer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public abstract class AbstractGridTest {

    @Getter
    @TempDir
    private Path tempDir;

    protected abstract GridConnector getGridConnector();

    protected void withNewGrid(int numNodes,
            FailableConsumer<MultiNodesMocker, Exception> consumer) {

        List<Grid> nodes = new ArrayList<>();
        for (var i = 0; i < numNodes; i++) {
            var grid = getGridConnector().connect(tempDir);
            nodes.add(grid);
        }
        //MAYBE wait for cluster connection

        try {
            consumer.accept(new MultiNodesMocker(nodes));
        } catch (Exception e) {
            fail("Error running on grid.", e);
        } finally {
            var storageCleaned = false;
            for (Grid grid : nodes) {
                if (!storageCleaned) {
                    grid.storage().destroy();
                    storageCleaned = true;
                }
                grid.close();
            }
            nodes.clear();
        }

        //        ThreadTracker.printAllThreads("blah");
        //        System.err
        //                .println("LIVE THREADS: " + ThreadTracker.getLiveThreadCount());
        //        System.err
        //                .println("PEAK THREADS: " + ThreadTracker.getPeakThreadCount());
        //        if (COUNT_THREADS) {
        //            var peakThreadCount = threadMXBean.getPeakThreadCount();
        //            for (Thread t : Thread.getAllStackTraces().keySet()) {
        //                System.err.println(
        //                        t.getName() + " (daemon=" + t.isDaemon() + ")");
        //            }
        //            System.err.println("Peak thread count: " + peakThreadCount);
        //        }
    }

    @RequiredArgsConstructor
    public static class MultiNodesMocker {
        private final List<Grid> nodes;

        public void onEachNodes(
                FailableBiConsumer<Grid, Integer, Exception> task) {
            var exec = Executors.newFixedThreadPool(nodes.size());
            List<Future<?>> futures = new ArrayList<>();
            for (var i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                var index = i;
                futures.add(CompletableFuture.runAsync(
                        ThreadRenamer.set("multi-nodes-mock", () -> {
                            try {
                                task.accept(node, index);
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        }),
                        exec));

            }
            for (Future<?> f : futures) {
                ConcurrentUtil.get(f);
            }
            exec.shutdown();
        }

        public Grid getGrid() {
            return nodes.get(0);
        }
    }
}
