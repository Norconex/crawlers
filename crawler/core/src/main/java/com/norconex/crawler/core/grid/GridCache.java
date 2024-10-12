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
package com.norconex.crawler.core.grid;

/**
 * <p>
 * Key-value data store for scalars or any type of POJO objects.
 * Implementations should focus on fast retrievals by ID.
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
public interface GridCache<T> extends GridStore<T> {

    /**
     * Saves a given object under the specified id. It is not mandatory
     * for the object itself to contain the said id.
     * @param id unique identifier for an object (record).
     * @param object the object to save
     * @return <code>true</code> if saving the object modified the store
     *     (by adding a new record, or modifying an existing one).
     */
    boolean put(String id, T object);

    /**
     * Retrieve an object previously stored with the given id.
     * @param id the id of the object to retrieve
     * @return optional containing the object, or <code>null</code> if not found
     */
    T get(String id);

    //    /**
    //     * The "first" object in the store. Based on the context, it may actually
    //     * be OK if the object is not exactly the first as long as general
    //     * queue principles apply.
    //     * @return optional with the first object or empty if the store is empty
    //     */
    //    Optional<T> findFirst();
    /**
     * Removes from the store the object previously saved under the given id
     * (if any).
     * @param id the id of the object to delete.
     * @return <code>true</code> if an object was deleted (it existed)
     */
    boolean delete(String id);

    //    /**
    //     * Removes the "first" object from the store and returns it.
    //     * @return optional with the deleted object or empty if none was deleted
    //     */
    //    Optional<T> deleteFirst();
}
