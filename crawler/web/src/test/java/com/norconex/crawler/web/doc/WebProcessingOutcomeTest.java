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
package com.norconex.crawler.web.doc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.ledger.ProcessingOutcome;

class WebProcessingOutcomeTest {

    @Test
    void testRedirectConstantIsNotNull() {
        assertThat(WebProcessingOutcome.REDIRECT).isNotNull();
    }

    @Test
    void testRedirectIsInstanceOfProcessingOutcome() {
        assertThat(WebProcessingOutcome.REDIRECT)
                .isInstanceOf(ProcessingOutcome.class);
    }

    @Test
    void testRedirectEquality() {
        // Two instances with the same state string must be equal
        // (ProcessingOutcome uses @EqualsAndHashCode on the outcome field).
        assertThat(WebProcessingOutcome.REDIRECT)
                .isEqualTo(new WebProcessingOutcome("REDIRECT"));
    }

    @Test
    void testRedirectIsNotGoodState() {
        // REDIRECT is not one of the "good" states (NEW, MODIFIED, UNMODIFIED,
        // PREMATURE).
        assertThat(WebProcessingOutcome.REDIRECT.isGoodState()).isFalse();
    }

    @Test
    void testCustomOutcome() {
        // Custom subclass outcome can be created with a distinct state.
        var custom = new WebProcessingOutcome("TEST_CUSTOM");
        assertThat(custom).isNotEqualTo(WebProcessingOutcome.REDIRECT);
        assertThat(custom).isInstanceOf(ProcessingOutcome.class);
    }
}
