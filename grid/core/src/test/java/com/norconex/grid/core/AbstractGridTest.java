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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.function.FailableBiConsumer;
import org.apache.commons.lang3.function.FailableConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.junit.WithTestWatcherLogging;
import com.norconex.grid.core.mocks.MockGridName;
import com.norconex.grid.core.util.ExecutorManager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@WithTestWatcherLogging
public abstract class AbstractGridTest {

    private static final AtomicInteger TEST_COUNT = new AtomicInteger();

    @Getter
    @TempDir
    private Path tempDir;

    private ExecutorManager em;

    @BeforeEach
    void beforeEachGridTest() {
        em = new ExecutorManager("mock-test-" + TEST_COUNT.incrementAndGet());
    }

    @AfterEach
    void afterEachGridTest() {
        if (em != null) {
            em.shutdown();
        }
    }

    protected abstract GridConnector getGridConnector(String gridName);

    /**
     * Creates a new grid instance that lives for the duration of the
     * consumer execution.
     * @param consumer consumer of grid nodes
     */
    protected void withNewGrid(
            FailableConsumer<MockNodeManager, Exception> consumer) {

        var manager = new MockNodeManager();
        try {
            consumer.accept(manager);
        } catch (Exception e) {
            fail("Error running on grid.", e);
        } finally {
            manager.waitUntilAllDone();
            manager.closeAll();
        }
    }

    @RequiredArgsConstructor
    public class MockNodeManager {
        private final String gridName = MockGridName.generate();
        private final List<Grid> currentNodes =
                Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger currentNumDone = new AtomicInteger();

        boolean allDone() {
            return currentNodes.size() > 0
                    && currentNodes.size() == currentNumDone.get();
        }

        void waitUntilAllDone() {
            var then = System.currentTimeMillis();
            // wait for 10 seconds
            while (!allDone() && System.currentTimeMillis() - then < 10_000) {
                Sleeper.sleepMillis(100);
            }
        }

        /**
         * Clears the nodes created so far but keeps the storage.
         */
        public synchronized void disconnect() {
            waitUntilAllDone();
            if (!allDone()) {
                throw new IllegalStateException("""
                    Cannot reset the test \
                    grid while there are one or more nodes stil \
                    executing tasks.""");
            }
            currentNodes.forEach(Grid::close);
            currentNodes.clear();
            currentNumDone.set(0);
            LOG.debug("Test grid reset.");
        }

        void closeAll() {
            var storageCleaned = false;
            for (Grid grid : currentNodes) {
                if (!storageCleaned) {
                    grid.storage().destroy();
                    storageCleaned = true;
                }
                grid.close();
            }
            currentNodes.clear();
        }

        public void onNewNode(
                FailableConsumer<Grid, Exception> nodeConsumer) {
            onNewNodes(1, (grid, index) -> nodeConsumer.accept(grid));
        }

        public void onNewNodes(
                int numNodes,
                FailableBiConsumer<Grid, Integer, Exception> nodeConsumer) {

            if (allDone()) {
                throw new IllegalStateException("""
                    All nodes on test grid ran \
                    and were done with that grid. \
                    Too late to add more.""");
            }

            // Add new nodes to existing grid
            for (var i = 0; i < numNodes; i++) {
                var node = getGridConnector(gridName)
                        .connect(tempDir); //TODO make unique per node?
                currentNodes.add(node);
                var index = i;
                em.runShortTask("mock-task-" + currentNodes.size(), () -> {
                    try {
                        nodeConsumer.accept(node, index);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    } finally {
                        currentNumDone.incrementAndGet();
                    }
                });
            }
        }
    }
}
