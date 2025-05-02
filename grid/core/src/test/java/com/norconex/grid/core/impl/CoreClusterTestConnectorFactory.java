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
package com.norconex.grid.core.impl;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.cluster.ClusterConnectorFactory;
import com.norconex.grid.core.mocks.MockStorage;
import com.norconex.grid.core.storage.GridStorage;

public class CoreClusterTestConnectorFactory
        implements ClusterConnectorFactory {

    @Override
    public GridConnector create(String gridName, String nodeName) {
        return Configurable.configure(
                new CoreGridConnector(createGridStorage()),
                cfg -> cfg.setGridName(gridName)
                        .setNodeName(nodeName)
                        .setProtocols(
                                CoreClusterTestProtocols.createProtocols()));
    }

    protected GridStorage createGridStorage() {
        return new MockStorage();
    }

}
