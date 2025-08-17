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
package com.norconex.crawler.core2.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanPipelineManager;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.util.ExceptionSwallower;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@EqualsAndHashCode
@Getter
@Slf4j
public class InfinispanCluster implements Cluster {

    private static final String BASEDIR_PLACEHOLDER = "__DERIVED__";

    private InfinispanClusterNode localNode;
    private InfinispanCacheManager cacheManager;
    private InfinispanTaskManager taskManager;
    private InfinispanPipelineManager pipelineManager;

    private final InfinispanClusterConfig configuration;

    public String getCrawlerId() {
        return CrawlSession.get(localNode).getCrawlerId();
    }

    @Override
    public synchronized void init(Path workDir) {
        var builderHolder = configuration.getInfinispan();
        var globalBuilder = builderHolder.getGlobalConfigurationBuilder();

        globalBuilder.transport().nodeName(UUID.randomUUID().toString());

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
            globalBuilder.globalState()
                    .persistentLocation(currentPath);
        }
        var defCacheManager = new DefaultCacheManager(builderHolder, true);
        defCacheManager.start();
        cacheManager = new InfinispanCacheManager(defCacheManager);
        localNode = new InfinispanClusterNode(defCacheManager);
        taskManager = new InfinispanTaskManager(this);
        pipelineManager = new InfinispanPipelineManager(this);
    }

    @Override
    public void stop() {
        LOG.info("Stopping InfinispanCluster (entire cluster) ...");
        // Stop task manager if present
        if (taskManager != null) {
            ExceptionSwallower.close(taskManager);
            taskManager = null;
        }
        // Stop local node if present
        if (localNode != null) {
            ExceptionSwallower.close(localNode);
            localNode = null;
        }
        // Stop cache manager (stops cluster)
        if (cacheManager != null) {
            ExceptionSwallower.close(cacheManager);
            cacheManager = null;
        }
        LOG.info("InfinispanCluster stopped.");
    }

    @Override
    public void close() {
        LOG.info("Disconnecting InfinispanCluster node ...");
        // Only disconnect this node, do not stop the cluster
        if (taskManager != null) {
            ExceptionSwallower.close(taskManager);
            taskManager = null;
        }
        if (localNode != null) {
            ExceptionSwallower.close(localNode);
            localNode = null;
        }
        // Do NOT close cacheManager here (leave cluster running)
        LOG.info("InfinispanCluster node disconnected.");
    }

    /**
     * Returns the addresses of all nodes in the cluster.
     * @return node addresses
     */
    public List<String> getAllNodeNames() {
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
}
