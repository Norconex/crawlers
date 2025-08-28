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
package com.norconex.crawler.core.cluster;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;

import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.ClusterNode;
import com.norconex.crawler.core2.cluster.TaskManager;

public interface Cluster extends Closeable {

    /**
     * Returns the number of nodes in the cluster.
     * @return node count
     */
    int getNodeCount();

    /**
     * Returns the names of all nodes in the cluster.
     * @return node names
     */
    List<String> getNodeNames();

    ClusterNode getLocalNode();

    CacheManager getCacheManager();

    TaskManager getTaskManager();

    PipelineManager getPipelineManager();

    void init(Path crawlerWorkDir);

    //TODO consider adding a "reason" argument that we would store.
    /**
     * Requests to stop the cluster. Does not wait.
     */
    void stop();

    @Override
    void close();
}
