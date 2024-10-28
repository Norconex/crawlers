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
package com.norconex.crawler.core.mocks.grid;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
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
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridAttributes;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridInstance;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Ignite client instance selected regardless of crawler configuration
 * when the {@value MockIgniteGridInstanceClient#PROP_IGNITE_TEST} system
 * property is set to <code>true</code>.
 * </p>
 * <p>
 * Use
 * {@value MockIgniteGridInstanceClient#PROP_IGNITE_TEST_SERVER_QTY} system
 * property to define the number of server nodes to use.
 * Default is 1.
 * </p>
 */
@EqualsAndHashCode
@ToString
@Slf4j
class MockIgniteGridInstanceClient implements IgniteGridInstance {

    //    static final String PROP_IGNITE_TEST = "grid.ignite.test";
    //    static final String PROP_IGNITE_TEST_SERVER_QTY =
    //            "grid.ignite.test.servers-qty";
    //
    private final Ignite igniteClient;
    private final List<Ignite> igniteServers = new ArrayList<>();

    //    public static boolean isIgniteTestClientEnabled() {
    //        return Boolean
    //                .getBoolean(MockIgniteGridInstanceClient.PROP_IGNITE_TEST);
    //    }

    MockIgniteGridInstanceClient(Path baseWorkDir, int serverNodes) {

        //        var baseWorkDir = cfg.getWorkDir() == null
        //                ? Path.of(SystemProperties.getJavaIoTmpdir())
        //                : cfg.getWorkDir();

        var serverNodeQty = Math.max(serverNodes, 1);
        //                Integer.getInteger(PROP_IGNITE_TEST_SERVER_QTY, 1);
        var totalNodeCount = serverNodeQty + 1;
        for (var i = 0; i < serverNodeQty; i++) {
            var nodeIndex = i + 1;
            var serverCfg = new IgniteConfiguration();
            var instanceName = "serverNode" + nodeIndex;
            serverCfg.setIgniteInstanceName(instanceName);
            serverCfg.setWorkDirectory(baseWorkDir.resolve(instanceName)
                    .toAbsolutePath().toString());

            LOG.info("Ignite %s work directory: %s"
                    .formatted(instanceName, serverCfg.getWorkDirectory()));

            //TODO delete this line?
            serverCfg.setUserAttributes(new IgniteGridAttributes()
                    .setActivationLeader(i == 0)
                    .setActivationExpectedServerCount(serverNodeQty));

            // persist
            var storageCfg = new DataStorageConfiguration();
            storageCfg.getDefaultDataRegionConfiguration()
                    .setPersistenceEnabled(true);
            serverCfg.setDataStorageConfiguration(storageCfg);

            configureDiscovery(serverCfg, nodeIndex, totalNodeCount);
            configurePersistentStorage(serverCfg);
            //            configureSql(serverCfg);

            var ignite = Ignition.start(serverCfg);
            igniteServers.add(ignite);
        }

        // Configuration for Client Node
        var clientCfg = new IgniteConfiguration();
        clientCfg.setIgniteInstanceName("clientNode");
        clientCfg.setWorkDirectory(
                baseWorkDir.resolve("clientNode").toAbsolutePath().toString());
        LOG.info("Ignite %s work directory: %s"
                .formatted("clientNode", clientCfg.getWorkDirectory()));
        clientCfg.setClientMode(true); // Mark this node as client
        configureDiscovery(clientCfg, 0, totalNodeCount);

        try {
            igniteClient = Ignition.start(clientCfg);
            if (checkAndActivateCluster(igniteClient, serverNodeQty,
                    serverNodeQty, 60_000)) {
                LOG.info("Cluster activated successfully.");
            } else {
                LOG.info("Failed to activate cluster.");
            }
        } catch (IgniteException e) {
            throw new GridException("Ignite failed to start.", e);
        }
    }

    public static boolean checkAndActivateCluster(
            Ignite ignite, int expectedServerCount, int quorum, long timeout) {

        var timer = StopWatch.createStarted();
        var nodes = ignite.cluster().forServers().nodes();
        var activeNodes = 0;
        while (activeNodes < expectedServerCount) {
            activeNodes = (int) nodes
                    .stream()
                    .filter(node -> !Boolean.TRUE.equals(
                            node.attribute("ignite.node.daemon")))
                    .count();

            if (activeNodes >= expectedServerCount) {
                return activateCluster(ignite);
            }

            if (timer.getTime() > timeout) {
                if (activeNodes >= quorum) {
                    LOG.warn("Reached quorum ({}) with {} of {} server "
                            + "nodes ready after timeout. Proceeding. ",
                            quorum,
                            activeNodes,
                            expectedServerCount);
                    return activateCluster(ignite);
                }
                LOG.warn("Timed out waiting for server nodes to reach "
                        + "quorum ({}). Only {} are ready. Aborting.",
                        quorum, activeNodes);
                return false;

            }

            Sleeper.sleepSeconds(1);
        }
        var result = activateCluster(ignite);
        timer.stop();
        LOG.info("Cluster initialized in {}", timer.getMessage());
        return result;
    }

    private static boolean activateCluster(Ignite ignite) {
        try {
            ignite.cluster().state(ClusterState.ACTIVE);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to activate cluster: ", e.getMessage());
            return false;
        }
    }

    @Override
    public Ignite get() {
        return igniteClient;
    }

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

    // Helper method to configure discovery with specified ports
    //    private static void configureDiscovery(IgniteConfiguration cfg) {
    //        cfg.setGridLogger(new Slf4jLogger());
    //        cfg.setPeerClassLoadingEnabled(false);
    //
    //        var discoverySpi = new TcpDiscoverySpi();
    //        discoverySpi.setLocalPort(47500); // Starting port for discovery
    //        discoverySpi.setLocalPortRange(10); // Port range for discovery (47500 to 47509)
    //
    //        // Set up an IP Finder for discovery
    //        var ipFinder = new TcpDiscoveryVmIpFinder();
    //        ipFinder.setAddresses(Arrays.asList("127.0.0.1:47500..47509")); // Specify a range of ports
    //        discoverySpi.setIpFinder(ipFinder);
    //
    //        cfg.setDiscoverySpi(discoverySpi);
    //    }

    //    private static void configureSql(IgniteConfiguration cfg) {
    //        cfg.setSqlConfiguration(new SqlConfiguration()
    //                .setQueryEnginesConfiguration(
    //                        new IndexingQueryEngineConfiguration()
    //                                .setDefault(true)));
    //    }

    private static void configurePersistentStorage(IgniteConfiguration cfg) {
        cfg.setDataStorageConfiguration(
                new DataStorageConfiguration()
                        .setDefaultDataRegionConfiguration(
                                new DataRegionConfiguration()
                                        .setPersistenceEnabled(true)));
    }
}
