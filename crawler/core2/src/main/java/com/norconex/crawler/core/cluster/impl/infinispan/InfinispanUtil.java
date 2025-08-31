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

import java.io.IOException;

import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.lifecycle.ComponentStatus;

import com.google.common.base.Objects;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;

public final class InfinispanUtil {
    private InfinispanUtil() {
    }

    /**
     * Whether a pipeline should be considered "terminated", either by
     * completing all steps (success), or having a non-COMPLETED terminal
     * status on any of the steps (aborting the pipeline).
     * A {@code null} step record suggests no steps have yet run so
     * we consider it non-terminated.
     * @param pipeline the pipeline
     * @param stepRecord the record of the last step ran/attempted.
     * @return true if terminated
     */
    public static boolean isPipelineTerminated(
            Pipeline pipeline, StepRecord stepRecord) {
        if (stepRecord == null) {
            return false;
        }
        return stepRecord.getStatus() != null
                && stepRecord.getStatus().isTerminal()
                && ((stepRecord.getStatus() != PipelineStatus.COMPLETED)
                        || Objects.equal(stepRecord.getStepId(),
                                pipeline.getLastStep().getId()));
    }

    public static boolean isClusterRunning(InfinispanCluster cluster) {
        return cluster.getCacheManager().vendor()
                .getStatus() == ComponentStatus.RUNNING;
    }

    public static ConfigurationBuilderHolder configBuilderHolder(
            String path) {
        try (var is = InfinispanUtil.class.getResourceAsStream(path)) {
            return new ParserRegistry(
                    Thread.currentThread().getContextClassLoader()).parse(
                            is, MediaType.APPLICATION_XML);
        } catch (IOException e) {
            throw new CacheException(
                    "Could not load Infinispan configuration from classpath "
                            + "location '%s'".formatted(path),
                    e);
        }
    }

    public static ConfigurationBuilderHolder defaultConfigBuilderHolder() {
        return configBuilderHolder("/cache/infinispan.xml");
    }
}
