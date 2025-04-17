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
package com.norconex.grid.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.Sleeper;

class ThreadTrackerTest {

    @Test
    void testAllThreadInfos() {
        assertThat(ThreadTracker.allThreadInfos()).isNotEmpty();
    }

    @Test
    void testGetLiveThreadCount() {
        var initialCount = ThreadTracker.getLiveThreadCount();
        var latch = new CountDownLatch(1);
        var t = new Thread(() -> {
            while (latch.getCount() > 0) {
                Sleeper.sleepMillis(100);
            }
        });
        t.start();
        try {
            assertThat(ThreadTracker.getLiveThreadCount())
                    .isGreaterThan(initialCount);
        } finally {
            latch.countDown();
        }
    }

    @Test
    @Timeout(5)
    void testGetPeakThreadCount() throws InterruptedException {
        var initialCount = ThreadTracker.getLiveThreadCount();
        var threadStartedLatch = new CountDownLatch(1);
        var endTreadLatch = new CountDownLatch(1);
        var t = new Thread(() -> {
            while (endTreadLatch.getCount() > 0) {
                Sleeper.sleepMillis(100);
                threadStartedLatch.countDown();
            }
        });
        t.start();
        threadStartedLatch.await();
        var peakCount = ThreadTracker.getPeakThreadCount();
        assertThat(peakCount).isGreaterThanOrEqualTo(initialCount);
        endTreadLatch.countDown();

        Sleeper.sleepMillis(100);
        assertThat(ThreadTracker.getPeakThreadCount())
                .isEqualTo(peakCount);

        ThreadTracker.resetPeakThreadCount();
        assertThat(ThreadTracker.getPeakThreadCount())
                .isLessThanOrEqualTo(initialCount);
    }

    @Test
    void testGetDaemonThreadCount() {
        var initialCount = ThreadTracker.getDaemonThreadCount();
        var latch = new CountDownLatch(1);
        var t = new Thread(() -> {
            while (latch.getCount() > 0) {
                Sleeper.sleepMillis(100);
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            assertThat(ThreadTracker.getDaemonThreadCount())
                    .isGreaterThan(initialCount);
        } finally {
            latch.countDown();
        }
    }
}
