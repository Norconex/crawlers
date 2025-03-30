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

import static com.norconex.grid.core.compute.GridJobState.COMPLETED;
import static com.norconex.grid.core.compute.GridJobState.FAILED;
import static com.norconex.grid.core.compute.GridJobState.IDLE;
import static com.norconex.grid.core.compute.GridJobState.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GridJobStateTest {

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
        assertThat(IDLE.isAny((GridJobState[]) null)).isFalse();
        assertThat(IDLE.isAny()).isFalse();
    }

    @Test
    void testOf() {
        assertThat(GridJobState.of("idle")).isSameAs(IDLE);
        assertThat(GridJobState.of("RUNNING")).isSameAs(RUNNING);
        assertThat(GridJobState.of("Failed")).isSameAs(FAILED);
        assertThat(GridJobState.of("Completed")).isSameAs(COMPLETED);
        assertThat(GridJobState.of("aBadOne")).isSameAs(IDLE);
        assertThat(GridJobState.of(null)).isSameAs(IDLE);
    }

}
