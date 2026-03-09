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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.junit.WithTestWatcherLogging;

/**
 * Unit tests for {@link CrawlState}.
 */
@WithTestWatcherLogging
@Timeout(30)
class CrawlStateTest {

    @ParameterizedTest(name = "{0} should be terminal")
    @EnumSource(
        value = CrawlState.class, names = { "STOPPED", "COMPLETED", "FAILED" }
    )
    void testIsTerminal_terminalStates(CrawlState state) {
        assertThat(state.isTerminal()).isTrue();
    }

    @Test
    void testIsTerminal_runningIsNotTerminal() {
        assertThat(CrawlState.RUNNING.isTerminal()).isFalse();
    }

    @Test
    void testAllValuesExist() {
        // Confirm the enum has exactly the expected constants so that
        // any future additions are noticed here.
        assertThat(CrawlState.values())
                .containsExactlyInAnyOrder(
                        CrawlState.RUNNING,
                        CrawlState.STOPPED,
                        CrawlState.COMPLETED,
                        CrawlState.FAILED);
    }

    @Test
    void testValueOf_knownStates() {
        assertThat(CrawlState.valueOf("RUNNING")).isEqualTo(CrawlState.RUNNING);
        assertThat(CrawlState.valueOf("STOPPED")).isEqualTo(CrawlState.STOPPED);
        assertThat(CrawlState.valueOf("COMPLETED"))
                .isEqualTo(CrawlState.COMPLETED);
        assertThat(CrawlState.valueOf("FAILED")).isEqualTo(CrawlState.FAILED);
    }
}
