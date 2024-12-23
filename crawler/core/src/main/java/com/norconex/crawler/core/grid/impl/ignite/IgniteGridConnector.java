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

import java.util.ArrayList;
import java.util.function.Consumer;

import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.util.ConfigUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class IgniteGridConnector
        implements GridConnector,
        Configurable<IgniteGridConnectorConfig> {

    @Getter
    private final IgniteGridConnectorConfig configuration =
            new IgniteGridConnectorConfig();

    //TODO remove this when we have a better way to configure
    private Consumer<IgniteConfiguration> tuner;

    @Override
    public Grid connect(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {

        //NOTE:
        // - grid name is node name and must be unique for each node
        // - cluster name is what is unique to the entire cluster
        // - instance name are local only, in case multiple nodes on a JVM
        //   otherwise the default "" is fine.
        // - the consistent id is unique for each node on the cluster

        //TODO apply config settings from crawler config
        var igniteCfg = new IgniteConfiguration();
        igniteCfg.setGridLogger(new Slf4jLogger());

        configureWorkDirectory(crawlerConfig, igniteCfg);
        configurePersistentStorage(igniteCfg);
        configureDiscovery(igniteCfg, 0, 1);

        if (tuner != null) {
            tuner.accept(igniteCfg);
        }

        var ignite = Ignition.getOrStart(igniteCfg);

        // Perform initial activation if needed
        if (ignite.cluster().state() != ClusterState.ACTIVE) {
            ignite.cluster().state(ClusterState.ACTIVE); // Initial manual activation
            var baselineNodes = ignite.cluster().nodes();
            ignite.cluster().setBaselineTopology(baselineNodes);
        }

        ignite.cluster().baselineAutoAdjustEnabled(true);
        ignite.cluster().baselineAutoAdjustTimeout(5000);

        while (!ignite.cluster().state().active()) {
            LOG.info("Waiting for cluster to be active...");
            Sleeper.sleepMillis(2000);
        }
        LOG.info("Cluster is now active.");

        return new IgniteGrid(ignite);
    }

    /**
     * Ability to tune the Ignite configuration directly before connecting.
     * <b>TEMPORARY</b> until we have a better way to configure.
     * @param consumer ignite configuration consumer
     */
    public void tune(Consumer<IgniteConfiguration> consumer) {
        tuner = consumer;
    }

    private static void configureWorkDirectory(
            CrawlerConfig crawlerCfg, IgniteConfiguration igniteCfg) {
        var instanceName = igniteCfg.getIgniteInstanceName();
        igniteCfg.setWorkDirectory(
                ConfigUtil.resolveWorkDir(crawlerCfg)
                        .toAbsolutePath().toString());
        LOG.info("Ignite %s work directory: %s"
                .formatted(instanceName, igniteCfg.getWorkDirectory()));
    }

    //TODO have this using crawler/ignite config
    private static void configurePersistentStorage(IgniteConfiguration cfg) {
        cfg.setDataStorageConfiguration(
                new DataStorageConfiguration()
                        .setDefaultDataRegionConfiguration(
                                new DataRegionConfiguration()
                                        .setPersistenceEnabled(true)));
    }

    //TODO invoke only if discovery not explicitly configured?
    // or maybe if we detect being local only?
    private static void configureDiscovery(
            IgniteConfiguration cfg, int nodeIndex, int totalNodeCount) {

        cfg.setGridLogger(new Slf4jLogger());
        cfg.setPeerClassLoadingEnabled(false);
        cfg.setConsistentId("node-" + nodeIndex);
        cfg.setLocalHost("127.0.0.1");

        var discoverySpi = new TcpDiscoverySpi();

        // Hard-wire the specific port for this node (no network discovery)
        discoverySpi.setLocalPort(47500 + nodeIndex);
        discoverySpi.setLocalPortRange(0);

        // Set up an IP Finder with static addresses for all nodes
        var ipFinder = new TcpDiscoveryVmIpFinder();
        var addresses = new ArrayList<String>(totalNodeCount);
        for (var i = 0; i < totalNodeCount; i++) {
            addresses.add("127.0.0.1:" + (47500 + i));
        }
        ipFinder.setAddresses(addresses);
        discoverySpi.setIpFinder(ipFinder);

        // since embedded, shorten time allocated for discovery
        discoverySpi.setJoinTimeout(5000);
        discoverySpi.setNetworkTimeout(5000);

        var communicationSpi = new TcpCommunicationSpi();
        // no need for unlimited in a test environment
        communicationSpi.setMessageQueueLimit(1024);
        cfg.setCommunicationSpi(communicationSpi);

        cfg.setDiscoverySpi(discoverySpi);
    }

}
