/* Copyright 2025 Norconex Inc.
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

import java.io.Serializable;

import org.junit.jupiter.api.Nested;

import com.norconex.grid.core.cluster.WithCluster;
import com.norconex.grid.core.compute.GridComputePipelineTest;
import com.norconex.grid.core.compute.GridComputeTaskTest;
import com.norconex.grid.core.storage.GridStorageTest;

/**
 * Implementors need to annotate their subclass with
 * {@literal @}{@link WithCluster} (or a composite annotation including it).
 */
public abstract class AbstractGridTestSuite implements Serializable {

    private static final long serialVersionUID = 1L;

    @Nested
    class StorageTest extends GridStorageTest {

        private static final long serialVersionUID = 1L;
    }

    @Nested
    class ComputeTaskTest extends GridComputeTaskTest {

        private static final long serialVersionUID = 1L;
    }

    @Nested
    class ComputePipelineTest extends GridComputePipelineTest {

        private static final long serialVersionUID = 1L;
    }

    //    @Nested
    //    class PipelineTest extends GridPipelineTest {
    //        @Override
    //        protected GridConnector getGridConnector(String gridName) {
    //            return GridTestSuite.this.getGridConnector(gridName);
    //        }
    //    }

    //    @Nested
    //    @ClusterTest
    //    class StorageTest extends GridStorageTest {
    //        @Override
    //        protected GridConnector getGridConnector(String gridName) {
    //            return GridTestSuite.this.getGridConnector(gridName);
    //        }
    //    }

    //    @Nested
    //        @ClusterTest
    //        class NestedTest { ... }
    //
    //        @Grid(connectorProvider = SpecialConnectorProvider.class)
    //        class SpecialNestedTest { ... }
}
