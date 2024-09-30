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
import java.util.Arrays;
import java.util.List;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import com.norconex.crawler.core.CrawlerConfig;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Ignite client instance selected regardless of crawler configuration
 * when the {@value IgniteClientTestInstance#PROP_IGNITE_TEST} system property
 * is set to <code>true</code>.
 * </p>
 * <p>
 * In addition to a client node, it creates one or more embedded Ignite server
 * nodes using ports in the range 47500..47509. The exact quantity of server
 * nodes is specified by the
 * {@value IgniteClientTestInstance#PROP_IGNITE_TEST_SERVER_QTY} system
 * property. Default is 1 and maximum is 10.
 * </p>
 *
 */
@EqualsAndHashCode
@ToString
class IgniteClientTestInstance implements IgniteClientInstance {

    static final String PROP_IGNITE_TEST = "grid.ignite.test";
    static final String PROP_IGNITE_TEST_SERVER_QTY =
            "grid.ignite.test.servers-qty";

    private final Ignite igniteClient;
    private final List<Ignite> igniteServers = new ArrayList<>();

    public static boolean isIgniteTestClientEnabled() {
        return Boolean.getBoolean(IgniteClientTestInstance.PROP_IGNITE_TEST);
    }

    public IgniteClientTestInstance(CrawlerConfig cfg) {

        //        var commonClassLoader =
        //                Thread.currentThread().getContextClassLoader();

        int serverNodeQty =
                Integer.getInteger(PROP_IGNITE_TEST_SERVER_QTY, 1);
        for (var i = 0; i < serverNodeQty; i++) {
            // Configuration for Server Node 1
            var serverCfg = new IgniteConfiguration();
            serverCfg.setIgniteInstanceName("serverNode" + (i + 1));
            //            serverCfg.setClassLoader(commonClassLoader);
            configureDiscovery(serverCfg); //, freePort(), freePort());
            igniteServers.add(Ignition.start(serverCfg));
        }

        //        // Configuration for Server Node 1
        //        var serverCfg1 = new IgniteConfiguration();
        //        serverCfg1.setIgniteInstanceName("serverNode1");
        //        configureDiscovery(serverCfg1, freePort(), freePort()); // Set unique ports for node 1
        //        //        configureDiscovery(serverCfg1, 47500, 47100); // Set unique ports for node 1
        //
        //        // Configuration for Server Node 2
        //        var serverCfg2 = new IgniteConfiguration();
        //        serverCfg2.setIgniteInstanceName("serverNode2");
        //        configureDiscovery(serverCfg2, freePort(), freePort()); // Set unique ports for node 2
        //        //        configureDiscovery(serverCfg2, 47501, 47101); // Set unique ports for node 2
        //
        //        Ignition.start(serverCfg1);
        //        Ignition.start(serverCfg2);

        // Configuration for Client Node
        var clientCfg = new IgniteConfiguration();
        clientCfg.setIgniteInstanceName("clientNode");
        clientCfg.setClientMode(true); // Mark this node as client
        //        clientCfg.setClassLoader(commonClassLoader);
        configureDiscovery(clientCfg);//, freePort(), freePort()); // Set unique ports for the client
        //        configureDiscovery(clientCfg, 47502, 47102); // Set unique ports for the client
        igniteClient = Ignition.start(clientCfg);

        for (ClusterNode node : igniteClient.cluster().nodes()) {
            System.out.println("Node in cluster: " + node.id());
        }
    }

    @Override
    public Ignite get() {
        return igniteClient;
    }

    @Override
    public void close() {
        igniteClient.close();
        igniteServers.forEach(Ignite::close);
    }

    // Helper method to configure discovery with specified ports
    private static void configureDiscovery(IgniteConfiguration cfg) {
        //            int discoveryPort, int communicationPort) {

        cfg.setPeerClassLoadingEnabled(false);

        var discoverySpi = new TcpDiscoverySpi();
        discoverySpi.setLocalPort(47500); // Starting port for discovery
        discoverySpi.setLocalPortRange(10); // Port range for discovery (47500 to 47509)

        // Set up an IP Finder for discovery
        var ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Arrays.asList("127.0.0.1:47500..47509")); // Specify a range of ports
        discoverySpi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(discoverySpi);

        //        var discoverySpi = new TcpDiscoverySpi();
        //        var ipFinder = new TcpDiscoveryVmIpFinder();
        //        ipFinder.setAddresses(
        //                Collections.singletonList("127.0.0.1:" + discoveryPort));
        //        discoverySpi.setIpFinder(ipFinder);
        //        cfg.setDiscoverySpi(discoverySpi);
        //
        //        // Set communication ports (optional if not using peer-to-peer tasks)
        //        cfg.setLocalHost("127.0.0.1");
        //        cfg.setCommunicationSpi(
        //                new TcpCommunicationSpi().setLocalPort(communicationPort));
    }
}
