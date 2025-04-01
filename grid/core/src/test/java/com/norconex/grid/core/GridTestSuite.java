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

import org.junit.jupiter.api.Nested;

import com.norconex.grid.core.compute.GridComputeTest;
import com.norconex.grid.core.pipeline.GridPipelineTest;
import com.norconex.grid.core.storage.GridStorageTest;

/**
 * Main test class to implement for grid implementations.
 */
public abstract class GridTestSuite extends AbstractGridTest {

    @Nested
    class ComputeTest extends GridComputeTest {
        @Override
        protected GridConnector getGridConnector() {
            return GridTestSuite.this.getGridConnector();
        }
    }

    @Nested
    class PipelineTest extends GridPipelineTest {
        @Override
        protected GridConnector getGridConnector() {
            return GridTestSuite.this.getGridConnector();
        }
    }

    @Nested
    class StorageTest extends GridStorageTest {
        @Override
        protected GridConnector getGridConnector() {
            return GridTestSuite.this.getGridConnector();
        }
    }

}
