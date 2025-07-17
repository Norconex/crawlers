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
package com.norconex.grid.calcite;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.grid.core.AbstractGridTestSuite;
import com.norconex.grid.core.cluster.WithCluster;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@WithCluster(connectorFactory = H2ClusterTestConnectorFactory.class)
class H2GridTestSuite extends AbstractGridTestSuite {
    private static final long serialVersionUID = 1L;

    @BeforeAll
    static void shareBaseTempDir(@TempDir Path tempDir) {
        H2ClusterTestConnectorFactory.tempBasePath = tempDir;
    }
}
