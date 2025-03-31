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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Sleeper;
import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.util.ConcurrentUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class GridComputeTest extends AbstractGridTest {

    @Test
    void runOnOneTest() {

        withNewGrid(3, mocker -> {
            LOG.trace("Running 'runOnOneTest' part 1 of 2");
            GridSet<String> set = mocker
                    .getGridInstance()
                    .storage()
                    .getSet("test", String.class);
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnOne("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(5);

            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnOneTest' part 2 of 2");
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnOne("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(10);

            set.clear();
        });
    }

    @Test
    void runOnOneOnceTest() {
        withNewGrid(3, mocker -> {
            LOG.trace("Running 'runOnOneOnceTest' part 1 of 2");
            GridSet<String> set = mocker
                    .getGridInstance()
                    .storage()
                    .getSet("test", String.class);
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnOneOnce("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(5);

            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnOneOnceTest' part 2 of 2");
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnOneOnce("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(5);

            set.clear();
        });
    }

    @Test
    void runOnAllTest() throws Exception {
        withNewGrid(3, mocker -> {
            LOG.trace("Running 'runOnAllTest' part 1 of 2");
            GridSet<String> set = mocker
                    .getGridInstance()
                    .storage()
                    .getSet("test", String.class);
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnAll("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(15);

            // we are allowed to run it again so the numbers should add up.
            LOG.trace("Running 'runOnAllTest' part 2 of 2");
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnAll("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(30);

            set.clear();
        });
    }

    @Test
    void runOnAllOnceTest() throws Exception {
        withNewGrid(3, mocker -> {
            LOG.trace("Running 'runOnAllOnceTest' part 1 of 2");
            GridSet<String> set = mocker
                    .getGridInstance()
                    .storage()
                    .getSet("test", String.class);
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnAllOnce("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(15);

            // we can't run the same job twice. number shall be unchanged
            LOG.trace("Running 'runOnAllOnceTest' part 2 of 2");
            mocker.onEachNodes((grid, index) -> {
                grid.compute().runOnAllOnce("test", () -> {
                    fill(set, 5);
                });
            });
            assertThat(set.size()).isEqualTo(15);

            set.clear();
        });
    }

    @Test
        void testRequestStop() {
            assertThatNoException().isThrownBy(() -> {
    
                withNewGrid(3, mocker -> {
                    GridMap<Integer> map = mocker
                            .getGridInstance()
                            .storage()
                            .getMap("count", Integer.class);
                    var future = CompletableFuture.runAsync(() -> {
                        mocker.onEachNodes((grid, index) -> {
                            map.update("count", v -> v == null ? 1 : v + 1);
                            grid.compute().runOnAll("test", new StoppableJob());
                        });
                    });
    
                    ConcurrentUtil.get(CompletableFuture.runAsync(() -> {
                        while (ofNullable(map.get("count")).orElse(0) < 3) {
                            Sleeper.sleepMillis(100);
                        }
                    }), 10, TimeUnit.SECONDS);
    
                    mocker.getGridInstance().compute().requestStop("test");
                    ConcurrentUtil.get(future, 10, TimeUnit.SECONDS);
                });
            });
        }

    private void fill(GridSet<String> set, int numEntries) {
        for (var i = 0; i < numEntries; i++) {
            set.add(UUID.randomUUID().toString());
        }
    }

    private static final class StoppableJob implements StoppableRunnable {
        private CompletableFuture<Void> pendingStop = new CompletableFuture<>();

        @Override
        public void run() {
            ConcurrentUtil.get(pendingStop, 20, TimeUnit.SECONDS);
        }

        @Override
        public void stopRequested() {
            pendingStop.complete(null);
        }
    }

}
