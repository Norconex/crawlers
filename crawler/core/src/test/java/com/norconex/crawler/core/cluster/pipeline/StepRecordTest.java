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
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.junit.WithTestWatcherLogging;

@WithTestWatcherLogging
@Timeout(30)
class StepRecordTest {

    // -----------------------------------------------------------------
    // hasTimedOut
    // -----------------------------------------------------------------

    @Test
    void hasTimedOut_zeroTimeout_neverTimesOut() {
        var rec = new StepRecord()
                .setUpdatedAt(1L); // very old timestamp
        assertThat(rec.hasTimedOut(0)).isFalse();
    }

    @Test
    void hasTimedOut_recentUpdate_returnsFalse() {
        var rec = new StepRecord()
                .setUpdatedAt(System.currentTimeMillis());
        // 10 minute timeout – should not be expired for a freshly set timestamp
        assertThat(rec.hasTimedOut(600_000)).isFalse();
    }

    @Test
    void hasTimedOut_veryOldUpdate_returnsTrue() {
        // Set updatedAt to 1 hour ago; timeout is 100ms → definitely expired
        var rec = new StepRecord()
                .setUpdatedAt(System.currentTimeMillis() - 3_600_000L);
        assertThat(rec.hasTimedOut(100)).isTrue();
    }

    @Test
    void hasTimedOut_justBeyondTimeout_returnsTrue() {
        // updatedAt set far enough in the past that timeout + 1s grace is exceeded
        long timeout = 500L;
        var rec = new StepRecord()
                .setUpdatedAt(System.currentTimeMillis() - timeout - 2000L);
        assertThat(rec.hasTimedOut(timeout)).isTrue();
    }

    @Test
    void hasTimedOut_withinGracePeriod_returnsFalse() {
        // updatedAt set such that only 200ms has elapsed, timeout is 500ms
        var rec = new StepRecord()
                .setUpdatedAt(System.currentTimeMillis() - 200L);
        assertThat(rec.hasTimedOut(500)).isFalse();
    }

    // -----------------------------------------------------------------
    // Builder / data access (Lombok @Data @Accessors(chain=true))
    // -----------------------------------------------------------------

    @Test
    void fluentSetter_chainsCorrectly() {
        var rec = new StepRecord()
                .setPipelineId("p1")
                .setStepId("s1")
                .setStatus(PipelineStatus.RUNNING)
                .setRunId("run-42");

        assertThat(rec.getPipelineId()).isEqualTo("p1");
        assertThat(rec.getStepId()).isEqualTo("s1");
        assertThat(rec.getStatus()).isSameAs(PipelineStatus.RUNNING);
        assertThat(rec.getRunId()).isEqualTo("run-42");
    }
}
