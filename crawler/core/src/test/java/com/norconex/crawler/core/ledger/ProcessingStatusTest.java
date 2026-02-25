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
package com.norconex.crawler.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessingStatusTest {

    @Test
    void is_sameStatus_returnsTrue() {
        assertThat(ProcessingStatus.QUEUED.is(ProcessingStatus.QUEUED))
                .isTrue();
        assertThat(ProcessingStatus.PROCESSED.is(ProcessingStatus.PROCESSED))
                .isTrue();
        assertThat(ProcessingStatus.PROCESSING.is(ProcessingStatus.PROCESSING))
                .isTrue();
        assertThat(ProcessingStatus.UNTRACKED.is(ProcessingStatus.UNTRACKED))
                .isTrue();
    }

    @Test
    void is_differentStatus_returnsFalse() {
        assertThat(ProcessingStatus.QUEUED.is(ProcessingStatus.PROCESSED))
                .isFalse();
        assertThat(ProcessingStatus.PROCESSING.is(ProcessingStatus.QUEUED))
                .isFalse();
    }

    @Test
    void is_nullArgument_returnsFalse() {
        assertThat(ProcessingStatus.QUEUED.is(null)).isFalse();
        assertThat(ProcessingStatus.PROCESSED.is(null)).isFalse();
    }

    @Test
    void of_validName_returnsEnum() {
        assertThat(ProcessingStatus.of("QUEUED"))
                .isEqualTo(ProcessingStatus.QUEUED);
        assertThat(ProcessingStatus.of("PROCESSED"))
                .isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(ProcessingStatus.of("PROCESSING"))
                .isEqualTo(ProcessingStatus.PROCESSING);
        assertThat(ProcessingStatus.of("UNTRACKED"))
                .isEqualTo(ProcessingStatus.UNTRACKED);
    }

    @Test
    void of_lowercaseName_returnsEnum() {
        // of() upper-cases the string before matching
        assertThat(ProcessingStatus.of("queued"))
                .isEqualTo(ProcessingStatus.QUEUED);
        assertThat(ProcessingStatus.of("processed"))
                .isEqualTo(ProcessingStatus.PROCESSED);
    }

    @Test
    void of_mixedCaseWithSpaces_returnsEnum() {
        assertThat(ProcessingStatus.of("  Queued  "))
                .isEqualTo(ProcessingStatus.QUEUED);
    }

    @Test
    void of_nullInput_returnsNull() {
        assertThat(ProcessingStatus.of(null)).isNull();
    }

    @Test
    void of_blankInput_returnsNull() {
        assertThat(ProcessingStatus.of("")).isNull();
        assertThat(ProcessingStatus.of("   ")).isNull();
    }

    @Test
    void of_unknownStatus_returnsNull() {
        assertThat(ProcessingStatus.of("UNKNOWN_STATUS")).isNull();
        assertThat(ProcessingStatus.of("DONE")).isNull();
    }
}
