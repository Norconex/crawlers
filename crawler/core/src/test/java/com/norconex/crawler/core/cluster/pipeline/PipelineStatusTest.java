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
package com.norconex.crawler.core.cluster.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.norconex.crawler.core.junit.WithTestWatcherLogging;

@WithTestWatcherLogging
class PipelineStatusTest {

    // -----------------------------------------------------------------
    // isTerminal
    // -----------------------------------------------------------------

    @Test
    void isTerminal_terminalStatuses() {
        assertThat(PipelineStatus.STOPPED.isTerminal()).isTrue();
        assertThat(PipelineStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(PipelineStatus.FAILED.isTerminal()).isTrue();
        assertThat(PipelineStatus.EXPIRED.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_nonTerminalStatuses() {
        assertThat(PipelineStatus.PENDING.isTerminal()).isFalse();
        assertThat(PipelineStatus.RUNNING.isTerminal()).isFalse();
        assertThat(PipelineStatus.STOPPING.isTerminal()).isFalse();
    }

    // -----------------------------------------------------------------
    // is / isNot
    // -----------------------------------------------------------------

    @Test
    void is_matchingSameStatus_returnsTrue() {
        assertThat(PipelineStatus.RUNNING.is(PipelineStatus.RUNNING)).isTrue();
    }

    @Test
    void is_differentStatus_returnsFalse() {
        assertThat(PipelineStatus.RUNNING.is(PipelineStatus.STOPPED)).isFalse();
    }

    @Test
    void is_null_returnsFalse() {
        assertThat(PipelineStatus.RUNNING.is(null)).isFalse();
    }

    @Test
    void isNot_differentStatus_returnsTrue() {
        assertThat(PipelineStatus.RUNNING.isNot(PipelineStatus.STOPPED))
                .isTrue();
    }

    @Test
    void isNot_sameStatus_returnsFalse() {
        assertThat(PipelineStatus.RUNNING.isNot(PipelineStatus.RUNNING))
                .isFalse();
    }

    // -----------------------------------------------------------------
    // Convenience boolean checks
    // -----------------------------------------------------------------

    @Test
    void isRunning_onlyRunning() {
        assertThat(PipelineStatus.RUNNING.isRunning()).isTrue();
        assertThat(PipelineStatus.PENDING.isRunning()).isFalse();
    }

    @Test
    void isStopping_onlyStopping() {
        assertThat(PipelineStatus.STOPPING.isStopping()).isTrue();
        assertThat(PipelineStatus.STOPPED.isStopping()).isFalse();
    }

    @Test
    void isStopped_onlyStopped() {
        assertThat(PipelineStatus.STOPPED.isStopped()).isTrue();
        assertThat(PipelineStatus.STOPPING.isStopped()).isFalse();
    }

    @Test
    void isCompleted_onlyCompleted() {
        assertThat(PipelineStatus.COMPLETED.isComleted()).isTrue();
        assertThat(PipelineStatus.RUNNING.isComleted()).isFalse();
    }

    @Test
    void isPending_onlyPending() {
        assertThat(PipelineStatus.PENDING.isPending()).isTrue();
        assertThat(PipelineStatus.RUNNING.isPending()).isFalse();
    }

    @Test
    void isFailed_onlyFailed() {
        assertThat(PipelineStatus.FAILED.isFailed()).isTrue();
        assertThat(PipelineStatus.STOPPED.isFailed()).isFalse();
    }

    // -----------------------------------------------------------------
    // Enum completeness
    // -----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(PipelineStatus.class)
    void allValues_isAndIsNot_areConsistent(PipelineStatus status) {
        assertThat(status.is(status)).isTrue();
        assertThat(status.isNot(status)).isFalse();
    }
}
