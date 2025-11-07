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
package com.norconex.crawler.core.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.transport.Address;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.impl.infinispan.event.CoordinatorChangeListener;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ExceptionSwallower;

import de.huxhorn.sulky.ulid.ULID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@EqualsAndHashCode
@Getter
@Slf4j
//TODO rename *Client?
public class InfinispanCluster implements Cluster {

    private static final String BASEDIR_PLACEHOLDER = "__DERIVED__";

    private InfinispanClusterNode localNode;
    private InfinispanCacheManager cacheManager;
    private InfinispanPipelineManager pipelineManager;

    private final List<CoordinatorChangeListener> coordinatorListeners =
            new CopyOnWriteArrayList<>();
    // so that if both previous coordinator and the new one are not this node
    // we do not trigger the event for no reason.
    private volatile boolean lastCoordinatorState = false;
    private StopController stopController;

    private final InfinispanClusterConfig configuration;

    @Listener
    @RequiredArgsConstructor
    public static class ClusterViewListener {
        @NonNull
        private final InfinispanCluster cluster;

        @ViewChanged
        public void viewChanged(ViewChangedEvent event) {
            LOG.info("Cluster membership changed: {} → {}",
                    event.getOldMembers().size(),
                    event.getNewMembers().size());
            cluster.checkCoordinatorStatus();
        }
    }

    public String getCrawlerId() {
        return getCrawlSession().getCrawlerId();
    }

    public CrawlSession getCrawlSession() {
        return CrawlSession.get(localNode);
    }

    //TODO why synchronized?
    @Override
    public synchronized void init(Path workDir) {
        var builderHolder = configuration.getInfinispan();
        var globalBuilder = builderHolder.getGlobalConfigurationBuilder();

        // Generate unique node name
        var uniqueNodeName = new ULID().nextULID();
        globalBuilder.transport().nodeName(uniqueNodeName);

        var proto = new ProtoStreamMarshaller();
        var serCtx = proto.getSerializationContext();
        CrawlerProtoSchema schema = new CrawlerProtoSchemaImpl();
        schema.registerSchema(serCtx);
        schema.registerMarshallers(serCtx);
        globalBuilder.serialization().marshaller(proto);

        var globalConfig = globalBuilder.build();
        var currentPath = globalConfig.globalState().persistentLocation();
        if (currentPath.contains(BASEDIR_PLACEHOLDER)) {
            currentPath = Path.of(currentPath.replace(
                    BASEDIR_PLACEHOLDER, workDir.toString()))
                    .normalize().toString();
            // Use the unique node name to ensure each node has its own storage directory
            currentPath = Path.of(currentPath, uniqueNodeName)
                    .normalize().toString();
            LOG.info("Infinispan persistent location: {}", currentPath);
            globalBuilder.globalState()
                    .persistentLocation(currentPath);
        }
        var defCacheManager = new DefaultCacheManager(builderHolder, true);
        defCacheManager.start();
        cacheManager = new InfinispanCacheManager(defCacheManager);
        localNode = new InfinispanClusterNode(defCacheManager);
        pipelineManager = new InfinispanPipelineManager(this);

        // Listen for coordinator change events
        defCacheManager.addListener(new ClusterViewListener(this));

        stopController =
                new StopController(cacheManager.getAdminCache(), ignored -> {
                    pipelineManager.stop();
                    //closing should be done by framework later
                });
        stopController.start();
    }

    /**
     * Fired initially when first registering a listener and,
     * subsequently, when <b>this node</b> gets promoted or demoted as
     * coordinator.
     * @param listener the listener to add
     */
    public void addCoordinatorChangeListener(
            CoordinatorChangeListener listener) {
        coordinatorListeners.add(listener);
        // Explicitly fire when registering - but JGroups should have
        // already elected a coordinator by this point since we wait for
        // cluster formation in execute()
        listener.onCoordinatorChange(localNode.isCoordinator());
    }

    public void removeCoordinatorChangeListener(
            CoordinatorChangeListener listener) {
        coordinatorListeners.remove(listener);
    }

    // Call this periodically or on cluster events
    private void checkCoordinatorStatus() {
        var isCoordinator = localNode.isCoordinator();
        if (isCoordinator != lastCoordinatorState) {
            lastCoordinatorState = isCoordinator;
            for (CoordinatorChangeListener l : coordinatorListeners) {
                l.onCoordinatorChange(isCoordinator);
            }
        }
    }

    // Store/send stop request
    @Override
    public void stop() {
        LOG.info("Stopping InfinispanCluster (entire cluster) ...");
        StopController.sendStop(getCacheManager().getAdminCache());
    }

    @Override
    public void close() {
        LOG.info("Disconnecting InfinispanCluster node ...");
        // Only disconnect this node, do not stop the cluster
        ExceptionSwallower.close(
                pipelineManager,
                localNode,
                cacheManager);
        stopController.stop();
        // Do NOT close cacheManager here (leave cluster running)
        LOG.info("InfinispanCluster node disconnected.");
    }

    /**
     * Returns the number of nodes in the cluster.
     * @return node count
     */
    @Override
    public int getNodeCount() {
        return ofNullable(cacheManager)
                .map(InfinispanCacheManager::vendor)
                .map(DefaultCacheManager::getClusterSize)
                .orElse(1);
    }

    /**
     * Returns the names of all nodes in the cluster.
     * @return node names
     */
    @Override
    public List<String> getNodeNames() {
        var members = ofNullable(cacheManager)
                .map(InfinispanCacheManager::vendor)
                .map(DefaultCacheManager::getMembers)
                .orElse(List.of());

        if (members.isEmpty()) {
            return List.of(localNode.getNodeName());
        }

        return members.stream()
                .map(Address::toString)
                .toList();
    }

    /**
     * Checks if the cluster is configured in standalone (non-clustered) mode
     * based on the Infinispan configuration. This checks whether transport
     * (JGroups) is configured, not the runtime cluster state.
     * <p>
     * Use this when you need to know the intended clustering mode from
     * configuration, not the current runtime state. For example, to avoid
     * waiting for cluster stabilization in standalone mode.
     * </p>
     * @return true if configured for standalone mode (no transport/JGroups)
     */
    public boolean isStandalone() {
        if (cacheManager == null) {
            return false;
        }
        var globalConfig = cacheManager.vendor()
                .getCacheManagerConfiguration()
                .transport();

        // If transport is null or not defined, it's standalone mode
        return globalConfig == null || globalConfig.transport() == null;
    }

}
