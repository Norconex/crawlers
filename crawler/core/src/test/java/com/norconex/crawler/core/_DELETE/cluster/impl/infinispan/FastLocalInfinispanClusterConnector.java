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
package com.norconex.crawler.core._DELETE.cluster.impl.infinispan;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterConnector;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanClusterConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Fast InfinispanClusterConnector optimized for local testing.
 * Uses a test-optimized configuration for quick cluster formation.
 *
 * <p><b>DO NOT USE IN PRODUCTION</b> - may sacrifice reliability for speed.</p>
 *
 * <p>To use this in tests, configure your crawler with:</p>
 * <pre>
 * crawlConfig.setClusterConnector(new FastLocalInfinispanClusterConnector());
 * </pre>
 */
@Slf4j
@Deprecated

public class FastLocalInfinispanClusterConnector implements ClusterConnector {

    private final InfinispanClusterConfig configuration =
            new InfinispanClusterConfig();

    public FastLocalInfinispanClusterConnector() {
        // Use test-specific configuration
        //        var builderHolder =
        //                InfinispanUtil
        //                        .configBuilderHolder("/cache/infinispan-fast-test.xml");
        //        configuration.setInfinispan(builderHolder);
        //        LOG.info(
        //                "Using FastLocalInfinispanClusterConnector with optimized settings");
    }

    @Override
    public Cluster connect() {
        return new InfinispanCluster(configuration);
    }
}
