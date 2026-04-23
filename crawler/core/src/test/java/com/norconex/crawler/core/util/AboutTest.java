/* Copyright 2025-2026 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;

/**
 * Unit tests for {@link About}.
 */
@Timeout(30)
class AboutTest {

    @Test
    void testAbout_nullConfig_noException() {
        assertThatNoException().isThrownBy(() -> About.about(null));
        var result = About.about(null);
        assertThat(result).isNotBlank();
    }

    @Test
    void testAbout_withConfig_containsRuntimeInfo() {
        var config = new CrawlConfig();
        var result = About.about(config);

        // Should always list the Java runtime section
        assertThat(result).contains("Runtime:");
        assertThat(result).contains("Version:");
    }

    @Test
    void testAbout_withConfig_noCommitters_showsNone() {
        var config = new CrawlConfig();
        // Default config has no committers configured
        var result = About.about(config);
        assertThat(result).contains("Committers:");
        assertThat(result).contains("<None>");
    }

    @Test
    void testNorconexAscii_notBlank() {
        assertThat(About.NORCONEX_ASCII).isNotBlank();
        // The banner contains the "C R A W L E R" label
        assertThat(About.NORCONEX_ASCII).contains("C R A W L E R");
    }

    @Test
    void testAbout_containsNorconexAsciiArt() {
        var result = About.about(null);
        // The ASCII banner (with C R A W L E R label) should appear in the output
        assertThat(result).contains("C R A W L E R");
    }
}
