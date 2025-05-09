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
package com.norconex.grid.core.storage;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A set of unique strings. Order is not guaranteed.
 *
 */
public interface GridSet extends GridStore<String> {

    /**
     * Adds the given object to the set.
     * @param id a unique identifier
     * @return <code>true</code> if the object was added (i.e., an equal object
     *     did not already exist).
     */
    boolean add(String id);

    /**
     * Loops through all elements of this set or until the predicate returns
     * <code>false</code>.
     * @param predicate the predicate applied to each items
     * @return <code>true</code> if the predicate returned <code>true</code>
     *   for all strings
     */
    boolean forEach(Predicate<String> predicate);

    /**
     * {@inheritDoc}
     * Key is always {@code null}. This method is implemented to conforms
     * to {@link GridStore#forEach(BiPredicate)}. Use
     * {@link #forEach(Predicate)} for a more appropriate method.
     */
    @Override
    default boolean forEach(BiPredicate<String, String> predicate) {
        return forEach(value -> predicate.test(null, value));
    }
}
