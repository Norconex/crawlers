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
package com.norconex.crawler.core.grid.impl.ignite;

import static java.util.Optional.ofNullable;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.kubernetes.configuration.KubernetesConnectionConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.kubernetes.TcpDiscoveryKubernetesIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteConfigAdapter;
import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteGridActivator;
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

    public static final String CRAWL_NODE_INDEX =
            SystemUtil.getEnvironmentOrProperty("CRAWL_NODE_INDEX");
    public static final String CRAWL_SESSION_ID =
            SystemUtil.getEnvironmentOrProperty("CRAWL_SESSION_ID");

    private static final String IGNITE_BASE_DIR =
            "/ignite/data/node-%s".formatted(CRAWL_NODE_INDEX);

    @Getter
    private final IgniteGridConnectorConfig configuration =
            new IgniteGridConnectorConfig();

    @Override
    public Grid connect(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {

        if (StringUtils.isBlank(CRAWL_NODE_INDEX)) {
            throw new GridException(
                    "Missing environment variable (or system property): "
                            + "CRAWL_NODE_INDEX");
        }
        if (StringUtils.isBlank(CRAWL_SESSION_ID)) {
            throw new GridException(
                    "Missing environment variable (or system property): "
                            + "CRAWL_SESSION_ID");
        }

        var cfg = new IgniteConfiguration();

        // --- Generic configuration: ---
        cfg.setWorkDirectory(IGNITE_BASE_DIR + "/work");

        // --- Persistent storage: ---

        var storageCfg = new DataStorageConfiguration();
        storageCfg.setStoragePath(IGNITE_BASE_DIR + "/storage");
        storageCfg.setWalPath(IGNITE_BASE_DIR + "/wal");
        storageCfg.setWalArchivePath(IGNITE_BASE_DIR + "/wal/archive");

        LOG.info("Crawl session id: {}", CRAWL_SESSION_ID);
        LOG.info("""
        Ignite specifics:
            Node index:       %s
            Work path:        %s
            Storage path:     %s
            WAL path:         %s
            WAL archive path: %s
        """.formatted(
                CRAWL_NODE_INDEX,
                cfg.getWorkDirectory(),
                storageCfg.getStoragePath(),
                storageCfg.getWalPath(),
                storageCfg.getWalArchivePath()));

        var dataRegionCfg = new DataRegionConfiguration();
        dataRegionCfg.setName("Default_Region");
        dataRegionCfg.setPersistenceEnabled(true);
        storageCfg.setDefaultDataRegionConfiguration(dataRegionCfg);

        cfg.setDataStorageConfiguration(storageCfg);

        // --- Node discovery: ---

        // Create the Kubernetes IP finder and set the namespace and service name.
        var kubeCfg = new KubernetesConnectionConfiguration();
        kubeCfg.setNamespace("default");
        kubeCfg.setServiceName("ignite-crawler");
        var ipFinder = new TcpDiscoveryKubernetesIpFinder(kubeCfg);

        // Set up the discovery SPI
        var discoverySpi = new TcpDiscoverySpi();
        discoverySpi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(discoverySpi);

        // Start
        var ignite = Ignition.start(cfg);
        new DefaultIgniteGridActivator().accept(ignite); // ignite.active(true);

        return new IgniteGrid(ignite);
    }

    // @Override
    public Grid connectORIG(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {

        var igniteCfg =
                ofNullable(configuration.getIgniteConfigAdapter())
                        .orElseGet(DefaultIgniteConfigAdapter::new)
                        .apply(configuration.getIgniteConfig());

        if (StringUtils.isBlank(igniteCfg.getWorkDirectory())) {
            igniteCfg.setWorkDirectory(ConfigUtil.resolveWorkDir(crawlerConfig)
                    .resolve("ignite").toAbsolutePath().toString());
            LOG.info("Ignite instance \"%s\" work directory: %s"
                    .formatted(
                            StringUtils.defaultIfBlank(
                                    igniteCfg.getIgniteInstanceName(),
                                    "<default>"),
                            igniteCfg.getWorkDirectory()));
        }

        System.setProperty("IGNITE_NO_ASCII", "true");
        //        var ignite = Ignition.getOrStart(igniteCfg);
        //
        //        ofNullable(configuration.getIgniteGridActivator())
        //                .orElseGet(DefaultIgniteGridActivator::new).accept(ignite);

        // Discovery overwrite: -----------------------------------------
        var containerName = System.getenv("HOSTNAME"); // Gets the container name (e.g., ignite-node-1)
        var baseName = containerName.replaceFirst("^(.*)-.*$", "$1"); // Get the base name of the container (e.g., ignite)

        var ipFinder = new TcpDiscoveryVmIpFinder();

        // Dynamically build the list of addresses
        List<String> addresses = new ArrayList<>();
        for (var i = 1; i <= 3; i++) { // Assuming 3 nodes
            addresses.add(baseName + "-" + i + ":47500..47509");
        }

        ipFinder.setAddresses(addresses);

        var discoverySpi = new TcpDiscoverySpi();
        discoverySpi.setIpFinder(ipFinder);

        igniteCfg.setDiscoverySpi(discoverySpi);

        var ignite = Ignition.getOrStart(igniteCfg);

        ofNullable(configuration.getIgniteGridActivator())
                .orElseGet(DefaultIgniteGridActivator::new).accept(ignite);

        return new IgniteGrid(ignite);
    }
}

//TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
//ipFinder.setAddresses(Arrays.asList("ignite-node-1:47500..47509",
//                                   "ignite-node-2:47500..47509",
//                                   "ignite-node-3:47500..47509"));  // Add all your node names here
//
//TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
//discoverySpi.setIpFinder(ipFinder);
//
//IgniteConfiguration cfg = new IgniteConfiguration();
//cfg.setDiscoverySpi(discoverySpi);
