/* Copyright 2024 Norconex Inc.
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
 * A set of unique objects. Order is not guaranteed.
 *
 * @param <T> set object type
 */
public interface GridSet<T> extends GridStore<T> {

    // if method can be made same as key/value store, pass hint instead
    // to engine when creating stores?

    // Maybe have base DataStore interface for common methods?
    // including startTransaction and stopTransaction? (optionally implemented)

    /**
     * Adds the given object to the set.
     * @param object the object to add to the set
     * @return <code>true</code> if the object was added (i.e., an equal object
     *     did not already exist).
     */
    boolean add(T object);

    /**
     * Loops through all elements of this set or until the predicate returns
     * <code>false</code>.
     * @param predicate the predicate applied to each items
     * @return <code>true</code> if the predicate returned <code>true</code>
     *   for all objects.
     */
    boolean forEach(Predicate<T> predicate);

    /**
     * {@inheritDoc}
     * Key is always <code>null</code>. This method is imlpemented to conforms
     * to {@link GridStore#forEach(BiPredicate)}. Use
     * {@link #forEach(Predicate)} for a more appropriate method.
     */
    @Override
    boolean forEach(BiPredicate<String, T> predicate);
}
