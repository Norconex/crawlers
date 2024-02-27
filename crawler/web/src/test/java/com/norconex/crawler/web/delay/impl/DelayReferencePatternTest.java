/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.web.delay.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DelayReferencePatternTest {

    @Test
    void test() {
        var drp = new DelayReferencePattern(
                ".*abc\\.html$", Duration.ofHours(2));
        assertThat(drp.matches("http://example.com/123abc.html")).isTrue();
        assertThat(drp.matches("http://example.com/123abc.php")).isFalse();
    }
}
