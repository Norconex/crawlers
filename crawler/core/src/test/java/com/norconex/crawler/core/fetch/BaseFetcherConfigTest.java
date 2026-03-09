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
package com.norconex.crawler.core.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;

@Timeout(30)
class BaseFetcherConfigTest {

    @Test
    void getReferenceFilters_defaultsToEmptyUnmodifiableList() {
        var cfg = new BaseFetcherConfig();

        assertThat(cfg.getReferenceFilters()).isEmpty();
        assertThatThrownBy(() -> cfg.getReferenceFilters().add(mock()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setReferenceFilters_replacesNullsAndReturnsUnmodifiableView() {
        var cfg = new BaseFetcherConfig();
        var f1 = mock(ReferenceFilter.class);
        var f2 = mock(ReferenceFilter.class);
        var filters = new ArrayList<ReferenceFilter>();
        filters.add(f1);
        filters.add(null);
        filters.add(f2);

        var returned = cfg.setReferenceFilters(filters);

        assertThat(returned).isSameAs(cfg);
        assertThat(cfg.getReferenceFilters()).containsExactly(f1, f2);
        assertThatThrownBy(() -> cfg.getReferenceFilters().add(mock()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setReferenceFilters_withNullClearsExistingFilters() {
        var cfg = new BaseFetcherConfig();
        cfg.setReferenceFilters(List.of(mock(ReferenceFilter.class)));

        cfg.setReferenceFilters(null);

        assertThat(cfg.getReferenceFilters()).isEmpty();
    }
}
