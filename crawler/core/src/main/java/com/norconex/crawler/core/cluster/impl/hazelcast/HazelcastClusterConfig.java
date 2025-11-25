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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.time.Duration;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HazelcastClusterConfig {

    @RequiredArgsConstructor
    public enum Preset {
        /**
         * Cluster mode using TCP/IP discovery for multi-node deployments.
         * Suitable for production environments where nodes need to discover
         * each other over the network.
         */
        CLUSTER(false),
        /**
         * Standalone mode for single-node deployments. Optimized for
         * single-node performance with local persistence.
         */
        STANDALONE(true),
        /**
         * In-memory only mode without persistence. Useful for quick
         * experiments and short-lived crawls. Data is lost on shutdown.
         */
        STANDALONE_MEMORY(true);

        @Getter
        private final boolean standalone;
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
     * data. It is useful for quick experiments and short-lived crawls,
     * but is not intended for durable, restartable crawls.
     * </p>
     */
    @NonNull
    private Preset preset = Preset.CLUSTER;

    /**
     * Custom Hazelcast configuration file path. Can be a local file-system
     * file or a classpath resource. If specified, overrides the preset
     * configuration.
     */
    private String configFile;

    /**
     * Cluster name for Hazelcast instance. All nodes with the same cluster
     * name will form a cluster. Defaults to "crawler-cluster".
     */
    private String clusterName = "crawler-cluster";

    /**
     * Maximum amount of time to wait before declaring a node as
     * "expired" when running a crawler task across multiple nodes.
     * <p>
     * The absolute minimum value is 5 seconds; any configured value
     * below this threshold will be clamped to 5 seconds at runtime.
     * Defaults to 30 seconds. Not applicable when running in
     * standalone mode.
     * </p>
     */
    private Duration nodeExpiryTimeout = Duration.ofSeconds(30);

    /**
     * Interval at which worker nodes send heartbeat updates for their
     * current pipeline status to the coordinator.
     * <p>
     * The default is 1 second. At runtime, the effective heartbeat
     * interval is validated against the configured node expiry
     * timeout to ensure it does not exceed one third of the expiry
     * interval. If it does, it will be reduced to that maximum
     * allowed value. The absolute minimum heartbeat interval is
     * 500 milliseconds.
     * </p>
     */
    private Duration workerHeartbeatInterval = Duration.ofSeconds(1);

    /**
     * Whether to enable persistence for caches. When enabled, cache
     * data is persisted to local storage. This allows
     * crawls to be resumed after a restart.
     */
    private boolean persistenceEnabled = true;

    /**
     * Number of backup copies for distributed data. In cluster mode,
     * this determines how many copies of each entry are stored across
     * the cluster for fault tolerance. Default is 1.
     */
    private int backupCount = 1;

    /**
     * TCP/IP members list for cluster discovery. Comma-separated list
     * of member addresses in format "host:port" or "host".
     * Only used in CLUSTER preset when no custom config file is specified.
     */
    private String tcpMembers;
}
