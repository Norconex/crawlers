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
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class JdbcHazelcastConfigurerTest {

    @TempDir
    Path tempDir;

    @Test
    void buildConfig_clustered_defaultDiscovery_usesTcpMembers()
            throws IOException {
        var cfg = new JdbcHazelcastConfigurer();

        var hzConfig = cfg.buildConfig(new HazelcastConfigurerContext(
                tempDir.resolve("tcp-default"),
                true,
                "cfg-test-tcp"));

        var join = hzConfig.getNetworkConfig().getJoin();
        assertThat(join.getMulticastConfig().isEnabled()).isFalse();
        assertThat(join.getAutoDetectionConfig()).isNotNull();
        assertThat(join.getAutoDetectionConfig().isEnabled()).isFalse();
        assertThat(join.getTcpIpConfig().isEnabled()).isTrue();
        assertThat(join.getTcpIpConfig().getMembers())
                .containsExactly(
                        "127.0.0.1:5701",
                        "127.0.0.1:5702",
                        "127.0.0.1:5703");
    }

    @Test
    void buildConfig_clustered_autoDiscovery_enabled_disablesTcpMembers()
            throws IOException {
        var cfg = new JdbcHazelcastConfigurer()
                .setAutoDiscoveryEnabled(true)
                .setTcpMembers("127.0.0.1:5901,127.0.0.1:5902");

        var hzConfig = cfg.buildConfig(new HazelcastConfigurerContext(
                tempDir.resolve("auto-discovery"),
                true,
                "cfg-test-auto"));

        var join = hzConfig.getNetworkConfig().getJoin();
        assertThat(join.getMulticastConfig().isEnabled()).isFalse();
        assertThat(join.getAutoDetectionConfig()).isNotNull();
        assertThat(join.getAutoDetectionConfig().isEnabled()).isTrue();
        assertThat(join.getTcpIpConfig().isEnabled()).isFalse();
    }

    @Test
    void buildConfig_standalone_disablesAutoAndTcpDiscovery()
            throws IOException {
        var cfg = new JdbcHazelcastConfigurer()
                .setAutoDiscoveryEnabled(true)
                .setTcpMembers("127.0.0.1:5901,127.0.0.1:5902");

        var hzConfig = cfg.buildConfig(new HazelcastConfigurerContext(
                tempDir.resolve("standalone"),
                false,
                "cfg-test-standalone"));

        var join = hzConfig.getNetworkConfig().getJoin();
        assertThat(join.getMulticastConfig().isEnabled()).isFalse();
        assertThat(join.getAutoDetectionConfig()).isNotNull();
        assertThat(join.getAutoDetectionConfig().isEnabled()).isFalse();
        assertThat(join.getTcpIpConfig().isEnabled()).isFalse();
    }
}
