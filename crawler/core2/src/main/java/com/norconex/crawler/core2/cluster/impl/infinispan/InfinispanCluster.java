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

import java.nio.file.Path;

import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.manager.DefaultCacheManager;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core2.cluster.Cluster;
import com.norconex.crawler.core2.util.ExceptionSwallower;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class InfinispanCluster
        implements Cluster, Configurable<InfinispanClusterConfig> {

    private static final String BASEDIR_PLACEHOLDER = "__DERIVED__";

    private final InfinispanClusterConfig configuration =
            new InfinispanClusterConfig();
    private InfinispanClusterNode localNode;
    private InfinispanCacheManager cacheManager;
    private InfinispanTaskManager taskManager;

    @Override
    public void init(Path workDir) {
        var builderHolder = configuration.getInfinispan();
        var globalBuilder = builderHolder.getGlobalConfigurationBuilder();

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
        var manager = new DefaultCacheManager(builderHolder, true);

        cacheManager = new InfinispanCacheManager(manager);
        localNode = new InfinispanClusterNode(manager);
        taskManager = new InfinispanTaskManager(this);
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub
    }

    @Override
    public void close() {
        ExceptionSwallower.close(cacheManager);
    }
}
