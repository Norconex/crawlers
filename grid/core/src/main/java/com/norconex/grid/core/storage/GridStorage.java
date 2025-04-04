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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface GridStorage {
    <T> GridMap<T> getMap(String name, Class<? extends T> type);

    /**
     * Whereas stores typically serve a unique purpose, the "globals" store is
     * a catch-all for arbitrary key-value pairs (e.g., global state
     * information).
     * @return globals store
     */
    GridMap<String> getGlobals();

    <T> GridQueue<T> getQueue(String name, Class<? extends T> type);

    GridSet getSet(String name);

    Set<String> getStoreNames();

    void forEachStore(Consumer<GridStore<?>> storeConsumer);

    boolean storeExists(String name);

    void destroy();

    <T> T runInTransaction(Callable<T> callable);

    <T> Future<T> runInTransactionAsync(Callable<T> callable);

}
