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
package com.norconex.crawler.core.grid.impl.ignite.cfg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.configuration.IgniteConfiguration;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnectorConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Supported Ignite grid configuration options applied to each node in a
 * cluster.
 * </p>
 * <p>
 * Only a relatively small subset of all Ignite configuration options are
 * offered by this class. For more advanced configuration, refer to the
 * Java API and  provide
 * your own {@link IgniteGridConnectorConfig#setIgniteConfigAdapter(
 * java.util.function.Function)}. Be advised this approach is for experts
 * and doing so may adversely affect the crawler behavior.
 * </p>
 * <p>
 * Where documentation is scarce, please refer to similar/same properties in
 * {@link IgniteConfiguration} or the official
 * <a href="https://ignite.apache.org/docs/latest/">Ignite documentation</a>
 * for more details on configuration options.
 * </p>
 * <p>
 * When the {@link IgniteGridConnector} is used without configuration, the
 * crawler will default to a single-node local cluster with minimal
 * configuration ideal for testing but not much else.
 * </p>
 */
@Data
@Accessors(chain = true)
public class LightIgniteConfig {

    private String igniteInstanceName;
    private String consistentId;
    private String localHost;
    private long metricsLogFrequency =
            IgniteConfiguration.DFLT_METRICS_LOG_FREQ;
    private long networkTimeout = IgniteConfiguration.DFLT_NETWORK_TIMEOUT;
    private long systemWorkerBlockedTimeout;

    /**
     * Mappings of socket addresses (host and port pairs) to each other
     * (e.g., for port forwarding) or mappings of a node internal address to
     * the corresponding external host (host only, port is assumed to be the
     * same). Corresponds to Ignite {@link BasicAddressResolver}.
     * @see BasicAddressResolver
     */
    private final Map<String, String> addressMappings = new HashMap<>();

    /**
     * Ignite work directory. Leave blank to use the default, which will
     * create an "ignite" sub-directory under
     * {@link CrawlerConfig#getWorkDir()}.
     */
    private String workDirectory;

    private final LightIgniteStorageConfig storage =
            new LightIgniteStorageConfig();

    private final LightIgniteDiscoveryConfig discovery =
            new LightIgniteDiscoveryConfig();

    private final LightIgniteCommunicationConfig communication =
            new LightIgniteCommunicationConfig();

    private final List<LightIgniteCacheConfig> caches = new ArrayList<>();

    public LightIgniteConfig setAddressMappings(
            Map<String, String> addressMappings) {
        CollectionUtil.setAll(this.addressMappings, addressMappings);
        return this;
    }
}
