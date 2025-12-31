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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;

@Timeout(60)
//@SlowTest
class HazelcastClusterStartupTest {

    @TempDir
    Path tempDir;

    @Test
    void testStandaloneClusterStartup() {
        var config = new HazelcastClusterConnectorConfig()
                //                .setPreset(Preset.STANDALONE)
                .setClusterName("standalone-test");
        var cluster = new HazelcastCluster(config);
        cluster.init(tempDir, false);
        assertThat(cluster.isStandalone()).isTrue();
        assertThat(cluster.getNodeCount()).isEqualTo(1);
        assertThat(cluster.getNodeNames()).hasSize(1);
        cluster.close();
    }

    @Test
    void testClusterModeStartup() throws Exception {
        var numNodes = 2;
        var config = new HazelcastClusterConnectorConfig()
                //TODO              .setPreset(Preset.CLUSTER)
                .setClusterName("cluster-test");
        var clusters = new HazelcastCluster[numNodes];
        var dirs = new Path[numNodes];
        for (var i = 0; i < numNodes; i++) {
            dirs[i] = tempDir.resolve("node" + i);
            java.nio.file.Files.createDirectories(dirs[i]);
            clusters[i] = new HazelcastCluster(config);
            clusters[i].init(dirs[i], true);
        }
        // Wait for cluster formation
        Sleeper.sleepSeconds(2);
        for (HazelcastCluster c : clusters) {
            assertThat(c.isStandalone()).isFalse();
            assertThat(c.getNodeCount()).isGreaterThanOrEqualTo(2);
            assertThat(c.getNodeNames()).hasSizeGreaterThanOrEqualTo(2);
        }
        for (HazelcastCluster c : clusters) {
            c.close();
        }
    }
}
