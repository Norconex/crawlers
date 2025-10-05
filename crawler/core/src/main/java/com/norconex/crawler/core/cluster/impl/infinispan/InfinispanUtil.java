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
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        if (stepRecord == null || stepRecord.getStatus() == null) {
            return false;
        }
        return stepRecord.getStatus().isTerminal()
                && ((stepRecord.getStatus() != PipelineStatus.COMPLETED)
                        || Objects.equal(stepRecord.getStepId(),
                                pipeline.getLastStep().getId()));
    }

    // either the first pipeline step or an existing one (joining mid-pipe).
    public static StepRecord currentPipelineStepRecordOrFirst(
            InfinispanCluster cluster, Pipeline pipeline) {
        var pipelineKey = CacheKeys.pipelineKey(cluster, pipeline);
        var stepRec = cluster.getCacheManager().getPipelineStepCache()
                .get(pipelineKey)
                .orElse(null);
        if (stepRec == null) {
            var stepId = pipeline.getFirstStep().getId();
            stepRec = new StepRecord();
            stepRec.setPipelineId(pipeline.getId());
            stepRec.setStepId(stepId);
            stepRec.setStatus(PipelineStatus.PENDING);
            stepRec.setRunId(cluster.getCrawlSession().getCrawlRunId());
            stepRec.setUpdatedAt(System.currentTimeMillis());
        } else {
            LOG.info("Resuming pipeline \"%s\" at step \"%s\" from status %s"
                    .formatted(
                            stepRec.getPipelineId(),
                            stepRec.getStepId(),
                            stepRec.getStatus()));
        }
        return stepRec;
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

    public static void waitForClusterWarmUp(InfinispanCluster cluster) {
        // Allow up to ~5 seconds for same-JVM multi-node tests to stabilize
        var deadline = System.currentTimeMillis() + 5_000;
        var lastNames = cluster.getNodeNames();
        var stableTicks = 0;
        while (System.currentTimeMillis() < deadline) {
            if (!InfinispanUtil.isClusterRunning(cluster)) {
                Sleeper.sleepMillis(50);
                continue;
            }
            var names = cluster.getNodeNames();
            if (names.equals(lastNames)) {
                stableTicks++;
            } else {
                stableTicks = 0;
            }
            lastNames = names;
            if (stableTicks >= 5) { // ~500ms of stability
                return;
            }
            Sleeper.sleepMillis(100);
        }
        LOG.debug("Cluster warm-up timed out; proceeding.");
    }
}
