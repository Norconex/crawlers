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
package com.norconex.crawler.core.grid;

import java.io.Closeable;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.crawler.core.grid.compute.DefaultGridCompute;
import com.norconex.crawler.core.grid.compute.GridCompute;
import com.norconex.crawler.core.grid.pipeline.DefaultGridPipeline;
import com.norconex.crawler.core.grid.pipeline.GridPipeline;
import com.norconex.crawler.core.grid.storage.GridStorage;

/**
 * Underlying system used to compute tasks and store crawl session data.
 */
//High-level interface for abstracting different grid technologies
public interface Grid extends Closeable {

    String KEY_NODE_ID = "NODE_ID";
    String KEY_SESSION_ID = "SESSION_ID";

    //TODO evaluate if we want those mandatory or only if not provided
    // by configurer and/or script
    String NODE_ID =
            SystemUtil.getEnvironmentOrProperty(KEY_NODE_ID);
    String SESSION_ID =
            SystemUtil.getEnvironmentOrProperty(KEY_SESSION_ID);

    default GridCompute compute() {
        return new DefaultGridCompute(this);
    }

    default GridPipeline pipeline() {
        return new DefaultGridPipeline(this);
    }

    GridStorage storage();

    //    GridServices services();

    GridTransactions transactions();

    /**
     * Generated ID unique to each node instances in a cluster.
     * Does not persist: a new one is created each time a new grid instance
     * is created
     * @return unique instance id
     */
    //TODO used?  How does it related to NODE_INDEX?
    String nodeId();

    /**
     * Closes the local connection, releasing any local resources associated
     * to it.
     */
    @Override
    void close();
}
