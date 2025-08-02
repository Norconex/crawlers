/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class HazelcastClusterConfigTest {

    @Test
    void testDefaultValues() {
        HazelcastClusterConfig config = new HazelcastClusterConfig();
        assertEquals("nx-crawler-cluster", config.getClusterName());
        assertThat(config.getMemberAddresses()).isEmpty();
        assertThat(config.getConfigFile()).isNull();
    }

    @Test
    void testSetters() {
        HazelcastClusterConfig config = new HazelcastClusterConfig();
        
        config.setClusterName("test-cluster");
        config.setMemberAddresses(Arrays.asList("127.0.0.1:5701", "127.0.0.1:5702"));
        config.setConfigFile("/path/to/config.xml");
        
        assertEquals("test-cluster", config.getClusterName());
        assertThat(config.getMemberAddresses()).containsExactly("127.0.0.1:5701", "127.0.0.1:5702");
        assertEquals("/path/to/config.xml", config.getConfigFile());
    }
}