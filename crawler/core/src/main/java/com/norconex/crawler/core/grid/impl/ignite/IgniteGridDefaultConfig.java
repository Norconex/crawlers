/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.isolated.IsolatedDiscoverySpi;

import com.norconex.crawler.core.CrawlerConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Single-node grid configuration with low memory usage for development
 * or testing. Default used when no configuration is supplied.
 * <b>Not for production use.</b>
 * </p>
 */
@Slf4j
final class IgniteGridDefaultConfig {

    private IgniteGridDefaultConfig() {
    }

    static IgniteConfiguration get(CrawlerConfig crawlerConfig) {

        //NOTE:
        // - grid name is node name and must be unique for each node
        // - cluster name is what is unique to the entire cluster
        // - instance name are local only, in case multiple nodes on a JVM
        //   otherwise the default "" is fine.
        // - the consistent id is unique for each node on the cluster

        var igniteCfg = new IgniteConfiguration();
        igniteCfg.setGridLogger(new Slf4jLogger());

        configurePersistentStorage(igniteCfg);
        configureDiscovery(igniteCfg, 0, 1);

        return igniteCfg;
    }

    private static void configurePersistentStorage(IgniteConfiguration cfg) {
        cfg.setDataStorageConfiguration(new DataStorageConfiguration()
                .setDefaultDataRegionConfiguration(
                        new DataRegionConfiguration()
                                .setPersistenceEnabled(true)
                                // 10 MB initial size
                                .setInitialSize(10 * 1024 * 1024L)
                                // 50 MB max size
                                .setMaxSize(50 * 1024 * 1024L)));
    }

    private static void configureDiscovery(
            IgniteConfiguration cfg, int nodeIndex, int totalNodeCount) {

        cfg.setGridLogger(new Slf4jLogger());
        cfg.setPeerClassLoadingEnabled(false);
        cfg.setConsistentId("node-" + nodeIndex);
        cfg.setLocalHost("127.0.0.1");
        var discoverySpi = new IsolatedDiscoverySpi();

        var communicationSpi = new TcpCommunicationSpi();
        // no need for unlimited in a test environment
        communicationSpi.setMessageQueueLimit(1024);
        cfg.setCommunicationSpi(communicationSpi);

        cfg.setDiscoverySpi(discoverySpi);
    }
}
