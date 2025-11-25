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

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CoordinatorChangeListener;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ExceptionSwallower;

import de.huxhorn.sulky.ulid.ULID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@EqualsAndHashCode
@Getter
@Slf4j
public class HazelcastCluster implements Cluster {

    private HazelcastClusterNode localNode;
    private HazelcastCacheManager cacheManager;
    private HazelcastPipelineManager pipelineManager;

    private final List<CoordinatorChangeListener> coordinatorListeners =
            new CopyOnWriteArrayList<>();
    private volatile boolean lastCoordinatorState = false;
    private CacheStopController stopController;
    private Path workDir;

    private final HazelcastClusterConfig configuration;
    private HazelcastInstance hazelcastInstance;
    private UUID membershipListenerId;

    public String getCrawlerId() {
        return getCrawlSession().getCrawlerId();
    }

    public CrawlSession getCrawlSession() {
        return CrawlSession.get(localNode);
    }

    @Override
    public void init(Path workDir) {
        this.workDir = workDir;
        LOG.info("HazelcastCluster.init(workDir={})", workDir);

        // Generate unique node name
        var uniqueNodeName = new ULID().nextULID();
        LOG.info("HazelcastCluster node ULID: {}", uniqueNodeName);

        // Build Hazelcast configuration
        var hazelcastConfig = buildConfig(workDir, uniqueNodeName);

        // Configure persistence directory if enabled
        if (configuration.isPersistenceEnabled()
                && configuration.getPreset()
                        != HazelcastClusterConfig.Preset.STANDALONE_MEMORY) {
            var persistenceDir =
                    workDir.resolve("cache/hazelcast/" + uniqueNodeName)
                            .normalize();
            LOG.info("Hazelcast persistence directory: {}", persistenceDir);
            // MVStore-based persistence is handled via MapStore in map configs
        }

        try {
            LOG.info("Creating HazelcastInstance with cluster name: {}",
                    hazelcastConfig.getClusterName());
            hazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig);

            // Add membership listener for coordinator changes
            membershipListenerId = hazelcastInstance.getCluster()
                    .addMembershipListener(new ClusterMembershipListener());

            boolean standalone =
                    configuration.getPreset().isStandalone();
            cacheManager = new HazelcastCacheManager(hazelcastInstance);
            localNode = new HazelcastClusterNode(hazelcastInstance, standalone);
            pipelineManager = new HazelcastPipelineManager(this);

            stopController = new CacheStopController(this);

            LOG.info("HazelcastCluster initialized on node: {}",
                    localNode.getNodeName());

        } catch (Exception e) {
            LOG.error("Failed to initialize Hazelcast for workDir='{}' ",
                    workDir, e);
            throw e;
        }
    }

    @Override
    public void startStopMonitoring() {
        if (stopController != null) {
            LOG.info("Starting stop signal monitoring for this node...");
            stopController.init();
        }
    }

    public void addCoordinatorChangeListener(
            CoordinatorChangeListener listener) {
        coordinatorListeners.add(listener);
        // Explicitly fire when registering
        listener.onCoordinatorChange(localNode.isCoordinator());
    }

    public void removeCoordinatorChangeListener(
            CoordinatorChangeListener listener) {
        coordinatorListeners.remove(listener);
    }

    private void checkCoordinatorStatus() {
        var isCoordinator = localNode.isCoordinator();
        if (isCoordinator != lastCoordinatorState) {
            lastCoordinatorState = isCoordinator;
            for (CoordinatorChangeListener l : coordinatorListeners) {
                l.onCoordinatorChange(isCoordinator);
            }
        }
    }

    @Override
    public void stop() {
        if (stopController != null) {
            var session = getCrawlSession();
            session.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN, session);
            stopController.sendClusterStopSignal();
            session.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_END, session);
            LOG.info("Stopping cluster...");
        }
    }

    @Override
    public void close() {
        LOG.info("Disconnecting HazelcastCluster node ...");

        try {
            if (stopController != null) {
                LOG.info("Closing stop controller...");
                stopController.close();
                LOG.info("Stop controller closed.");
            }

            LOG.info("Closing pipeline manager...");
            ExceptionSwallower.close(pipelineManager);
            LOG.info("Pipeline manager closed.");

            Thread.sleep(100);

        } catch (Exception e) {
            LOG.warn("Error during initial cleanup", e);
        }

        LOG.info("Closing local node...");
        ExceptionSwallower.close(localNode);
        LOG.info("Closing cache manager...");
        ExceptionSwallower.close(cacheManager);
        LOG.info("HazelcastCluster node disconnected.");
    }

    @Override
    public int getNodeCount() {
        if (hazelcastInstance == null
                || !hazelcastInstance.getLifecycleService().isRunning()) {
            return 1;
        }
        return hazelcastInstance.getCluster().getMembers().size();
    }

    @Override
    public List<String> getNodeNames() {
        if (hazelcastInstance == null
                || !hazelcastInstance.getLifecycleService().isRunning()) {
            return List.of(localNode.getNodeName());
        }

        return hazelcastInstance.getCluster().getMembers().stream()
                .map(Member::getUuid)
                .map(UUID::toString)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isStandalone() {
        return configuration.getPreset().isStandalone();
    }

    //--- Private methods ------------------------------------------------------

    private Config buildConfig(Path workDir, String nodeName) {
        Config config;

        // Check if a custom config file is specified
        if (StringUtils.isNotBlank(configuration.getConfigFile())) {
            try {
                config = new XmlConfigBuilder(configuration.getConfigFile())
                        .build();
                LOG.info("Using custom Hazelcast configuration file: {}",
                        configuration.getConfigFile());
            } catch (FileNotFoundException e) {
                throw new CacheException(
                        "Could not load Hazelcast configuration from '%s'"
                                .formatted(configuration.getConfigFile()),
                        e);
            }
        } else {
            config = new Config();

            // Configure based on preset
            switch (configuration.getPreset()) {
            case STANDALONE:
            case STANDALONE_MEMORY:
                // Disable network for standalone mode
                config.getNetworkConfig().getJoin().getMulticastConfig()
                        .setEnabled(false);
                config.getNetworkConfig().getJoin().getTcpIpConfig()
                        .setEnabled(false);
                config.getNetworkConfig().getJoin().getAutoDetectionConfig()
                        .setEnabled(false);
                break;
            case CLUSTER:
                // Enable TCP/IP discovery for cluster mode
                config.getNetworkConfig().getJoin().getMulticastConfig()
                        .setEnabled(false);
                config.getNetworkConfig().getJoin().getAutoDetectionConfig()
                        .setEnabled(false);
                var tcpConfig =
                        config.getNetworkConfig().getJoin().getTcpIpConfig();
                tcpConfig.setEnabled(true);
                if (StringUtils.isNotBlank(configuration.getTcpMembers())) {
                    for (var member : configuration.getTcpMembers()
                            .split(",")) {
                        tcpConfig.addMember(member.trim());
                    }
                } else {
                    // Default to localhost for single-node cluster testing
                    tcpConfig.addMember("127.0.0.1");
                }
                break;
            default:
                break;
            }
        }

        // Set cluster name
        config.setClusterName(configuration.getClusterName());

        // Set instance name
        config.setInstanceName(nodeName);

        // Configure default map settings
        var defaultMapConfig = new MapConfig("default");
        defaultMapConfig.setBackupCount(configuration.getBackupCount());
        defaultMapConfig.setAsyncBackupCount(0);
        config.addMapConfig(defaultMapConfig);

        // Configure indexed maps for ledger entries
        configureLedgerMaps(config);

        // Configure pipeline-related maps
        configurePipelineMaps(config);

        return config;
    }

    private void configureLedgerMaps(Config config) {
        // Create ledger map configurations with indexes
        for (var ledgerName : List.of("ledger_a", "ledger_b")) {
            var mapConfig = new MapConfig(ledgerName);
            mapConfig.setBackupCount(configuration.getBackupCount());

            // Add index on processingStatus for efficient queries
            mapConfig.addIndexConfig(new IndexConfig(
                    IndexType.HASH, "processingStatus"));

            // Add sorted index on queuedAt for ordering
            mapConfig.addIndexConfig(new IndexConfig(
                    IndexType.SORTED, "queuedAt"));

            config.addMapConfig(mapConfig);
        }

        // Counters map
        var countersConfig = new MapConfig(CacheNames.COUNTERS);
        countersConfig.setBackupCount(configuration.getBackupCount());
        config.addMapConfig(countersConfig);
    }

    private void configurePipelineMaps(Config config) {
        // Pipeline step cache - replicated for shutdown safety
        var stepConfig = new MapConfig(CacheNames.PIPE_CURRENT_STEP);
        stepConfig.setBackupCount(
                Math.max(1, configuration.getBackupCount()));
        config.addMapConfig(stepConfig);

        // Worker statuses cache - replicated for shutdown safety
        var workerConfig = new MapConfig(CacheNames.PIPE_WORKER_STATUSES);
        workerConfig.setBackupCount(
                Math.max(1, configuration.getBackupCount()));
        config.addMapConfig(workerConfig);

        // Admin cache
        var adminConfig = new MapConfig(CacheNames.ADMIN);
        adminConfig.setBackupCount(
                Math.max(1, configuration.getBackupCount()));
        config.addMapConfig(adminConfig);
    }

    //--- Inner classes --------------------------------------------------------

    private class ClusterMembershipListener implements MembershipListener {
        @Override
        public void memberAdded(MembershipEvent membershipEvent) {
            LOG.info("Member added to cluster: {}",
                    membershipEvent.getMember().getUuid());
            Sleeper.sleepMillis(100); // Allow state to settle
            checkCoordinatorStatus();
        }

        @Override
        public void memberRemoved(MembershipEvent membershipEvent) {
            LOG.info("Member removed from cluster: {}",
                    membershipEvent.getMember().getUuid());
            Sleeper.sleepMillis(100); // Allow state to settle
            checkCoordinatorStatus();
        }
    }
}
