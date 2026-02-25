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
package com.norconex.crawler.core.doc.pipelines;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.OnMatchFilter;

/**
 * Tests for {@link OnMatchFiltersResolver} logic.
 */
class OnMatchFiltersResolverTest {

    // ---------------------------------------------------------
    // Empty filter list
    // ---------------------------------------------------------

    @Test
    void emptyFilters_isAccepted_returnsTrue() {
        var resolver = OnMatchFiltersResolver.<String, String>builder()
                .subject("anything")
                .filters(List.of())
                .predicate((s, f) -> true)
                .build();
        assertThat(resolver.isAccepted()).isTrue();
    }

    // ---------------------------------------------------------
    // Exclude filters (non-OnMatch or OnMatch.EXCLUDE)
    // ---------------------------------------------------------

    @Test
    void excludeFilter_accepts_returnsTrue() {
        // All exclude filters pass → accepted
        var resolver = OnMatchFiltersResolver.<String, String>builder()
                .subject("good-subject")
                .filters(List.of("filter-a", "filter-b"))
                .predicate((s, f) -> true) // always accept
                .build();
        assertThat(resolver.isAccepted()).isTrue();
    }

    @Test
    void excludeFilter_rejects_returnsFalse() {
        var resolver = OnMatchFiltersResolver.<String, String>builder()
                .subject("bad-subject")
                .filters(List.of("rejecting-filter"))
                .predicate((s, f) -> false) // always reject
                .build();
        assertThat(resolver.isAccepted()).isFalse();
    }

    @Test
    void excludeFilter_firstRejects_callsOnRejectedAndReturnsFalse() {
        var rejectedFilters = new ArrayList<String>();
        var msg = new String[1];

        var resolver = OnMatchFiltersResolver.<String, String>builder()
                .subject("subject")
                .filters(List.of("filter-a", "filter-b"))
                .predicate((s, f) -> f.equals("filter-b")) // reject "filter-a"
                .onRejected((filters, message) -> {
                    rejectedFilters.addAll(filters);
                    msg[0] = message;
                })
                .build();

        assertThat(resolver.isAccepted()).isFalse();
        assertThat(rejectedFilters).contains("filter-a");
    }

    // ---------------------------------------------------------
    // Include filters (OnMatchFilter with OnMatch.INCLUDE)
    // ---------------------------------------------------------

    @Test
    void includeFilter_matchesAtLeastOne_returnsTrue() {
        var incFilter1 = buildIncludeFilter();
        var incFilter2 = buildIncludeFilter();

        var resolver = OnMatchFiltersResolver
                .<String, OnMatchFilter>builder()
                .subject("subject")
                .filters(List.of(incFilter1, incFilter2))
                // predicate: match incFilter1 only
                .predicate((s, f) -> f == incFilter1)
                .build();

        assertThat(resolver.isAccepted()).isTrue();
    }

    @Test
    void includeFilter_noneMatch_callsOnRejectedAndReturnsFalse() {
        var incFilter1 = buildIncludeFilter();
        var incFilter2 = buildIncludeFilter();

        var rejectedFilters = new ArrayList<OnMatchFilter>();
        var resolver = OnMatchFiltersResolver
                .<String, OnMatchFilter>builder()
                .subject("subject")
                .filters(List.of(incFilter1, incFilter2))
                .predicate((s, f) -> false) // none match
                .onRejected((filters, message) -> {
                    rejectedFilters.addAll(filters);
                })
                .build();

        assertThat(resolver.isAccepted()).isFalse();
        assertThat(rejectedFilters).hasSize(2);
    }

    @Test
    void includeFilter_noneMatch_noOnRejected_stillReturnsFalse() {
        var incFilter = buildIncludeFilter();
        var resolver = OnMatchFiltersResolver
                .<String, OnMatchFilter>builder()
                .subject("subject")
                .filters(List.of(incFilter))
                .predicate((s, f) -> false)
                // no onRejected callback
                .build();

        assertThat(resolver.isAccepted()).isFalse();
    }

    // ---------------------------------------------------------
    // Mixed: include + exclude filters
    // ---------------------------------------------------------

    @Test
    void mixedFilters_includeMatchesExcludePasses_returnsTrue() {
        var incFilter = buildIncludeFilter();
        var excFilter = "regular-filter";

        // predicate: include filter matches, regular filter passes
        var resolver = OnMatchFiltersResolver
                .<String, Object>builder()
                .subject("subject")
                .filters(List.of(incFilter, excFilter))
                .predicate((s, f) -> true) // all pass
                .build();

        assertThat(resolver.isAccepted()).isTrue();
    }

    @Test
    void mixedFilters_excludeFilterRejects_returnsFalse() {
        var incFilter = buildIncludeFilter();
        var excFilter = "rejecting-regular-filter";

        var resolver = OnMatchFiltersResolver
                .<String, Object>builder()
                .subject("subject")
                .filters(List.of(incFilter, excFilter))
                // Reject the regular (exclude) filter; include passes
                .predicate((s, f) -> f instanceof OnMatchFilter)
                .build();

        assertThat(resolver.isAccepted()).isFalse();
    }

    @Test
    void excludeFilter_rejects_withNullOnRejected_stillReturnsFalse() {
        var resolver = OnMatchFiltersResolver.<String, String>builder()
                .subject("s")
                .filters(List.of("f"))
                .predicate((s, f) -> false)
                // omit onRejected (null) - ensure no NPE
                .build();
        assertThat(resolver.isAccepted()).isFalse();
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private static OnMatchFilter buildIncludeFilter() {
        return new OnMatchFilter() {
            @Override
            public OnMatch getOnMatch() {
                return OnMatch.INCLUDE;
            }
        };
    }
}
