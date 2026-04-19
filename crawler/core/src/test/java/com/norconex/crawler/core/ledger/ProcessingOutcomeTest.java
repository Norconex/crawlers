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
package com.norconex.crawler.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ProcessingOutcomeTest {

    @Test
    void newAndModified_areGoodState() {
        assertThat(ProcessingOutcome.NEW.isGoodState()).isTrue();
        assertThat(ProcessingOutcome.MODIFIED.isGoodState()).isTrue();
    }

    @Test
    void unmodifiedAndPremature_areGoodState() {
        assertThat(ProcessingOutcome.UNMODIFIED.isGoodState()).isTrue();
        assertThat(ProcessingOutcome.PREMATURE.isGoodState()).isTrue();
    }

    @Test
    void error_isNotGoodState() {
        assertThat(ProcessingOutcome.ERROR.isGoodState()).isFalse();
    }

    @Test
    void rejected_isNotGoodState() {
        assertThat(ProcessingOutcome.REJECTED.isGoodState()).isFalse();
    }

    @Test
    void badStatus_isNotGoodState() {
        assertThat(ProcessingOutcome.BAD_STATUS.isGoodState()).isFalse();
    }

    @Test
    void notFound_isNotGoodState() {
        assertThat(ProcessingOutcome.NOT_FOUND.isGoodState()).isFalse();
    }

    @Test
    void isGoodState_staticNull_returnsFalse() {
        assertThat(ProcessingOutcome.isGoodState(null)).isFalse();
    }

    @Test
    void isGoodState_staticNew_returnsTrue() {
        assertThat(ProcessingOutcome.isGoodState(ProcessingOutcome.NEW))
                .isTrue();
    }

    @Test
    void equality_sameOutcome_areEqual() {
        assertThat(ProcessingOutcome.NEW).isEqualTo(ProcessingOutcome.NEW);
    }

    @Test
    void equality_differentOutcomes_areNotEqual() {
        assertThat(ProcessingOutcome.NEW)
                .isNotEqualTo(ProcessingOutcome.MODIFIED);
    }

    @Test
    void serialization_roundTrip() {
        var json = com.norconex.crawler.core.util.SerialUtil
                .toJsonString(ProcessingOutcome.DELETED);
        var restored = com.norconex.crawler.core.util.SerialUtil
                .fromJson(json, ProcessingOutcome.class);
        assertThat(restored).isEqualTo(ProcessingOutcome.DELETED);
    }
}
