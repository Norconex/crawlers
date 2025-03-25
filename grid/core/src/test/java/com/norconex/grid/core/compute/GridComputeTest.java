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

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.storage.GridSet;

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
    void runOnOneOnceTest() throws Exception {
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

    private void fill(GridSet<String> set, int numEntries) {
        for (var i = 0; i < numEntries; i++) {
            set.add(UUID.randomUUID().toString());
        }
    }
}
