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
public interface Grid extends Closeable {

    //MAYBE: use SPI to detect which grid/storage implementation to use
    // but also offer to optionally pass one in constructor instead.

    GridCompute compute();

    GridPipeline pipeline();

    GridStorage storage();

    /**
     * Logical name unique to each node in a cluster.
     * @return unique node id
     */
    String getNodeName();

    /**
     * The name of the grid we are connected to.
     * @return grid name
     */
    String getGridName();

    /**
     * Closes the local grid connection, releasing any local resources
     * associated to it. If there are still pipelines or jobs running, a stop
     * request will be made in an attempt to end cleanly.
     */
    @Override
    void close();

}
