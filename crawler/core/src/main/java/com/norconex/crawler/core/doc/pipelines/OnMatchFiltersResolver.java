/* Copyright 2024-2025 Norconex Inc.
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

import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.OnMatchFilter;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the predicate against each list items and return <code>true</code>
 * if all entries are accepted, or if there are items implementing
 * {@link OnMatchFilter}, if at least one of them is "included".
 *
 * @param <S> type of the subject being tested by the list of filters
 * @param <F> type of filters in the list of filters
 */
@Builder
@Slf4j
public class OnMatchFiltersResolver<S, F> {

    private final S subject;
    private final List<F> filters;
    /** Predicate matching a filter against the subject. */
    private final BiPredicate<S, F> predicate;
    /**
     * Optional callback. Receives all filters or the rejecting one based
     * on context, along with possible rejection message.
     */
    private final BiConsumer<List<F>, String> onRejected;

    public boolean isAccepted() {
        if (filters.isEmpty()) {
            return true;
        }

        var hasIncludes = false;
        var atLeastOneIncludeMatch = false;
        for (F filter : filters) {
            var accepted = predicate.test(subject, filter);
            // Deal with includes
            var isAnIncludeFilter = isAnIncludeFilter(filter);
            if (isAnIncludeFilter) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }

            // Deal with exclude and non-OnMatch filters
            if (!accepted) {
                ofNullable(onRejected).ifPresent(
                        c -> c.accept(List.of(filter), ""));
                return false;
            }
            LOG.debug("ACCEPTED Subject={} Filter={}", subject, filter);
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ofNullable(onRejected).ifPresent(
                    c -> c.accept(filters, "No \"include\" filters matched."));
            return false;
        }
        return true;
    }

    private static boolean isAnIncludeFilter(Object filter) {
        return filter instanceof OnMatchFilter onMatchFilter
                && OnMatch.INCLUDE == onMatchFilter.getOnMatch();
    }
}
