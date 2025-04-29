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

import static com.norconex.grid.core.impl.compute.TaskState.COMPLETED;
import static com.norconex.grid.core.impl.compute.TaskState.FAILED;
import static com.norconex.grid.core.impl.compute.TaskState.PENDING;
import static com.norconex.grid.core.impl.compute.TaskState.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.impl.compute.TaskState;

class TaskStateTest {

    @Test
    void testIsRunning() {
        assertThat(PENDING.isRunning()).isFalse();
        assertThat(RUNNING.isRunning()).isTrue();
        assertThat(FAILED.isRunning()).isFalse();
        assertThat(COMPLETED.isRunning()).isFalse();
    }

    @Test
    void testHasRan() {
        assertThat(PENDING.isTerminal()).isFalse();
        assertThat(RUNNING.isTerminal()).isFalse();
        assertThat(FAILED.isTerminal()).isTrue();
        assertThat(COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void testIsAny() {
        assertThat(PENDING.isAny(RUNNING, PENDING)).isTrue();
        assertThat(PENDING.isAny(FAILED, COMPLETED)).isFalse();
        assertThat(PENDING.isAny((TaskState[]) null)).isFalse();
        assertThat(PENDING.isAny()).isFalse();
    }

    @Test
    void testOf() {
        assertThat(TaskState.of("pending")).isSameAs(PENDING);
        assertThat(TaskState.of("RUNNING")).isSameAs(RUNNING);
        assertThat(TaskState.of("Failed")).isSameAs(FAILED);
        assertThat(TaskState.of("Completed")).isSameAs(COMPLETED);
        assertThat(TaskState.of("aBadOne")).isSameAs(PENDING);
        assertThat(TaskState.of(null)).isSameAs(PENDING);
    }

}
