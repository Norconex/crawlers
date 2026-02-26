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
package com.norconex.crawler.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ThreadTracker}.
 */
class ThreadTrackerTest {

    @Test
    void testGetLiveThreadCount_positive() {
        assertThat(ThreadTracker.getLiveThreadCount()).isPositive();
    }

    @Test
    void testGetPeakThreadCount_positive() {
        assertThat(ThreadTracker.getPeakThreadCount()).isPositive();
    }

    @Test
    void testGetDaemonThreadCount_nonNegative() {
        assertThat(ThreadTracker.getDaemonThreadCount()).isNotNegative();
    }

    @Test
    void testAllThreadInfos_returnsNonEmpty() {
        var infos = ThreadTracker.allThreadInfos();
        assertThat(infos).isNotEmpty();
    }

    @Test
    void testAllThreadInfos_withFilter_filtersCorrectly() {
        // Filter for the current test thread by name
        var currentThreadName = Thread.currentThread().getName();
        var infos = ThreadTracker.allThreadInfos(
                t -> t.getThreadName().equals(currentThreadName));
        assertThat(infos).isNotEmpty();
        assertThat(infos).allMatch(
                t -> t.getThreadName().equals(currentThreadName));
    }

    @Test
    void testAllThreadInfos_withNonMatchingFilter_returnsEmpty() {
        var infos = ThreadTracker.allThreadInfos(
                t -> t.getThreadName().equals(
                        "this-thread-name-should-never-exist-xyz-123"));
        assertThat(infos).isEmpty();
    }

    @Test
    void testResetPeakThreadCount_noException() {
        // Should not throw; peak count ≥ current after reset
        ThreadTracker.resetPeakThreadCount();
        assertThat(ThreadTracker.getPeakThreadCount())
                .isGreaterThanOrEqualTo(ThreadTracker.getLiveThreadCount());
    }
}
