/* Copyright 2025-2026 Norconex Inc.
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

import java.io.Closeable;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.norconex.crawler.core.cluster.ClusterNode;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HazelcastClusterNode implements ClusterNode, Closeable {

    private final HazelcastInstance hazelcastInstance;
    private final boolean standalone;

    @Override
    public String getNodeName() {
        if (hazelcastInstance == null ||
                !hazelcastInstance.getLifecycleService().isRunning()) {
            return null;
        }
        var member = hazelcastInstance.getCluster().getLocalMember();
        return member.getUuid().toString();
    }

    /**
     * @return true if this JVM is effectively standalone
     * (single-node mode).
     */
    public boolean isStandaloneNode() {
        return standalone;
    }

    /**
     * @return true if this node should run "one‑per‑cluster" tasks.
     *   - In standalone mode (no clustering at all): true
     *   - In clustered mode: true only for the oldest cluster member
     */
    @Override
    public boolean isCoordinator() {
        if (standalone) {
            return true;
        }

        LifecycleService lifecycle = hazelcastInstance.getLifecycleService();
        if (!lifecycle.isRunning()) {
            return false;
        }

        // In Hazelcast, the oldest member is considered the master/coordinator
        var cluster = hazelcastInstance.getCluster();
        var members = cluster.getMembers();
        if (members.isEmpty()) {
            return true;
        }

        Member oldestMember = members.iterator().next();
        Member localMember = cluster.getLocalMember();

        return oldestMember.getUuid().equals(localMember.getUuid());
    }

    @Override
    public void close() {
        if (hazelcastInstance != null
                && hazelcastInstance.getLifecycleService().isRunning()) {
            ExceptionSwallower.swallow(hazelcastInstance::shutdown);
        }
    }
}
