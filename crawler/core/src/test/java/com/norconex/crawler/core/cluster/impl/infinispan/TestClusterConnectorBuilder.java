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
package com.norconex.crawler.core.cluster.impl.infinispan;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterConnector;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanClusterConfig.Preset;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Accessors(fluent = true)
public final class TestClusterConnectorBuilder {

    private boolean cluster;
    private boolean persistent;

    public TestClusterConnector build() {
        if (cluster) {
            return persistent
                    ? new ClusterWithPersistence()
                    : new ClusterNoPersistence();
        }
        return persistent
                ? new LocalWithPersistence()
                : new LocalNoPersistence();
    }

    @RequiredArgsConstructor
    abstract static class TestClusterConnector implements ClusterConnector {
        private final String resource;

        @Override
        public Cluster connect() {
            var config = new InfinispanClusterConfig();
            config.setPreset(Preset.CUSTOM);
            config.setConfigFile(resource);
            LOG.info("Using Infinispan configuration from: " + resource);
            return new InfinispanCluster(config);
        }
    }

    public static class LocalNoPersistence extends TestClusterConnector {
        public LocalNoPersistence() {
            super("cache/test-local-no-persistence.xml");
        }
    }

    public static class LocalWithPersistence extends TestClusterConnector {
        public LocalWithPersistence() {
            super("cache/test-local-with-persistence.xml");
        }
    }

    public static class ClusterNoPersistence extends TestClusterConnector {
        public ClusterNoPersistence() {
            super("cache/test-cluster-no-persistence.xml");
        }
    }

    public static class ClusterWithPersistence extends TestClusterConnector {
        public ClusterWithPersistence() {
            super("cache/test-cluster-with-persistence.xml");
        }
    }
}
