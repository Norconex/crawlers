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
package com.norconex.crawler.core.grid.storage;

import com.norconex.crawler.core.util.SerializableUnaryOperator;

/**
 * <p>
 * Key-value data store for scalars or any type of POJO objects.
 * Implementations should focus on fast retrievals by key.
 * </p>
 * <p>
 * When running a given crawler in a cluster, it is important to chose
 * a distributed implementation (one that can be accessed simultaneously
 * by multiple instances of the same crawler).
 * </p>
 * <p>
 * Unless otherwise mentioned by specific implementations, there are no
 * guarantee of atomicity for all operations.
 * </p>
 *
 * @param <T> type of object stored
 */
public interface GridMap<T> extends GridStore<T> {

    /**
     * Saves a given object under the specified key. It is not mandatory
     * for the object itself to contain the said key.
     * @param key unique identifier for an object (record).
     * @param object the object to save
     * @return <code>true</code> if saving the object modified the store
     *     (by adding a new record, or modifying an existing one).
     */
    boolean put(String key, T object);

    /**
     * Updates the value associated with an key with a function returning
     * the new value. This method is equivalent to
     * first calling {@link #get(String)},
     * modifying the returned value, and calling {@link #put(String, Object)}
     * with the new value. The difference is it gives a chance to the cache
     * implementation do so atomically.
     * If there are currently no value for the supplied key, <code>null</code>
     * will be received. Returning <code>null</code> will effectively remove
     * the existing entry (or do nothing if the entry was not there in the
     * first place).
     *
     * @param key unique identifier for the object
     * @param updater function modifying a value
     * @return <code>true</code> if the existing object was modified.
     */
    boolean update(String key, SerializableUnaryOperator<T> updater);

    /**
     * Retrieve an object previously stored with the given key.
     * @param key the key of the object to retrieve
     * @return optional containing the object, or <code>null</code> if not found
     */
    T get(String key);

    /**
     * Removes from the store the object previously saved under the given key
     * (if any).
     * @param key the key of the object to delete.
     * @return <code>true</code> if an object was deleted (it existed)
     */
    boolean delete(String key);
}
