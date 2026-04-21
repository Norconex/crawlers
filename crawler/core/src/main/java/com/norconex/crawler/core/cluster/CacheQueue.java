/* Copyright 2026 Norconex Inc.
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

package com.norconex.crawler.core.cluster;

import java.util.List;

public interface CacheQueue<T> {

    void add(T item);

    T poll();

    List<T> pollBatch(int batchSize);

    int size();

    boolean isEmpty();

    void clear();

    /**
     * Returns whether this queue persists data across restarts.
     * @return true if the queue is persistent, false if ephemeral
     */
    boolean isPersistent();

    /**
     * Gets the name of this cache.
     * @return cache name
     */
    String getName();

}
