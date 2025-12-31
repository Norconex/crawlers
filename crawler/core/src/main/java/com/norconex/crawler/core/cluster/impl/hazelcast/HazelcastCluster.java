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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.StringUtils;

import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterNode;
import com.norconex.crawler.core.cluster.impl.hazelcast.event.CoordinatorChangeListener;
import com.norconex.crawler.core.cluster.impl.hazelcast.pipeline.HazelcastPipelineManager;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@EqualsAndHashCode(exclude = { "pipelineManager", "coordinatorListeners" })
@Slf4j
public class HazelcastCluster implements Cluster {

    private HazelcastClusterNode localNode;
    private HazelcastCacheManager cacheManager;
    private HazelcastPipelineManager pipelineManager;

    private final List<CoordinatorChangeListener> coordinatorListeners =
            new CopyOnWriteArrayList<>();
    private volatile boolean lastCoordinatorState = false;
    private CacheStopController stopController;
    @Getter
    private Path workDir;

    @Getter
    private final HazelcastClusterConnectorConfig configuration;
    private HazelcastInstance hazelcastInstance;
    private UUID membershipListenerId;
    private boolean clustered;

    public String getCrawlerId() {
        return getCrawlSession().getCrawlerId();
    }

    @Override
    public ClusterNode getLocalNode() {
        return localNode;
    }

    @Override
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public PipelineManager getPipelineManager() {
        return pipelineManager;
    }

    public CrawlSession getCrawlSession() {
        return CrawlSession.get(localNode);
    }

    @Override
    public void init(Path workDir, boolean clustered) {
        this.workDir = workDir;
        this.clustered = clustered;

        LOG.info("Initializing Hazelcast cluster node...");

        Map<String, Object> variables = new HashMap<>();
        variables.put("workDir", workDir.toAbsolutePath().toString());
        configuration.getProperties()
                .forEach((k, v) -> variables.put(Objects.toString(k, null), v));
        var hzConfig = HazelcastConfigLoader.load(
                configuration.getConfigFile(), variables);

        hzConfig.setProperty("hazelcast.logging.type", "slf4j");

        try {

            if (StringUtils.isNotBlank(configuration.getClusterName())) {
                hzConfig.setClusterName(configuration.getClusterName());
            }

            validateClusterMode(hzConfig, configuration, clustered);

            LOG.info("Creating HazelcastInstance with cluster name: {}",
                    hzConfig.getClusterName());
            hazelcastInstance = createHazelcastInstance(hzConfig);

            // Set HazelcastInstance on any LazyTypedStoreFactory instances
            // created by Hazelcast during config loading
            var factoryCfg = hazelcastInstance.getConfig();
            for (var mapConfig : factoryCfg.getMapConfigs().values()) {
                var storeConfig = mapConfig.getMapStoreConfig();
                if (storeConfig != null) {
                    var factory = storeConfig.getFactoryImplementation();
                    if (factory instanceof LazyTypedStoreFactory lazyFactory) {
                        lazyFactory.setHazelcastInstance(hazelcastInstance);
                    }
                }
            }

            // Diagnostic dump: log DataConnectionConfig and map configs so
            // we can verify which JDBC properties Hazelcast actually has
            // at runtime (helps debug mismatches between Testcontainers
            // jdbcUrl and Hikari's attempted connection).
            try {
                // Also write to a file in the workDir so we don't lose it if
                // test console output is truncated.
                var dumpFile = workDir.resolve("hazelcast-runtime-dump.log");
                var dumpLines = new java.util.ArrayList<String>();
                dumpLines.add("=== Hazelcast runtime dump ===");
                dumpLines.add("nodeWorkDir=" + workDir);
                var runtimeCfg = hazelcastInstance.getConfig();
                var dataConns = runtimeCfg.getDataConnectionConfigs();
                if (dataConns != null && !dataConns.isEmpty()) {
                    LOG.info("Hazelcast DataConnectionConfigs (count={}):",
                            dataConns.size());
                    dumpLines.add("DataConnectionConfigs count="
                            + dataConns.size());
                    dataConns.forEach((name, dcc) -> {
                        try {
                            var props = dcc.getProperties();
                            LOG.info(
                                    "  data-conn name='{}' type='{}' shared='{}'",
                                    name, dcc.getType(), dcc.isShared());
                            dumpLines.add("  data-conn name='" + name
                                    + "' type='" + dcc.getType()
                                    + "' shared='" + dcc.isShared() + "'");
                            if (props != null && !props.isEmpty()) {
                                for (String k : props.stringPropertyNames()) {
                                    LOG.info("    {}='{}'", k,
                                            props.getProperty(k));
                                    dumpLines.add("    " + k + "='"
                                            + props.getProperty(k) + "'");
                                }
                            } else {
                                LOG.info("    (no properties)");
                                dumpLines.add("    (no properties)");
                            }
                        } catch (Exception e) {
                            LOG.warn(
                                    "Could not log DataConnectionConfig '{}' : {}",
                                    name, e.getMessage());
                            dumpLines.add("  ERROR data-conn name='" + name
                                    + "': " + e);
                        }
                    });
                } else {
                    LOG.info(
                            "No DataConnectionConfigs present in Hazelcast config");
                    dumpLines.add("No DataConnectionConfigs present");
                }

                var mapCfgs = runtimeCfg.getMapConfigs();
                LOG.info("Hazelcast MapConfigs (count={})", mapCfgs.size());
                dumpLines.add("MapConfigs count=" + mapCfgs.size());
                mapCfgs.forEach((mname, mc) -> {
                    try {
                        var msc = mc.getMapStoreConfig();
                        if (msc == null) {
                            LOG.info("  map='{}' mapStore=null", mname);
                            dumpLines.add("  map='" + mname
                                    + "' mapStore=null");
                        } else {
                            LOG.info(
                                    "  map='{}' mapStore.enabled={} factory='{}'",
                                    mname, msc.isEnabled(),
                                    msc.getFactoryClassName());
                            dumpLines.add("  map='" + mname
                                    + "' mapStore.enabled=" + msc.isEnabled()
                                    + " factory='" + msc.getFactoryClassName()
                                    + "'");
                            var mprops = msc.getProperties();
                            if (mprops != null && !mprops.isEmpty()) {
                                for (String k : mprops.stringPropertyNames()) {
                                    LOG.info("    {}='{}'", k,
                                            mprops.getProperty(k));
                                    dumpLines.add("    " + k + "='"
                                            + mprops.getProperty(k) + "'");
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Could not log MapConfig '{}' : {}", mname,
                                e.getMessage());
                        dumpLines.add(
                                "  ERROR map='" + mname + "': " + e);
                    }
                });

                dumpLines.add("=== end ===");
                try {
                    Files.write(dumpFile, dumpLines,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                    LOG.info("Wrote Hazelcast runtime dump to: {}",
                            dumpFile.toAbsolutePath());
                } catch (Exception e) {
                    LOG.warn("Could not write Hazelcast runtime dump file: {}",
                            e.getMessage());
                }
            } catch (Exception e) {
                LOG.warn("Could not dump Hazelcast runtime config: {}",
                        e.getMessage());
            }

            // Set HazelcastInstance on any LazyTypedStoreFactory instances
            // created by Hazelcast during config loading
            var mapRuntimeCfg = hazelcastInstance.getConfig();
            for (var mapConfig : mapRuntimeCfg.getMapConfigs().values()) {
                var storeConfig = mapConfig.getMapStoreConfig();
                if (storeConfig != null) {
                    var factory = storeConfig.getFactoryImplementation();
                    if (factory instanceof LazyTypedStoreFactory lazyFactory) {
                        lazyFactory.setHazelcastInstance(hazelcastInstance);
                    }
                }
            }

            // Add membership listener for coordinator changes
            membershipListenerId = hazelcastInstance.getCluster()
                    .addMembershipListener(new ClusterMembershipListener());

            cacheManager = new HazelcastCacheManager(hazelcastInstance);

            localNode = new HazelcastClusterNode(hazelcastInstance,
                    !clustered);
            pipelineManager = new HazelcastPipelineManager(this);

            stopController = new CacheStopController(this);

            //            // Set instance name
            //       hazelcastConfig.setInstanceName(nodeName);
            //
            //

            LOG.info("HazelcastCluster initialized on node: {}",
                    localNode.getNodeName() != null ? localNode.getNodeName()
                            : "(inactive)");

        } catch (Exception e) {
            LOG.error("Failed to initialize Hazelcast for workDir='{}' ",
                    workDir, e);
            throw e;
        }
    }

    protected HazelcastInstance createHazelcastInstance(Config hzConfig) {
        return Hazelcast.newHazelcastInstance(hzConfig);
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
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
            var nodeName = localNode.getNodeName();
            return nodeName != null ? List.of(nodeName) : List.of();
        }
        return hazelcastInstance.getCluster().getMembers().stream()
                .map(Member::getUuid)
                .map(UUID::toString)
                .toList();
    }

    public boolean isStandalone() {
        return !isClustered();
    }

    public boolean isClustered() {
        return clustered;
    }

    //    /**
    //     * Persists all backup data to RocksDB before Hazelcast shutdown.
    //     * This ensures that backup replicas are not lost when all nodes restart.
    //     */
    //    private void persistAllBackupData() {
    //        try {
    //            LOG.info("Persisting all backup data before Hazelcast shutdown...");
    //            RocksDBMapStore.persistAllBackupData();
    //            LOG.info("Backup data persistence complete");
    //        } catch (Exception e) {
    //            LOG.error("Failed to persist backup data", e);
    //        }
    //    }
    //
    //    private static void applyPersistenceDir(Config hzConfig, Path dir) {
    //        var path = dir.normalize().toString();
    //
    //        hzConfig.getMapConfigs().values().forEach(mapCfg -> {
    //            var mapStoreCfg = mapCfg.getMapStoreConfig();
    //            if (mapStoreCfg == null || !mapStoreCfg.isEnabled()) {
    //                return;
    //            }
    //            var props = mapStoreCfg.getProperties();
    //            if (props == null) {
    //                props = new java.util.Properties();
    //                mapStoreCfg.setProperties(props);
    //            }
    //            props.setProperty("database.dir", path);
    //        });
    //
    //        hzConfig.getQueueConfigs().values().forEach(queueCfg -> {
    //            var queueStoreCfg = queueCfg.getQueueStoreConfig();
    //            if (queueStoreCfg == null || !queueStoreCfg.isEnabled()) {
    //                return;
    //            }
    //            var props = queueStoreCfg.getProperties();
    //            if (props == null) {
    //                props = new java.util.Properties();
    //                queueStoreCfg.setProperties(props);
    //            }
    //            props.setProperty("database.dir", path);
    //        });
    //    }

    //    private static String buildInstanceName(Path workDir) {
    //        if (workDir == null) {
    //            return "crawler-node";
    //        }
    //        var fileName = workDir.getFileName();
    //        var suffix = fileName != null ? fileName.toString() : "node";
    //        return "crawler-" + suffix + "-" + Math.abs(workDir.hashCode());
    //    }

    //TODO move this logic to
    private static void validateClusterMode(
            Config hzConfig, HazelcastClusterConnectorConfig hzClusterConfig,
            boolean isClustered) {
        var configAnalyzedClustered = false;
        var join = hzConfig.getNetworkConfig().getJoin();
        // Consider clustered if multicast or tcp-ip join is enabled
        if ((join.getMulticastConfig() != null
                && join.getMulticastConfig().isEnabled()) ||
                (join.getTcpIpConfig() != null
                        && join.getTcpIpConfig().isEnabled())) {
            configAnalyzedClustered = true;
        }
        // Also consider backup-count > 0 as a sign of clustering
        var backupCount = hzConfig.getMapConfig("default") != null
                ? hzConfig.getMapConfig("default").getBackupCount()
                : 0;
        if (backupCount > 0) {
            configAnalyzedClustered = true;
        }
        if (configAnalyzedClustered != isClustered) {
            var msg = String.format("""
                %s mode requested, but Hazelcast configuration
                appears to be configured as %s.""",
                    isClustered ? "CLUSTERED" : "STANDALONE",
                    !isClustered ? "CLUSTERED" : "STANDALONE");
            //            LOG.warn(msg);
            // Optionally, throw exception to fail fast
            LOG.warn(msg);
        }
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

        private void checkCoordinatorStatus() {
            var isCoordinator = localNode.isCoordinator();
            if (isCoordinator != lastCoordinatorState) {
                lastCoordinatorState = isCoordinator;
                for (CoordinatorChangeListener l : coordinatorListeners) {
                    l.onCoordinatorChange(isCoordinator);
                }
            }
        }
    }
}
