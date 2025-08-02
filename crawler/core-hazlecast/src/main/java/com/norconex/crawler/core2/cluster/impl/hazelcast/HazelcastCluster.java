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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.Cluster;
import com.norconex.crawler.core2.cluster.ClusterNode;
import com.norconex.crawler.core2.cluster.TaskManager;

/**
 * Hazelcast implementation of the Cluster interface.
 */
public class HazelcastCluster implements Cluster {

    private static final Logger LOG =
            LoggerFactory.getLogger(HazelcastCluster.class);

    private final HazelcastInstance hazelcastInstance;
    private final HazelcastClusterConfig config;
    private final String nodeId;
    private final HazelcastCacheManager cacheManager;
    private final HazelcastTaskManager taskManager;

    public HazelcastCluster(HazelcastClusterConfig config) {
        Objects.requireNonNull(config,
                "Hazelcast cluster config cannot be null");
        this.config = config;
        nodeId = UUID.randomUUID().toString();

        LOG.info("Initializing Hazelcast cluster...");
        hazelcastInstance = HazelcastUtil.createHazelcastInstance(config);
        LOG.info("Hazelcast cluster initialized with node ID: {}", nodeId);

        cacheManager = new HazelcastCacheManager(hazelcastInstance);
        taskManager = new HazelcastTaskManager(hazelcastInstance);

        LOG.info("Hazelcast cluster ready. Cluster size: {}",
                getNodes().size());
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public Set<ClusterNode> getNodes() {
        return hazelcastInstance.getCluster().getMembers().stream()
                .map(this::memberToNode)
                .collect(Collectors.toSet());
    }

    private ClusterNode memberToNode(Member member) {
        return new HazelcastClusterNode(member);
    }

    @Override
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public TaskManager getTaskManager() {
        return taskManager;
    }

    //    @Override
    //    public void shutdown() {
    //    }

    @Override
    public ClusterNode getLocalNode() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void init(Path crawlerWorkDir) {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() {
        LOG.info("Shutting down Hazelcast cluster node: {}", nodeId);
        if (hazelcastInstance != null
                && hazelcastInstance.getLifecycleService().isRunning()) {
            hazelcastInstance.shutdown();
        }
    }
}