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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.norconex.grid.core.Grid;

/**
 * Provides different storage facades for different purposes.
 * Stores are associated with a grid name, and normally persist until
 * explicitly destroyed (except for {@link #getSessionAttributes()}).
 */
public interface GridStorage {
    <T> GridMap<T> getMap(String name, Class<? extends T> type);

    //TODO rename "getSessionMap" or equivalent and clear it everytime
    // the session is reset.  And maybe have the CrawlerContext use that
    // instead of having its own map for state?

    /**
     * Session-scoped string attributes. Whereas stores typically serve a
     * specific purpose, the session attributes map is a catch-all for
     * arbitrary key-value pairs that persist for
     * the duration of a grid session. A grid session survive grid restarts
     * under the same grid name. A session is reset whenever
     * {@link Grid#resetSession()} is invoked.
     * @return session-scoped attributes
     */
    GridMap<String> getSessionAttributes();

    /**
     * Durable string attributes. Whereas stores typically serve a
     * specific purpose, the durable attributes map is a catch-all for
     * arbitrary key-value pairs that persist for any number of session.
     * The values stick for as long as the grid storage layer exists.
     * Unaffected by session resets.
     * @return durable attributes
     */
    GridMap<String> getDurableAttributes();

    <T> GridQueue<T> getQueue(String name, Class<? extends T> type);

    GridSet getSet(String name);

    Set<String> getStoreNames();

    void forEachStore(Consumer<GridStore<?>> storeConsumer);

    boolean storeExists(String name);

    void destroy();

    <T> T runInTransaction(Callable<T> callable);

    <T> Future<T> runInTransactionAsync(Callable<T> callable);

}
