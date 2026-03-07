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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.CacheNames;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterException;
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

    private static final Duration CACHE_READY_TIMEOUT =
            Duration.ofSeconds(30);
    private static final Duration CLUSTERED_CACHE_READY_TIMEOUT =
            Duration.ofSeconds(60);
    private static final Duration CACHE_READY_POLL_INTERVAL =
            Duration.ofMillis(250);

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
    private CrawlSession session;

    public String getCrawlerId() {
        return session != null ? session.getCrawlerId() : null;
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

    @Override
    public CrawlSession getCrawlSession() {
        return session;
    }

    @Override
    public void bindSession(CrawlSession session) {
        this.session = session;
    }

    @Override
    public void init(Path workDir, boolean clustered,
            Map<String, Class<?>> cacheTypes) {
        this.workDir = workDir;
        this.clustered = clustered;

        LOG.info("Initializing Hazelcast cluster node...");

        var ctx = new HazelcastConfigurerContext(
                workDir, clustered, configuration.getClusterName());
        var hzConfig = configuration.getConfigurer().buildConfig(ctx);

        hzConfig.setProperty("hazelcast.logging.type", "slf4j");

        try {

            validateClusterMode(hzConfig, clustered);

            // Bake concrete value types into map-store configs before starting
            // Hazelcast so EAGER loading always uses the right class.
            applyCacheTypes(hzConfig, cacheTypes);

            LOG.info("Creating HazelcastInstance with cluster name: {}",
                    hzConfig.getClusterName());
            hazelcastInstance = createHazelcastInstance(hzConfig);

            // Wire HazelcastInstance into any LazyTypedStoreFactory instances
            // that Hazelcast created while loading the map-store config.
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

            localNode = new HazelcastClusterNode(hazelcastInstance,
                    !clustered);
            // Add membership listener for coordinator changes only after the
            // local node wrapper is available, since membership events can be
            // emitted immediately during startup.
            membershipListenerId = hazelcastInstance.getCluster()
                    .addMembershipListener(new ClusterMembershipListener());

            cacheManager = new HazelcastCacheManager(hazelcastInstance);
            awaitCriticalCachesReady();

            // Seed the tracked coordinator state so that the first membership
            // event does not fire a spurious transition (Fix D).
            lastCoordinatorState = localNode.isCoordinator();
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

    /**
     * Applies concrete value types to map-store properties in the Hazelcast
     * config <em>before</em> {@code newHazelcastInstance()} is called, so
     * EAGER loading always deserializes values with the right class.
     */
    private void applyCacheTypes(
            Config hzConfig, Map<String, Class<?>> cacheTypes) {
        if (cacheTypes == null || cacheTypes.isEmpty()) {
            return;
        }
        for (var entry : cacheTypes.entrySet()) {
            var mapCfg = hzConfig.getMapConfigs().get(entry.getKey());
            if (mapCfg == null) {
                LOG.debug("No map config found for cache-type key '{}'; "
                        + "skipping.", entry.getKey());
                continue;
            }
            var storeCfg = mapCfg.getMapStoreConfig();
            if (storeCfg != null && storeCfg.isEnabled()) {
                storeCfg.getProperties().setProperty(
                        "value-class-name", entry.getValue().getName());
                LOG.debug("Set value-class-name={} for map config '{}'",
                        entry.getValue().getName(), entry.getKey());
            }
        }
    }

    protected HazelcastInstance createHazelcastInstance(Config hzConfig) {
        return Hazelcast.newHazelcastInstance(hzConfig);
    }

    private void awaitCriticalCachesReady() {
        var timeout = clustered
                ? CLUSTERED_CACHE_READY_TIMEOUT
                : CACHE_READY_TIMEOUT;
        var probeKey = "__hz-cache-ready__" + UUID.randomUUID();
        var startedAt = System.nanoTime();
        Throwable lastFailure = null;

        while (Duration.ofNanos(System.nanoTime() - startedAt)
                .compareTo(timeout) < 0) {
            try {
                var sessionCache = cacheManager.getCrawlSessionCache();
                var runCache = cacheManager.getCrawlRunCache();
                var referenceQueue = cacheManager.getCacheQueue(
                        CacheNames.REFERENCE_QUEUE, String.class);

                sessionCache.put(probeKey, "ready");
                runCache.put(probeKey, "ready");
                sessionCache.get(probeKey);
                runCache.get(probeKey);
                sessionCache.remove(probeKey);
                runCache.remove(probeKey);

                referenceQueue.size();

                LOG.debug("Critical Hazelcast caches/queues are ready.");
                return;
            } catch (Exception e) {
                lastFailure = e;
                Sleeper.sleepMillis(CACHE_READY_POLL_INTERVAL.toMillis());
            }
        }

        throw new ClusterException(
                "Timed out waiting for critical Hazelcast caches/queues to become ready.",
                lastFailure);
    }

    @Override
    public void startStopMonitoring() {
        if (isStandalone()) {
            LOG.debug(
                    "Standalone mode: skipping stop signal monitoring poller.");
            return;
        }
        if (stopController != null) {
            LOG.info("Starting stop signal monitoring for this node...");
            stopController.init();
        }
    }

    public void addCoordinatorChangeListener(
            CoordinatorChangeListener listener) {
        coordinatorListeners.add(listener);
        // Fire immediately with current state so the listener is fully
        // initialised on registration. Do NOT update lastCoordinatorState
        // here — that field is owned by the membership-event path.
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
            if (isStandalone()) {
                LOG.info("Standalone mode: stopping local pipeline directly.");
                pipelineManager.stop();
            } else {
                stopController.sendClusterStopSignal();
            }
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

    //TODO move this logic to the configurer
    private static void validateClusterMode(
            Config hzConfig, boolean isClustered) {
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
            // Allow state to settle, but never fail membership handling
            // because a shutdown/interruption occurred.
            try {
                Sleeper.sleepMillis(100);
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                } else {
                    throw e;
                }
            }
            checkCoordinatorStatus();
        }

        @Override
        public void memberRemoved(MembershipEvent membershipEvent) {
            LOG.info("Member removed from cluster: {}",
                    membershipEvent.getMember().getUuid());
            // Allow state to settle, but never fail membership handling
            // because a shutdown/interruption occurred.
            try {
                Sleeper.sleepMillis(100);
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                } else {
                    throw e;
                }
            }
            checkCoordinatorStatus();
        }

        private void checkCoordinatorStatus() {
            if (localNode == null) {
                LOG.debug(
                        "Skipping coordinator status check: localNode not initialized yet.");
                return;
            }
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
