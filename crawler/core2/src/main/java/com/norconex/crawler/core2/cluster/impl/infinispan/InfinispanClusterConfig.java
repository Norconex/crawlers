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

import java.time.Duration;

import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InfinispanClusterConfig {
    @JsonDeserialize(using = InfinispanConfigDeserializer.class)
    @JsonSerialize(using = InfinispanConfigSerializer.class)
    private ConfigurationBuilderHolder infinispan =
            InfinispanUtil.defaultConfigBuilderHolder();

    //
    //
    //TODO DELETE BELOW:
    private TaskRetentionConfig retention = new TaskRetentionConfig();

    @Data
    @Accessors(chain = true)
    public class TaskRetentionConfig {
        // null or negative = retain until session end
        private Duration onceTaskRetention = null; // keep until session end
        private Duration continuousFinalStatsRetention = Duration.ofMinutes(5);
        private int maxHistoricalExecutionsPerTask = 1; // keep latest only for runOnOne
        private boolean purgeOnSessionClose = true;
        // New tuning: stale timeout for continuous task heartbeats
        private Duration continuousStaleTimeout = Duration.ofSeconds(15);
    }
}
