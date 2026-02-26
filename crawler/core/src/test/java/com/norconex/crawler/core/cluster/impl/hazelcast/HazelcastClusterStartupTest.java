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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link HazelcastCluster} standalone startup, node introspection,
 * and shutdown.
 */
@Timeout(60)
class HazelcastClusterStartupTest {

    @TempDir
    Path tempDir;

    @Test
    void testStandaloneClusterStartup() throws IOException {
        var workDir = tempDir.resolve("standalone-test");
        Files.createDirectories(workDir);

        var config = new HazelcastClusterConnectorConfig()
                .setClusterName("startup-test-standalone-"
                        + java.util.UUID.randomUUID()
                                .toString()
                                .substring(0,
                                        8));
        var cluster = HazelcastTestSupport.newCluster(config);

        cluster.init(workDir, false);

        assertThat(cluster.isStandalone()).isTrue();
        assertThat(cluster.getNodeCount()).isEqualTo(1);
        assertThat(cluster.getNodeNames()).hasSize(1);
        assertThat(cluster.getLocalNode()).isNotNull();
        assertThat(cluster.getCacheManager()).isNotNull();
        assertThat(cluster.getPipelineManager()).isNotNull();
        assertThat(cluster.getWorkDir()).isEqualTo(workDir);

        cluster.close();
    }

    @Test
    void testStandaloneCluster_coordinatorIsLocal() throws IOException {
        var workDir = tempDir.resolve("coord-test");
        Files.createDirectories(workDir);

        var config = new HazelcastClusterConnectorConfig()
                .setClusterName("startup-coord-test-"
                        + java.util.UUID.randomUUID()
                                .toString()
                                .substring(0,
                                        8));
        var cluster = HazelcastTestSupport.newCluster(config);
        cluster.init(workDir, false);

        // In a single-node cluster, the local node is always the coordinator
        assertThat(cluster.getLocalNode().isCoordinator()).isTrue();

        cluster.close();
    }

    @Test
    void testHazelcastUtil_isClusterRunning() throws IOException {
        var workDir = tempDir.resolve("running-test");
        Files.createDirectories(workDir);

        var config = new HazelcastClusterConnectorConfig()
                .setClusterName("running-test-"
                        + java.util.UUID.randomUUID()
                                .toString()
                                .substring(0,
                                        8));
        var cluster = HazelcastTestSupport.newCluster(config);
        cluster.init(workDir, false);

        assertThat(HazelcastUtil.isClusterRunning(cluster)).isTrue();

        cluster.close();
    }
}
