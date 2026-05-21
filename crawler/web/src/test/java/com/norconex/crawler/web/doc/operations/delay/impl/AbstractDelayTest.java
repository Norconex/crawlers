/* Copyright 2026 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.delay.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class AbstractDelayTest {

    // Minimal concrete subclass that exposes the protected delay method.
    private static class TestDelay extends AbstractDelay {
        @Override
        public void delay(long expectedDelayMillis, String url) {
            // delegate to the protected overload with "now" as last-hit
        }

        void delayFrom(long expectedDelayMillis, long lastHitMillis) {
            delay(expectedDelayMillis, lastHitMillis);
        }
    }

    @Test
    void zeroExpectedDelayIsNoop() {
        var delay = new TestDelay();
        var before = System.currentTimeMillis();
        assertThatNoException().isThrownBy(
                () -> delay.delayFrom(0, System.currentTimeMillis()));
        // Should return almost immediately — well under 500 ms
        assertThat(System.currentTimeMillis() - before).isLessThan(500);
    }

    @Test
    void negativeExpectedDelayIsNoop() {
        var delay = new TestDelay();
        var before = System.currentTimeMillis();
        assertThatNoException().isThrownBy(
                () -> delay.delayFrom(-100, System.currentTimeMillis()));
        assertThat(System.currentTimeMillis() - before).isLessThan(500);
    }

    @Test
    void elapsedTimeExceedsExpectedDelay_noExtraSleep() {
        // lastHit was 500 ms ago, but we only want a 10 ms delay
        var delay = new TestDelay();
        var lastHit = System.currentTimeMillis() - 500;
        var before = System.currentTimeMillis();
        assertThatNoException().isThrownBy(() -> delay.delayFrom(10, lastHit));
        // The 10 ms delay has already passed, so we only sleep the 1 ms guard
        assertThat(System.currentTimeMillis() - before).isLessThan(500);
    }

    @Test
    void remainingDelayIsEnforced() {
        // lastHit is "now", and we want at least 20 ms of delay
        var delay = new TestDelay();
        var lastHit = System.currentTimeMillis();
        var before = System.currentTimeMillis();
        assertThatNoException().isThrownBy(() -> delay.delayFrom(20, lastHit));
        // Must have waited at least 20 ms (plus the 1 ms guard sleep)
        assertThat(System.currentTimeMillis() - before)
                .isGreaterThanOrEqualTo(20);
    }

    @Test
    void threadDelayDelegatesToProtectedMethod() {
        var td = new ThreadDelay();
        assertThatNoException().isThrownBy(() -> {
            td.delay(0, "http://example.com");
            td.delay(5, "http://example.com");
        });
    }
}
