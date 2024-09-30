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
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.committer.core.Committer;
import com.norconex.crawler.core.Crawler;

/**
 * Database of some kind used by the crawler to store minimum information
 * required to operate (e.g., tracking its progress, remember previously
 * crawled documents, ...). Not to be confused with {@link Committer}, which
 * is used as the target destination for crawled content.
 */
public interface DataStoreEngine extends Closeable {

    //    /**
    //     * Operations performed on any data stores will be done atomically, provided
    //     * atomicity is supported by the store implementation. Store not supporting
    //     * atomic transactions will execute this method normally, but simply won't
    //     * offer atomicity.
    //     * Refer to the data store documentation for transaction support.
    //     * Try to keep your transactions as short as possible and focused
    //     * on store operations.
    //     * @param <T> the return type
    //     * @param callable code executed involving store transactions.
    //     * @return any value as per the callable passed
    //     * @throws DataStoreException
    //     */
    //    default <T> T atomically(@NonNull FailableCallable<T, Exception> callable)
    //            throws DataStoreException {
    //        try {
    //            //TODO start transaction
    //            return callable.call();
    //            //TODO end transaction
    //        } catch (Exception e) {
    //            throw new DataStoreException(
    //                    "Could not execute operation involving data store.", e);
    //        }
    //    }

    /**
     * Initializes the data store. Called once per crawler execution, before
     * crawling begins.
     * @param crawler the crawler instance
     */
    void init(Crawler crawler);

    /**
     * Wipe out all stores in the data store. Same as invoking
     * {@link #dropStore(String)} for each of the existing stores.
     * @return <code>true</code> if there were stores to clean
     */
    boolean clean();

    /**
     * Closes the data store. Called once after crawling completes.
     */
    @Override
    void close();

    /**
     * Opens (and creates as needed) a store of the given name to store
     * and retrieve objects of the given type.
     * @param <T> class type of the object kept in the store
     * @param name store name
     * @param type a class for type of the object kept in the store
     * @return a data store
     */
    <T> DataStore<T> openStore(String name, Class<? extends T> type);

    /**
     * Eliminates the store matching the given name.
     * @param name the name of the store to delete
     * @return <code>true</code> if there was a store to delete
     */
    boolean dropStore(String name);

    /**
     * Rename a data store.
     * @param dataStore the data store to rename
     * @param newName the new name
     * @return <code>true</code> if a store already exist with the new name and
     *     had to first be deleted
     */
    boolean renameStore(DataStore<?> dataStore, String newName);

    /**
     * Gets the name of all stores in the engine.
     * @return a set of stores
     */
    @JsonIgnore
    Set<String> getStoreNames();

    /**
     * Gets the type of an existing store, or empty if there are no stores
     * of that name.
     * @param name store name
     * @return optional with the store type
     */
    @JsonIgnore
    Optional<Class<?>> getStoreType(String name);
}
