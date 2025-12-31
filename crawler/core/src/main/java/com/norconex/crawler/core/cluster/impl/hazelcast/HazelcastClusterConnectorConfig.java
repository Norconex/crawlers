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
import java.util.Properties;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HazelcastClusterConnectorConfig {

    public static final String DEFAULT_CONFIG_FILE =
            "classpath:cache/hazelcast-standalone.yaml";

    /**
     * Hazelcast configuration file path. Can be a local file-system
     * file or a classpath resource when prefixed with "classpath:".
     * Default is {@value HazelcastClusterConnectorConfig#DEFAULT_CONFIG_FILE}.
     */
    private String configFile = DEFAULT_CONFIG_FILE;

    /**
     * Any variables replacing matching ones specified in hazelcast
     * configuration file. Syntax is: <code>${variableName|defaultValue}</code>
     * where the default value is optional. The following variables are
     * automatically set so you do not need to add them:
     * <ul>
     *   <li>workDir</li>
     * </ul>
     *
     */
    private final Properties properties = new Properties();

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
     * TCP/IP members list for cluster discovery. Comma-separated list
     * of member addresses in format "host:port" or "host".
     * Only used in CLUSTER preset when no custom config file is specified.
     */
    private String tcpMembers;
}
