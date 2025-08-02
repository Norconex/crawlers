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

import java.util.Objects;
import java.util.UUID;

import com.hazelcast.cluster.Member;
import com.norconex.crawler.core2.cluster.ClusterNode;

/**
 * Hazelcast implementation of the ClusterNode interface.
 */
public class HazelcastClusterNode implements ClusterNode {

    private final Member member;

    public HazelcastClusterNode(Member member) {
        this.member = Objects.requireNonNull(member, "Member cannot be null");
    }

    @Override
    public String getId() {
        return member.getUuid().toString();
    }

    @Override
    public boolean isLocal() {
        return member.localMember();
    }

    @Override
    public int hashCode() {
        return Objects.hash(member.getUuid());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        HazelcastClusterNode other = (HazelcastClusterNode) obj;
        return Objects.equals(member.getUuid(), other.member.getUuid());
    }

    @Override
    public String toString() {
        return "HazelcastClusterNode [id=" + getId() 
            + ", address=" + member.getAddress() 
            + ", local=" + isLocal() + "]";
    }
}