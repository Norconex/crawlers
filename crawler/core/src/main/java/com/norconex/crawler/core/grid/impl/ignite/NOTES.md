Example: Running Two Server Nodes and a Client in the Same JVM

java

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.util.Collections;

public class IgniteIsolationExample {
    public static void main(String[] args) {
        // Configuration for Server Node 1
        IgniteConfiguration serverCfg1 = new IgniteConfiguration();
        serverCfg1.setIgniteInstanceName("serverNode1");
        configureDiscovery(serverCfg1, 47500, 47100);  // Set unique ports for node 1

        // Configuration for Server Node 2
        IgniteConfiguration serverCfg2 = new IgniteConfiguration();
        serverCfg2.setIgniteInstanceName("serverNode2");
        configureDiscovery(serverCfg2, 47501, 47101);  // Set unique ports for node 2

        // Start both server nodes
        Ignite serverNode1 = Ignition.start(serverCfg1);
        Ignite serverNode2 = Ignition.start(serverCfg2);

        // Configuration for Client Node
        IgniteConfiguration clientCfg = new IgniteConfiguration();
        clientCfg.setIgniteInstanceName("clientNode");
        clientCfg.setClientMode(true);  // Mark this node as client
        configureDiscovery(clientCfg, 47502, 47102);  // Set unique ports for the client

        // Start client node
        Ignite clientNode = Ignition.start(clientCfg);

        // Simulate interactions between client and server nodes
        clientNode.compute().broadcast(() -> {
            System.out.println("Task executed on server nodes");
        });

        // Stop all nodes at the end
        serverNode1.close();
        serverNode2.close();
        clientNode.close();
    }

    // Helper method to configure discovery with unique ports
    private static void configureDiscovery(IgniteConfiguration cfg, int discoveryPort, int communicationPort) {
        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + discoveryPort));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

        // Set communication ports (optional if not using peer-to-peer tasks)
        cfg.setLocalHost("127.0.0.1");
        cfg.setCommunicationSpi(new org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi().setLocalPort(communicationPort));
    }
}

Key Configuration Details:

    Unique Instance Names: Each node (server or client) is assigned a unique IgniteInstanceName. This ensures they are treated as separate entities.
    Discovery SPI with Unique Ports: Each node is configured with a TcpDiscoverySpi and unique discovery and communication ports to avoid port conflicts, ensuring that they are logically isolated from each other.
    Client Mode for Client Node: The client node is configured with clientMode(true) so that it doesn’t participate in storing data or computation but can interact with the server nodes.
    Separate Data Regions (optional): For further isolation, you can configure separate data regions and persistence stores for each server node. This ensures that each node manages its own data independently, even if they are part of the same JVM.

Benefits of This Approach:

    Classpath Sharing: All nodes share the same classpath, meaning that when you make changes to your code, the client and server nodes automatically pick up those changes without the need to redeploy or create fat JARs.
    Logical Isolation: Each node runs with a separate configuration, including unique discovery ports, instance names, and potentially different data regions, ensuring logical isolation while still being in the same JVM.
    Fast Iteration: You can quickly test new code changes, fix bugs, or add new features in a distributed-like environment without needing to rebuild and redeploy the entire application.

Additional Considerations:

    Data Storage Isolation: If your server nodes store data, consider configuring separate persistence directories for each server node. This ensures that their data remains isolated. You can configure different paths using the DataStorageConfiguration:

    java

serverCfg1.setDataStorageConfiguration(new DataStorageConfiguration().setStoragePath("/path/to/server1"));
serverCfg2.setDataStorageConfiguration(new DataStorageConfiguration().setStoragePath("/path/to/server2"));

Memory Allocation: If you’re working with in-memory caches or data regions, you can allocate different memory pools for each node to avoid competition for resources:

java

    DataRegionConfiguration regionConfig = new DataRegionConfiguration()
            .setName("region1")
            .setInitialSize(100 * 1024 * 1024)
            .setMaxSize(200 * 1024 * 1024);

    serverCfg1.getDataStorageConfiguration().setDataRegionConfigurations(regionConfig);

Conclusion:

Running multiple Ignite nodes (both server and client) within the same JVM allows for a streamlined development experience, eliminating the need for constant repackaging and redeployment. By logically isolating the nodes with unique configurations, ports, and data regions, you can simulate a real-world cluster while benefiting from fast iteration in local development. This setup lets you focus on code changes and testing without the overhead of managing fat JARs or a multi-node cluster.

Let me know if you'd like further details or a different approach!