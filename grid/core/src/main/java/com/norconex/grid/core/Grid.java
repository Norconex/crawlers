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
package com.norconex.grid.core;

import java.io.Closeable;

import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.pipeline.GridPipeline;
import com.norconex.grid.core.storage.GridStorage;

/**
 * Underlying system used to compute tasks and store crawl session data.
 */
//High-level interface for abstracting different grid technologies

//TODO MAYBE: leave this as is, but have a wrapper when used that caches
// each of compute() storage(), etc.
public interface Grid extends Closeable {

    //TODO use SPI to detect which grid/storage implementation to use
    // but also offer to optionally pass one in constructor instead.

    GridCompute compute();

    GridPipeline pipeline();

    GridStorage storage();

    /**
     * Generated ID unique to each node instances in a cluster.
     * Does not persist: a new one is created each time a new grid instance
     * is created
     * @return unique instance id
     */
    //TODO used?  How does it related to NODE_INDEX?
    String getNodeName();

    String getClusterName();

    /**
     * Closes the local connection, releasing any local resources associated
     * to it. If there are still pipelines or jobs running, a stop request
     * will be made in an attempt to end cleanly.
     */
    @Override
    void close();

}
