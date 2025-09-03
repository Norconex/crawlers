/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.mocks.cluster;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterNode;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;

import lombok.Data;

@Data
public class MockFailingCluster implements Cluster {

    private final String nodeId = UUID.randomUUID().toString();

    @Override
    public ClusterNode getLocalNode() {
        return new ClusterNode() {
            @Override
            public boolean isCoordinator() {
                return true;
            }

            @Override
            public String getNodeName() {
                return nodeId;
            }
        };
    }

    @Override
    public CacheManager getCacheManager() {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public void init(Path crawlerWorkDir) {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public void close() {
        //NOOP
    }

    @Override
    public PipelineManager getPipelineManager() {
        throw new UnsupportedOperationException("IN_TEST");
    }

    @Override
    public int getNodeCount() {
        return 0;
    }

    @Override
    public List<String> getNodeNames() {
        throw new UnsupportedOperationException("IN_TEST");
    }
}
