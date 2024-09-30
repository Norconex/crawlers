/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.core.store;

import java.io.Closeable;
import java.util.function.BiPredicate;

/**
 * Data store
 * @param <T> type of datastore object
 */
public interface DataStore2<T> extends Closeable {

    /**
     * Gets the store name.
     * @return store name
     */
    String getName();

    // have put/get methods for all impls instead of put/poll for queue and save/? for key-value?
    /**
     * Whether there is already an object in this store under the same id.
     * @param id the object id
     * @return <code>true</code> if id is already present
     */
    boolean contains(String id);

    /**
     * The store size. It is possible for some implementations to approximate
     * this value past a certain threshold.
     * @return store size
     */
    long size();

    /**
     * Wipes all objects from the store.
     */
    void clear();

    /**
     * Close the store implementation (provided there is something to close
     * for the specific implementation).
     */
    @Override
    void close();

    /**
     * Iterate through all objects in the store until the predicate returns
     * <code>false</code> or the end of the store was reached.
     * @param predicate predicate to apply on each object
     * @return <code>true</code> if the store was read entirely
     *     (the predicate returned <code>true</code> for all objects).
     */
    boolean forEach(BiPredicate<String, T> predicate);

    /**
     * Whether the store is empty.
     * @return <code>true</code> if empty
     */
    boolean isEmpty();
}
