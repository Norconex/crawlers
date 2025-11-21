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

import java.time.Duration;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InfinispanClusterConfig {

    @RequiredArgsConstructor
    public enum Preset {
        CLUSTER("cache/infinispan-cluster.xml"),
        STANDALONE("cache/infinispan-standalone.xml"),
        STANDALONE_MEMORY("cache/infinispan-standalone-memory.xml"),
        CUSTOM(null);

        @Getter
        private final String configFile;
    }

    /**
     * Pre-defined configuration settings. The "CLUSTER" preset is used by
     * default and will also work if you have just one node. Extra nodes
     * can be added during an executing crawl. If you are not running
     * the crawler on a clustered environment, choosing the "STANDALONE"
     * preset is recommended for better single-node performance.
     * <p>
     * The "STANDALONE_MEMORY" preset is a lightweight, single-node
     * configuration that keeps caches local and avoids persisting
     * cluster-wide global state. It is useful for quick experiments
     * and short-lived crawls, but may consume more heap and is not
     * intended for durable, restartable crawls.
     * </p>
     * Choosing "CUSTOM" allows you to specify your own Infinispan
     * configuration and is for advanced use only.
     */
    @NonNull
    private Preset preset = Preset.CLUSTER;

    /**
     * An Infinispan configuration file. Can either be a local file-system
     * file, or a file found in this application classpath tried in that order).
     * Ignored unless preset is {@link Preset#CUSTOM}.
     * <p>
     * To have the crawler working directory used as the Infinispan storage
     * location, you can use the placeholder {@code __WORKDIR__} in your
     * configuration, where paths are defined.
     * </p>
     */
    private String configFile;

    //    @JsonDeserialize(using = InfinispanConfigDeserializer.class)
    //    @JsonSerialize(using = InfinispanConfigSerializer.class)
    //    private ConfigurationBuilderHolder infinispan =
    //            InfinispanUtil.defaultConfigBuilderHolder();

    //preset: cluster|standalone|custom

    /**
     * Maximum amount of time to wait before declaring a node as
     * "expired" when running a crawler task across multiple nodes.
     * The minimum value is 10 seconds. Defaults to 30 seconds.
     * Not applicable when running in standalone mode.
     */
    private Duration nodeExpiryTimeout = Duration.ofSeconds(30);
}
