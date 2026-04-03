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
package com.norconex.crawler.core.cluster.impl.memory;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterConnector;

/**
 * Connector that creates a lightweight {@link MemoryCluster} with
 * in-memory caches and no distributed infrastructure.
 *
 * <p><b>No persistence:</b> all data is lost when the JVM exits.
 * For a file-backed single-node cluster, use the MVStore connector
 * instead (which is the default).</p>
 *
 * <p>The cluster instance is cached so that multiple crawler sessions
 * using the same connector share in-memory state (enabling incremental
 * crawling across sessions within the same JVM).</p>
 */
public class MemoryClusterConnector implements ClusterConnector {

    private MemoryCluster cluster;

    @Override
    public synchronized Cluster connect() {
        if (cluster == null) {
            cluster = new MemoryCluster();
        }
        return cluster;
    }
}
