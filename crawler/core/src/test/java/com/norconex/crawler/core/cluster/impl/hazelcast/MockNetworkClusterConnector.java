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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.norconex.crawler.core.cluster.Cluster;

/**
 * A test-only {@link HazelcastClusterConnector} that creates its
 * {@link com.hazelcast.core.HazelcastInstance HazelcastInstance} via the
 * shared {@link com.hazelcast.test.TestHazelcastInstanceFactory
 * TestHazelcastInstanceFactory} mock-network layer provided by
 * {@link HazelcastTestSupport}.
 *
 * <p>Use this connector when running multiple crawler instances inside
 * a single JVM (embedded / in-process) so that they discover each other
 * through the mock network instead of real TCP sockets.  This eliminates
 * port-range allocation, cuts per-node startup from seconds to
 * milliseconds, and allows cluster-level tests to run without spawning
 * child JVM processes.</p>
 *
 * <h3>Example usage</h3>
 * <pre>
 *   var cfg = new CrawlConfig();
 *   cfg.getClusterConfig().setClustered(true);
 *   cfg.getClusterConfig().setConnector(new MockNetworkClusterConnector());
 *   // … configure start references, fetcher, etc.
 *   new Crawler(driver, cfg).crawl();
 * </pre>
 *
 * @see HazelcastTestSupport#newCluster(HazelcastClusterConnectorConfig)
 */
public class MockNetworkClusterConnector extends HazelcastClusterConnector {

    @Override
    public Cluster connect() {
        return HazelcastTestSupport.newCluster(getConfiguration());
    }
}
