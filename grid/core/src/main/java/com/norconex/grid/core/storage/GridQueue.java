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

import java.util.Optional;

/**
 * A FIFO queue that do not store duplicates.
 *
 * @param <T> queue object type
 */
public interface GridQueue<T> extends GridStore<T> {

    // if method can be made same as key/value store, pass hint instead
    // to engine when creating stores?

    // Maybe have base DataStore interface for common methods?
    // including startTransaction and stopTransaction? (optionally implemented)

    /**
     * Puts the given object a the bottom of the queue, under the specified key.
     * The key is assumed to be unique and is used to prevent duplicates.
     * Adding an object with an key that is already present in the queue
     * will have no effect (i.e., you can't replace or modify an object
     * once added).
     * It is not mandatory for the object itself to contain the said key but
     * it can be useful if you need to reconcile.
     * @param key unique identifier for an object.
     * @param object the object to add to the queue
     * @return <code>true</code> if the object was queued (i.e., not object
     *     already exist under the same key).
     */
    boolean put(String key, T object);

    /**
     * Return and delete the object at the top of the queue.
     * @return an optional with the object or an empty optional if queue
     *     is empty
     */
    Optional<T> poll();
}
