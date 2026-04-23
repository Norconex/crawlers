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
package com.norconex.crawler.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.ledger.ProcessingStatus;

@Timeout(30)
class QueryFilterTest {

    @Test
    void ofAll_hasNullFieldsAndValue() {
        var filter = QueryFilter.ofAll();

        assertThat(filter.getFieldName()).isNull();
        assertThat(filter.getFieldValue()).isNull();
    }

    @Test
    void of_setsFieldsCorrectly() {
        var filter = QueryFilter.of("processingStatus",
                ProcessingStatus.QUEUED.name());

        assertThat(filter.getFieldName()).isEqualTo("processingStatus");
        assertThat(filter.getFieldValue())
                .isEqualTo(ProcessingStatus.QUEUED.name());
    }

    @Test
    void of_differentFieldAndValue() {
        var filter = QueryFilter.of("depth", 5);

        assertThat(filter.getFieldName()).isEqualTo("depth");
        assertThat(filter.getFieldValue()).isEqualTo(5);
    }

    @Test
    void equality_sameFields_areEqual() {
        var f1 = QueryFilter.of("key", "value");
        var f2 = QueryFilter.of("key", "value");

        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
    }

    @Test
    void equality_differentFields_areNotEqual() {
        var f1 = QueryFilter.of("key", "value1");
        var f2 = QueryFilter.of("key", "value2");

        assertThat(f1).isNotEqualTo(f2);
    }

    @Test
    void ofAll_twoInstances_areEqual() {
        assertThat(QueryFilter.ofAll()).isEqualTo(QueryFilter.ofAll());
    }
}
