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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;
import org.junit.jupiter.api.io.TempDir;

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
            var grid = getGridConnector().connect(
                    "Node-" + i, "TestCluster", tempDir);
            nodes.add(grid);
        }
        //MAYBE wait for cluster connection

        try {
            consumer.accept(new MultiNodesMocker(nodes));
        } catch (Exception e) {
            fail("Error running on grid.", e);
        } finally {
            for (Grid grid : nodes) {
                grid.storage().clean();
                grid.close();
            }
            nodes.clear();
        }
    }

    @RequiredArgsConstructor
    public static class MultiNodesMocker {
        private final List<Grid> nodes;

        public void onEachNodes(
                FailableBiConsumer<Grid, Integer, Exception> task)
                throws Exception {
            var exec = Executors.newFixedThreadPool(nodes.size());
            List<Future<?>> futures = new ArrayList<>();
            for (var i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                var index = i;
                futures.add(exec.submit(() -> {
                    task.accept(node, index);
                    return null;
                }));

            }
            for (Future<?> f : futures) {
                f.get();
            }
            exec.shutdown();
        }

        public Grid getGridInstance() {
            return nodes.get(0);
        }
    }
}
