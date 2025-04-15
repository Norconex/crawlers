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

import static com.norconex.grid.core.compute.GridComputeState.COMPLETED;
import static com.norconex.grid.core.compute.GridComputeState.FAILED;
import static com.norconex.grid.core.compute.GridComputeState.IDLE;
import static com.norconex.grid.core.compute.GridComputeState.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GridComputeStateTest {

    @Test
    void testIsRunning() {
        assertThat(IDLE.isRunning()).isFalse();
        assertThat(RUNNING.isRunning()).isTrue();
        assertThat(FAILED.isRunning()).isFalse();
        assertThat(COMPLETED.isRunning()).isFalse();
    }

    @Test
    void testHasRan() {
        assertThat(IDLE.hasRan()).isFalse();
        assertThat(RUNNING.hasRan()).isFalse();
        assertThat(FAILED.hasRan()).isTrue();
        assertThat(COMPLETED.hasRan()).isTrue();
    }

    @Test
    void testIsAny() {
        assertThat(IDLE.isAny(IDLE, RUNNING)).isTrue();
        assertThat(IDLE.isAny(FAILED, COMPLETED)).isFalse();
        assertThat(IDLE.isAny((GridComputeState[]) null)).isFalse();
        assertThat(IDLE.isAny()).isFalse();
    }

    @Test
    void testOf() {
        assertThat(GridComputeState.of("idle")).isSameAs(IDLE);
        assertThat(GridComputeState.of("RUNNING")).isSameAs(RUNNING);
        assertThat(GridComputeState.of("Failed")).isSameAs(FAILED);
        assertThat(GridComputeState.of("Completed")).isSameAs(COMPLETED);
        assertThat(GridComputeState.of("aBadOne")).isSameAs(IDLE);
        assertThat(GridComputeState.of(null)).isSameAs(IDLE);
    }

}
