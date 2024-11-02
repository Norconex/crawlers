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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.UUID;

import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;

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

    @Override
    public Grid connect(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {

        //        var cfg = crawlerContext.getConfiguration();

        //TODO set context as service

        //TODO apply config settings from crawler config
        var igniteCfg = new IgniteConfiguration();
        igniteCfg.setGridLogger(new Slf4jLogger());
        igniteCfg.setIgniteInstanceName(
                crawlerConfig.getId() + "__" + UUID.randomUUID().toString());

        //        igniteCfg.setClusterStateOnStart(ClusterState.ACTIVE);
        //        igniteCfg.setAutoActivationEnabled(false)

        configureWorkDirectory(crawlerConfig, igniteCfg);
        configurePersistentStorage(igniteCfg);
        configureDiscovery(igniteCfg, 0, 1);

        var ignite = Ignition.start(igniteCfg);

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

        //        // Get the current cluster nodes
        //        var currentNodes = ignite.cluster().nodes();
        //        // Set the baseline topology to the current nodes
        //        ignite.cluster().setBaselineTopology(currentNodes);

        //        ignite.services().deployClusterSingleton(

        var jsonWriter = new StringWriter();
        ClassUtil.newInstance(crawlerSpecProviderClass).get()
                .beanMapper()
                .write(crawlerConfig, jsonWriter, Format.JSON);
        var jsonConfig = jsonWriter.toString();

        // Each node has the context and config in a service, so no
        // need to recreate with each tasks
        ignite.services().deployNodeSingletonAsync(
                IgniteGridKeys.CONTEXT_SERVICE,
                new IgniteGridInitServiceImpl(
                        crawlerSpecProviderClass, jsonConfig))
                .get();

        //        Grid grid = new IgniteGrid(ignite);
        //        grid.compute().runOnce("clear-runonce-cache", () -> {
        //            Ignition.localIgnite()
        //                    .getOrCreateCache(IgniteGridKeys.RUN_ONCE_CACHE).clear();
        //        });
        //        return grid;
        return new IgniteGrid(ignite);
    }

    private static void configureWorkDirectory(
            CrawlerConfig crawlerCfg, IgniteConfiguration igniteCfg) {
        //        var baseWorkDir = crawlerCfg.getWorkDir();
        //        igniteCfg.setWorkDirectory(baseWorkDir.resolve(instanceName)
        //                .toAbsolutePath().toString());
        //
        //        LOG.info("Ignite %s work directory: %s"
        //                .formatted(instanceName, serverCfg.getWorkDirectory()));
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

//        igniteCfg.setPeerClassLoadingEnabled(true);
//        igniteCfg.setDeploymentMode(DeploymentMode.CONTINUOUS);
//        var igniteInstance = instance != null
//                ? instance
//                : new IgniteGridInstanceClient(cfg);
//
//        //TODO do a MockConnector that
//        //
//        //            igniteInstance =
//        //                    IgniteGridInstanceClientTest.isIgniteTestClientEnabled()
//        //                            ? new IgniteGridInstanceClientTest(cfg)
//        //                            : new IgniteGridInstanceClient(cfg);
//        //        }
//
//        // serialize crawler builder factory and config to create the
//        // crawler on each nodes
//        var crawlerCfgWriter = new StringWriter();
//        crawlerContext.getBeanMapper().write(
//                cfg, crawlerCfgWriter, Format.JSON);
//        var crawlerCfgStr = crawlerCfgWriter.toString();
//        var globalCache = igniteInstance.get()
//                .getOrCreateCache(IgniteGridKeys.GLOBAL_CACHE);
//        globalCache.put(IgniteGridKeys.CRAWLER_CONFIG, crawlerCfgStr);
//        globalCache.put(IgniteGridKeys.CRAWLER_SPEC_PROVIDER_CLASS,
//                crawlerContext.getSpecProviderClass().getName());
//        igniteInstance.get().getOrCreateCache(IgniteGridKeys.RUN_ONCE_CACHE)
//                .clear();
