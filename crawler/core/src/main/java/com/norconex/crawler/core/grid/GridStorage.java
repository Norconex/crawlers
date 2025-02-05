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

import java.util.Set;
import java.util.function.Consumer;

public interface GridStorage {
    <T> GridCache<T> getCache(String name, Class<? extends T> type);

    /**
     * Whereas caches typically serve a unique purpose, the global cache is
     * a catch-all cache for arbitrary one-off values (e.g., global state
     * information).
     * @return global cache
     */
    GridCache<String> getGlobalCache();

    <T> GridQueue<T> getQueue(String name, Class<? extends T> type);

    <T> GridSet<T> getSet(String name, Class<? extends T> type);

    Set<String> getStoreNames();

    void forEachStore(Consumer<GridStore<?>> storeConsumer);

    void clean();
}
