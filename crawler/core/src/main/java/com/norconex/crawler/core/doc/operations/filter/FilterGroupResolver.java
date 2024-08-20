/* Copyright 2022 Norconex Inc.
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

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.Builder;

/**
 * Resolves a group of filters, marking the group as accepted
 * (<code>true</code>) if All following conditions are met:
 *
 * <ul>
 *   <li>
 *     All filters NOT implementing {@link OnMatchFilter} return
 *     <code>true</code>.
 *   </li>
 *   <li>
 *     All filters implementing {@link OnMatchFilter} and set
 *     to {@link OnMatch#EXCLUDE} return <code>false</code>.
 *   </li>
 *   <li>
 *     If there is at least one filter implementing {@link OnMatchFilter}
 *     set to {@link OnMatch#INCLUDE}, then at least of those "include"
 *     filters must return <code>true</code>.
 *   </li>
 * </ul>
 * @param <T> filter type
 */
@Builder
public class FilterGroupResolver<T> {

    private final Predicate<T> filterResolver;
    private Consumer<T> onAccepted;
    private Consumer<T> onRejected;
    private Consumer<Collection<T>> onRejectedNoInclude;

    public boolean accepts(Collection<T> filters) {
        return !isRejected(filters);
    }

    private boolean isRejected(Collection<T> filters) {
        var hasIncludes = false;
        var atLeastOneIncludeMatch = false;
        for (T filter : filters) {
            var accepted = filterResolver.test(filter);

            // Deal with includes
            if (isIncludeFilter(filter)) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }

            // Deal with exclude and non-OnMatch filters
            if (!accepted) {
                Optional.ofNullable(onRejected).ifPresent(
                        c -> c.accept(filter));
                return true;
            }

            Optional.ofNullable(onAccepted).ifPresent(c -> c.accept(filter));
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            Optional.ofNullable(onRejectedNoInclude).ifPresent(
                    c -> c.accept(filters));
            return true;
        }
        return false;
    }

    private static boolean isIncludeFilter(Object filter) {
        return filter instanceof OnMatchFilter f
                && OnMatch.INCLUDE == f.getOnMatch();
    }
}
