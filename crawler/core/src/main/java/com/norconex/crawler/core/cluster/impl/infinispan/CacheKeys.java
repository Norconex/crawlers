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

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.cluster.pipeline.Pipeline;

import lombok.NonNull;

public final class CacheKeys {
    private CacheKeys() {
    }

    public static String pipelineKey(
            @NonNull InfinispanCluster cluster, @NonNull Pipeline pipeline) {
        //NOTE: given the pipeline id can be human provided, we replace
        // : with _ to avoid splitting issues.
        return cluster.getCrawlSession().getCrawlSessionId()
                + ":" + StringUtils.replace(pipeline.getId(), ":", "_");
    }

    public static String pipelineWorkerNodeKey(
            @NonNull InfinispanCluster cluster, @NonNull Pipeline pipeline) {
        return pipelineWorkerKeyPrefix(cluster, pipeline)
                + cluster.getLocalNode().getNodeName();
    }

    /**
     * <p>
     * The worker key without the node information, constructed as:
     * </p>
     * {@code crawlSessionId:pipelineId:crawlRunId:}
     * <p>
     * Can be used to get all worker entries for a given session and run.
     * </p>
     * @param cluster the cluster
     * @param pipeline the pipeline
     * @return the key prefix
     */
    public static String pipelineWorkerKeyPrefix(
            @NonNull InfinispanCluster cluster, @NonNull Pipeline pipeline) {

        return pipelineKey(cluster, pipeline) + ":"
                + cluster.getCrawlSession().getCrawlRunId() + ":";
    }
}
