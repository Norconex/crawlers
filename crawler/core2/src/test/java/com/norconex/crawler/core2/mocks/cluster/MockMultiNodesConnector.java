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
package com.norconex.crawler.core2.mocks.cluster;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanClusterConnector;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanUtil;

public final class MockMultiNodesConnector extends InfinispanClusterConnector {
    @Override
    public Cluster connect() {
        return Configurable.configure(
                new InfinispanClusterConnector(),
                c -> c.setInfinispan(InfinispanUtil.configBuilderHolder(
                        "/cache/infinispan-cluster-test.xml")))
                //"/cache/infinispan.xml")))
                .connect();
    }
}
