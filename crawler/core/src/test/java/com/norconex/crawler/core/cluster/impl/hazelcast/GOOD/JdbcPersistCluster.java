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
package com.norconex.crawler.core.cluster.impl.hazelcast.GOOD;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hazelcast.config.ClasspathYamlConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.impl.hazelcast.LiquibaseMigrationRunner;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Multi-node cluster test for JDBC persistence using PostgreSQL.
 * This test verifies that data persisted by one node can be read
 * by another node after the first node is shut down.
 */
@Slf4j
@Testcontainers
class JdbcPersistCluster {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("crawler_test")
                    .withUsername("test")
                    .withPassword("test");

    private final List<HazelcastInstance> instances = new ArrayList<>();
    private String jdbcUrl;
    private String username;
    private String password;

    @BeforeEach
    void setUp() throws Exception {
        // Get connection details from testcontainer
        jdbcUrl = POSTGRES.getJdbcUrl();
        username = POSTGRES.getUsername();
        password = POSTGRES.getPassword();

        LOG.info("PostgreSQL container started at: {}", jdbcUrl);

        // Run Liquibase migrations to create tables
        LiquibaseMigrationRunner.runMigrations(
                jdbcUrl, username, password);
        LOG.info("Liquibase migrations completed");

        printDbTables();
    }

    @AfterEach
    void tearDown() {
        // Shutdown all instances
        instances.forEach(hz -> {
            if (hz != null && hz.getLifecycleService().isRunning()) {
                hz.shutdown();
            }
        });
        instances.clear();
    }

    /**
     * Creates a Hazelcast cluster node configured to use the
     * shared PostgreSQL database.
     *
     * @param nodePort the port for this node (e.g., 5701, 5702)
     * @return configured Hazelcast instance
     */
    @SneakyThrows
    private HazelcastInstance createNode(int nodePort) {
        var config = new ClasspathYamlConfig(
                "cache/hazelcast-cluster.yaml");

        // Configure cluster name
        config.setClusterName("test-cluster");

        // Configure network settings for cluster formation
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(nodePort);
        networkConfig.setPortAutoIncrement(false);

        // Configure TCP/IP cluster discovery
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);

        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        tcpIpConfig.setEnabled(true);
        // Add all potential cluster members
        tcpIpConfig.addMember("127.0.0.1:5701");
        tcpIpConfig.addMember("127.0.0.1:5702");
        tcpIpConfig.addMember("127.0.0.1:5703");

        // Build JDBC URL with credentials embedded for PostgreSQL
        // This avoids authentication issues with default OS username
        String jdbcUrlWithCreds = jdbcUrl
                + "?user=" + username
                + "&password=" + password;

        // Configure data connection with credentials properly set
        var dataConnectionConfig =
                new com.hazelcast.config.DataConnectionConfig()
                        .setName("postgres-connection")
                        .setType("JDBC")
                        .setShared(true);

        // Set properties with explicit username/password
        dataConnectionConfig.setProperty("jdbcUrl", jdbcUrl);
        dataConnectionConfig.setProperty("user", username);
        dataConnectionConfig.setProperty("password", password);

        config.getDataConnectionConfigs().put(
                "postgres-connection", dataConnectionConfig);

        // Configure JDBC MapStore for CRAWLER map
        var mapConfig = config.getMapConfig("CRAWLER");
        var mapStoreConfig = mapConfig.getMapStoreConfig();
        mapStoreConfig.setEnabled(true);
        mapStoreConfig.setClassName(
                "com.hazelcast.mapstore.GenericMapStore");
        mapStoreConfig.setInitialLoadMode(
                com.hazelcast.config.MapStoreConfig.InitialLoadMode.EAGER);
        mapStoreConfig.setWriteDelaySeconds(0);
        mapStoreConfig.setWriteCoalescing(false);

        // Use data-connection-ref and specify table name
        // Note: PostgreSQL stores unquoted identifiers in lowercase
        mapStoreConfig.setProperty(
                "data-connection-ref", "postgres-connection");
        mapStoreConfig.setProperty("external-name", "crawler");
        mapStoreConfig.setProperty("id-column", "map_key");
        mapStoreConfig.setProperty("type-name", "CrawlerData");

        LOG.info("Starting Hazelcast node on port {}", nodePort);
        LOG.info("JDBC URL: {}", jdbcUrl);
        LOG.info("Database user: {}", username);
        var instance = Hazelcast.newHazelcastInstance(config);
        instances.add(instance);

        return instance;
    }

    /**
     * Print database tables for debugging.
     */
    private void printDbTables() {
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl, username, password)) {
            String sql =
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public' "
                            + "AND table_type = 'BASE TABLE' "
                            + "ORDER BY table_name";

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                LOG.info("Database tables:");
                int count = 0;
                while (rs.next()) {
                    LOG.info("  - {}", rs.getString("table_name"));
                    count++;
                }
                LOG.info("Total tables: {}", count);
            }
        } catch (Exception e) {
            LOG.error("Error listing tables", e);
        }
    }

    /**
     * Print database contents for the CRAWLER table.
     */
    private void printDbContents() {
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl, username, password)) {
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT * FROM CRAWLER")) {
                LOG.info("CRAWLER table contents:");
                int count = 0;
                while (rs.next()) {
                    LOG.info("  map_key={}, map_value={}",
                            rs.getString("map_key"),
                            rs.getString("map_value"));
                    count++;
                }
                LOG.info("Total rows: {}", count);
            }
        } catch (Exception e) {
            LOG.error("Error querying CRAWLER table", e);
        }
    }

    /**
     * Test multi-node cluster with JDBC persistence.
     * This test:
     * 1. Starts node1 and writes data
     * 2. Starts node2 and verifies it can see node1's data
     * 3. Shuts down node1
     * 4. Verifies node2 still has the data
     * 5. Shuts down node2
     * 6. Starts node3 and verifies it loads data from database
     */
    @Test
    void testMultiNodeJdbcPersistence() throws Exception {
        // Step 1: Start first node and write data
        LOG.info("=== Starting Node 1 ===");
        HazelcastInstance node1 = createNode(5701);
        waitForClusterSize(node1, 1);

        IMap<String, GenericRecord> map1 = node1.getMap("CRAWLER");
        LOG.info("Node 1 MapStore enabled: {}",
                node1.getConfig().getMapConfig("CRAWLER")
                        .getMapStoreConfig().isEnabled());

        // Create GenericRecords for values
        GenericRecord value1 = GenericRecordBuilder.compact("CrawlerData")
                .setString("map_value", "value1").build();
        GenericRecord value2 = GenericRecordBuilder.compact("CrawlerData")
                .setString("map_value", "value2").build();

        map1.put("key1", value1);
        map1.put("key2", value2);
        LOG.info("Node 1 wrote 2 entries");

        // Force persistence
        map1.flush();
        LOG.info("Node 1 flushed map to database");

        // Verify data in memory on node1
        assertThat(map1.get("key1").getString("map_value")).isEqualTo("value1");
        assertThat(map1.get("key2").getString("map_value")).isEqualTo("value2");
        LOG.info("Node 1 verified in-memory data");

        // Step 2: Start second node - should join cluster and see data
        LOG.info("=== Starting Node 2 ===");
        HazelcastInstance node2 = createNode(5702);
        waitForClusterSize(node1, 2);
        waitForClusterSize(node2, 2);

        IMap<String, GenericRecord> map2 = node2.getMap("CRAWLER");
        LOG.info("Cluster formed with 2 nodes");

        // Node 2 should see the data (either from cluster or DB)
        assertThat(map2.get("key1").getString("map_value")).isEqualTo("value1");
        assertThat(map2.get("key2").getString("map_value")).isEqualTo("value2");
        LOG.info("Node 2 verified data from cluster");

        // Step 3: Write from node2
        GenericRecord value3 = GenericRecordBuilder.compact("CrawlerData")
                .setString("map_value", "value3").build();
        map2.put("key3", value3);
        map2.flush();
        LOG.info("Node 2 wrote additional entry");

        // Both nodes should see all data
        assertThat(map1.size()).isEqualTo(3);
        assertThat(map2.size()).isEqualTo(3);

        // Step 4: Shutdown node1
        LOG.info("=== Shutting down Node 1 ===");
        node1.shutdown();
        instances.remove(node1);
        waitForClusterSize(node2, 1);

        // Node 2 should still have all data
        assertThat(map2.size()).isEqualTo(3);
        assertThat(map2.get("key1").getString("map_value")).isEqualTo("value1");
        assertThat(map2.get("key2").getString("map_value")).isEqualTo("value2");
        assertThat(map2.get("key3").getString("map_value")).isEqualTo("value3");
        LOG.info("Node 2 still has all data after Node 1 shutdown");

        printDbContents();

        // Step 5: Shutdown node2
        LOG.info("=== Shutting down Node 2 ===");
        map2.flush(); // Ensure all data is persisted
        Thread.sleep(500); // Give it time to persist
        node2.shutdown();
        instances.remove(node2);

        printDbContents();

        // Step 6: Start node3 - should load data from database
        LOG.info("=== Starting Node 3 (fresh node) ===");
        HazelcastInstance node3 = createNode(5703);
        waitForClusterSize(node3, 1);

        IMap<String, GenericRecord> map3 = node3.getMap("CRAWLER");

        // Wait for MapStore to load data
        Thread.sleep(1000);

        // Verify all data was loaded from database
        LOG.info("Node 3 map size: {}", map3.size());
        assertThat(map3.size())
                .withFailMessage(
                        "Expected 3 entries but found %d", map3.size())
                .isEqualTo(3);
        assertThat(map3.get("key1").getString("map_value")).isEqualTo("value1");
        assertThat(map3.get("key2").getString("map_value")).isEqualTo("value2");
        assertThat(map3.get("key3").getString("map_value")).isEqualTo("value3");
        LOG.info("Node 3 successfully loaded all data from database");

        LOG.info("=== Test completed successfully ===");
    }

    /**
     * Wait for the cluster to reach the expected size.
     */
    private void waitForClusterSize(
            HazelcastInstance hz, int expectedSize)
            throws InterruptedException {
        for (int i = 0; i < 30; i++) { // 30 seconds timeout
            if (hz.getCluster().getMembers().size() == expectedSize) {
                LOG.info("Cluster size reached: {}", expectedSize);
                return;
            }
            TimeUnit.MILLISECONDS.sleep(1000);
        }
        throw new IllegalStateException(
                "Cluster did not reach expected size: " + expectedSize);
    }
}
