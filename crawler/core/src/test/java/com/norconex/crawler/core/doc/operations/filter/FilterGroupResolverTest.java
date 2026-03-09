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
package com.norconex.crawler.core.doc.operations.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link FilterGroupResolver}.
 */
@Timeout(30)
class FilterGroupResolverTest {

    // A simple OnMatchFilter implementation for testing
    static class OnMatchFilterStub implements OnMatchFilter {
        private final OnMatch onMatch;

        OnMatchFilterStub(OnMatch onMatch) {
            this.onMatch = onMatch;
        }

        @Override
        public OnMatch getOnMatch() {
            return onMatch;
        }
    }

    // A plain filter that does NOT implement OnMatchFilter
    static class PlainFilter {
    }

    // -----------------------------------------------------------------
    // Empty filter list
    // -----------------------------------------------------------------

    @Test
    void emptyFilters_accepts() {
        var resolver = FilterGroupResolver.<String>builder()
                .filterResolver(f -> true)
                .build();
        assertThat(resolver.accepts(List.of())).isTrue();
    }

    // -----------------------------------------------------------------
    // Non-OnMatch (plain) filters
    // -----------------------------------------------------------------

    @Test
    void plainFilter_allAccepted_returnsTrue() {
        var resolver = FilterGroupResolver.<PlainFilter>builder()
                .filterResolver(f -> true)
                .build();
        assertThat(resolver.accepts(
                List.of(new PlainFilter(), new PlainFilter()))).isTrue();
    }

    @Test
    void plainFilter_oneRejects_returnsFalse() {
        var filters = List.of(new PlainFilter(), new PlainFilter());
        var count = new int[] { 0 };
        var resolver = FilterGroupResolver.<PlainFilter>builder()
                .filterResolver(f -> count[0]++ == 0) // first accepts, second rejects
                .build();
        assertThat(resolver.accepts(filters)).isFalse();
    }

    @Test
    void plainFilter_rejects_callsOnRejectedCallback() {
        var rejected = new ArrayList<PlainFilter>();
        var filter = new PlainFilter();
        var resolver = FilterGroupResolver.<PlainFilter>builder()
                .filterResolver(f -> false)
                .onRejected(rejected::add)
                .build();
        resolver.accepts(List.of(filter));
        assertThat(rejected).containsExactly(filter);
    }

    @Test
    void plainFilter_accepted_callsOnAcceptedCallback() {
        var accepted = new ArrayList<PlainFilter>();
        var filter = new PlainFilter();
        var resolver = FilterGroupResolver.<PlainFilter>builder()
                .filterResolver(f -> true)
                .onAccepted(accepted::add)
                .build();
        resolver.accepts(List.of(filter));
        assertThat(accepted).containsExactly(filter);
    }

    // -----------------------------------------------------------------
    // INCLUDE filters
    // -----------------------------------------------------------------

    @Test
    void includeFilter_atLeastOneMatches_returnsTrue() {
        var f1 = new OnMatchFilterStub(OnMatch.INCLUDE);
        var f2 = new OnMatchFilterStub(OnMatch.INCLUDE);
        var count = new int[] { 0 };
        var resolver = FilterGroupResolver.<OnMatchFilter>builder()
                .filterResolver(f -> count[0]++ == 0) // first matches
                .build();
        assertThat(resolver.accepts(List.of(f1, f2))).isTrue();
    }

    @Test
    void includeFilter_noneMatch_returnsFalse() {
        var f1 = new OnMatchFilterStub(OnMatch.INCLUDE);
        var f2 = new OnMatchFilterStub(OnMatch.INCLUDE);
        var resolver = FilterGroupResolver.<OnMatchFilter>builder()
                .filterResolver(f -> false)
                .build();
        assertThat(resolver.accepts(List.of(f1, f2))).isFalse();
    }

    @Test
    void includeFilter_noneMatch_callsOnRejectedNoInclude() {
        var rejectedCollections = new ArrayList<Collection<OnMatchFilter>>();
        var f1 = new OnMatchFilterStub(OnMatch.INCLUDE);
        var f2 = new OnMatchFilterStub(OnMatch.INCLUDE);
        var filters = List.<OnMatchFilter>of(f1, f2);
        var resolver = FilterGroupResolver.<OnMatchFilter>builder()
                .filterResolver(f -> false)
                .onRejectedNoInclude(rejectedCollections::add)
                .build();
        resolver.accepts(filters);
        assertThat(rejectedCollections).hasSize(1);
        assertThat(rejectedCollections.get(0)).containsExactlyElementsOf(
                filters);
    }

    // -----------------------------------------------------------------
    // EXCLUDE filters (via OnMatchFilter with EXCLUDE)
    // -----------------------------------------------------------------

    @Test
    void excludeFilter_notTriggered_returnsTrue() {
        var filter = new OnMatchFilterStub(OnMatch.EXCLUDE);
        var resolver = FilterGroupResolver.<OnMatchFilter>builder()
                .filterResolver(f -> true) // filter "accepts" — not excluding
                .build();
        // EXCLUDE filter: when filterResolver returns true → not rejected
        // (accepted), when false → rejected
        // Wait: looking at FilterGroupResolver.isRejected:
        // non-include filter: if !accepted → rejected
        // EXCLUDE filter: if filterResolver returns true (= not excluding) → not rejected
        assertThat(resolver.accepts(List.of(filter))).isTrue();
    }

    @Test
    void excludeFilter_triggered_returnsFalse() {
        var filter = new OnMatchFilterStub(OnMatch.EXCLUDE);
        var resolver = FilterGroupResolver.<OnMatchFilter>builder()
                .filterResolver(f -> false) // filter "rejects" — excluding
                .build();
        assertThat(resolver.accepts(List.of(filter))).isFalse();
    }

    // -----------------------------------------------------------------
    // Mixed filters
    // -----------------------------------------------------------------

    @Test
    void mixed_includeAndPlain_allPass_returnsTrue() {
        var includeFilter = new OnMatchFilterStub(OnMatch.INCLUDE);
        var plainFilter = new PlainFilter();

        var resolver = FilterGroupResolver.<Object>builder()
                .filterResolver(f -> true) // all pass
                .build();
        assertThat(resolver.accepts(List.of(includeFilter, plainFilter)))
                .isTrue();
    }

    @Test
    void mixed_includeMatches_plainRejects_returnsFalse() {
        var includeFilter = new OnMatchFilterStub(OnMatch.INCLUDE);
        var plainFilter = new PlainFilter();

        var resolver = FilterGroupResolver.<Object>builder()
                .filterResolver(f -> f instanceof OnMatchFilter) // include passes, plain fails
                .build();
        assertThat(resolver.accepts(List.of(includeFilter, plainFilter)))
                .isFalse();
    }
}
