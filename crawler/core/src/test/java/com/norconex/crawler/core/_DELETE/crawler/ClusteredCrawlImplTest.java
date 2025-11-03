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
package com.norconex.crawler.core._DELETE.crawler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example demonstrating how to use @ClusteredCrawlTest with parameterized
 * node counts. SharedCluster is used automatically - no need to call it
 * explicitly!
 */
class ClusteredCrawlImplTest {

    /**
     * This test runs three times: once with 1 node, once with 2 nodes,
     * and once with 3 nodes. SharedCluster automatically reuses containers
     * across iterations for performance.
     */
    @ClusteredCrawlTest(nodes = { 1, 2, 3 })
    void testWithMultipleNodeCounts(ClusteredCrawlContext context) {
        assertThat(context.getClient().getNodes()).isNotEmpty();

        // This assertion will be checked for each node count
        assertThat(context.getClient().getNodes())
                .hasSize(context.getNodeCount());
        assertThat(context.getNodeCount()).isIn(1, 2, 3);

        // You can have node-count-specific logic
        if (context.getNodeCount() == 1) {
            // Test behavior specific to 1-node cluster
            assertThat(context.getClient().getNode1()).isNotNull();
            assertThat(context.getClient().getNodes()).hasSize(1);
            assertThat(context.getNodeCount()).isEqualTo(1);
        } else if (context.getNodeCount() == 2) {
            // Test behavior specific to 2-node clusters
            assertThat(context.getClient().getNode2()).isNotNull();
            assertThat(context.getClient().getNodes()).hasSize(2);
            assertThat(context.getNodeCount()).isEqualTo(2);
        } else if (context.getNodeCount() == 3) {
            // Test behavior specific to 3-node clusters
            assertThat(context.getClient().getNode3()).isNotNull();
            assertThat(context.getClient().getNodes()).hasSize(3);
            assertThat(context.getNodeCount()).isEqualTo(3);
        }

        // Copy files to cluster, execute commands, etc.
        var configPath =
                context.getClient().copyStringToClusterFile("test-config");
        assertThat(configPath).isNotNull();
    }
}
