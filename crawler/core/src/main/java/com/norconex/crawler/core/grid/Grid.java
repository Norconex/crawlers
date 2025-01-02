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

/**
 * Underlying system used to compute tasks and store crawl session data.
 */
//High-level interface for abstracting different grid technologies
public interface Grid extends Closeable {

    GridCompute compute();

    GridStorage storage();

    GridServices services();

    /**
     * Generated ID unique to each node instances in a cluster.
     * Does not persist: a new one is created each time a new grid instance
     * is created
     * @return unique instance id
     */
    String nodeId();

    /**
     * Closes the local connection, releasing any local resources associated
     * to it.
     */
    @Override
    void close();
}
