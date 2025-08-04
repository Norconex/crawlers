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
package com.norconex.crawler.core2.cluster.impl.infinispan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

//TODO test persistence (check that already on "core" unit tests

class InfinispanClusterTest {

    @TempDir
    private Path tempDir;

    @Test
    void testSingleMemoryNodeCluster() {
        //this test ensure our test XML configs work for whatever other
        // tests needing them
        InfinispanTestUtil.withSingleMemoryNodeCluster(cluster -> {
            cluster.init(tempDir);
            //            cluster.init(CrawlContextStubber.crawlerContext(tempDir));
            assertThat(cluster.getLocalNode().isCoordinator()).isTrue();
            var cache =
                    cluster.getCacheManager().getCache("cache", String.class);
            assertThat(cache.get("str")).isEmpty();
            cache.put("str", "A");
            assertThat(cache.get("str")).contains("A");
            cache.merge("str", "B", (val1, val2) -> val1 + val2);
            assertThat(cache.get("str")).contains("AB");
        });
    }

    @Test
    void testMultiMemoryNodeCluster() {
        //this test ensure our test XML configs work for whatever other
        // tests needing them
    }

    @Test
    void testDefaultClusterNodeSetup() {
        var crawlWorkDir = tempDir.resolve("blah");
        var infinispanDir =
                crawlWorkDir.resolve("cache/infinispan").normalize();

        // check that the cluster is properly created with the default
        // configuration found under src/main/resources.
        try (var cluster =
                new InfinispanCluster(new InfinispanClusterConfig())) {
            cluster.init(crawlWorkDir);
            //            cluster.init(CrawlContextStubber.crawlerContext(tempDir));
            var props = cluster.getCacheManager().vendor()
                    .getGlobalConfigurationAsProperties();

            // Check global persistent location
            assertThat(props).containsEntry("globalState.enabled", "true");
            assertThat(props).containsEntry("globalState.persistentLocation",
                    infinispanDir.toString());

            // Check all defined cache names
            var cacheManager = cluster.getCacheManager().vendor();
            assertThat(cacheManager.getCacheNames())
                    .contains("default", "counter-cache");
        }
    }
}
