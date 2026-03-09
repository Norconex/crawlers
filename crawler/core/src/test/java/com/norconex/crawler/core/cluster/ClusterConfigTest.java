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
package com.norconex.crawler.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;

@WithTestWatcherLogging
@Timeout(30)
class ClusterConfigTest {

    @Test
    void defaultConnector_isHazelcast() {
        var cfg = new ClusterConfig();
        assertThat(cfg.getConnector())
                .isInstanceOf(HazelcastClusterConnector.class);
    }

    @Test
    void defaultAdminPort_isExpected() {
        var cfg = new ClusterConfig();
        assertThat(cfg.getAdminPort())
                .isEqualTo(ClusterConfig.DEFAULT_ADMIN_PORT);
    }

    @Test
    void defaultAdminDisabled_isFalse() {
        var cfg = new ClusterConfig();
        assertThat(cfg.isAdminDisabled()).isFalse();
    }

    @Test
    void defaultClustered_isFalse() {
        var cfg = new ClusterConfig();
        assertThat(cfg.isClustered()).isFalse();
    }

    @Test
    void fluentSetters_chainCorrectly() {
        var cfg = new ClusterConfig()
                .setAdminDisabled(true)
                .setAdminPort(9999)
                .setClustered(true);

        assertThat(cfg.isAdminDisabled()).isTrue();
        assertThat(cfg.getAdminPort()).isEqualTo(9999);
        assertThat(cfg.isClustered()).isTrue();
    }

    @Test
    void DEFAULT_ADMIN_PORT_value() {
        assertThat(ClusterConfig.DEFAULT_ADMIN_PORT).isEqualTo(27295);
    }
}
