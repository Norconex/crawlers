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

import java.io.Closeable;

import com.norconex.crawler.core.Crawler;

/**
 * Underlying system used to compute tasks and store crawl session data.
 */
//High-level interface for abstracting different grid technologies
public interface GridSystem extends Closeable {
    <T> GridCache<T> getCache(String name, Class<? extends T> type); // Abstract access to a specific grid cache

    /**
     * Whereas caches typically serve a unique purpose, the global cache is
     * a catch-all cache for arbitrary one-off values (e.g., global state
     * information).
     * @return global cache
     */
    GridCache<String> getGlobalCache(); // Abstract access to a specific grid cache

    <T> GridQueue<T> getQueue(String name, Class<? extends T> type); // Abstract access to a specific grid queue

    GridCompute getCompute(); // Abstract compute operations (tasks, jobs)

    //    GridService getService(); // Abstract access to grid services

    // Get the distributed compute interface
    //    DistributedCompute getCompute();
    //
    //    // Get the distributed queue interface
    //    <T> DistributedQueue<T> getQueue(String name);
    //
    //    // Get the distributed key-value store interface
    //    <K, V> DistributedKeyValueStore<K, V> getKeyValueStore(String name);
    //
    //    // Get the distributed transaction interface
    //    DistributedTransaction getTransaction();

    /**
     * Initialize the grid client and prepare the cluster as needed
     * before any compute tasks. Run once per command, by the grid client only.
     * @param crawler the crawler
     */
    void clientInit(Crawler crawler);

    //    /**
    //     * Returns the initialized crawler running on the instance asking for it.
    //     * Typically called inside a grid task to get the local instance
    //     * @return crawler
    //     */
    //    Crawler getLocalCrawler();

    /**
     * Closes the grid client.
     */
    @Override
    void close();
}
