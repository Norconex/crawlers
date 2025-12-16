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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hazelcast.config.ClasspathYamlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import lombok.SneakyThrows;

public class JdbcPersistWasWorking {
    @TempDir
    File tempDir;

    private static final String CONFIG_PATH =
            "cache/hazelcast-standalone.yaml";

    private String getDbPath() {
        return new File(tempDir, "crawler_db").getAbsolutePath();
    }

    void clearLiquibaseHistory(String dbPath) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:file:" + dbPath
                        + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "")) {
            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
            stmt.execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");
            System.out.println("Cleared Liquibase history tables.");
        } catch (Exception e) {
            System.out.println(
                    "Error clearing Liquibase history: " + e.getMessage());
        }
    }

    @SneakyThrows
    HazelcastInstance start(boolean cleanDb) {
        String dbPath = getDbPath();
        var dbFile = new File(dbPath + ".mv.db");
        System.out.println(
                "Test working directory: " + new File("").getAbsolutePath());
        System.out.println(
                "Liquibase DB file absolute path: " + dbFile.getAbsolutePath());
        System.out.println("Hazelcast JDBC URL: jdbc:h2:file:" + dbPath);
        if (cleanDb && dbFile.exists()) {
            dbFile.delete();
        }
        dbFile.getParentFile().mkdirs();

        // Don't add .mv.db extension - H2 adds it automatically
//        LiquibaseMigrationRunner.runH2Migrations(dbPath);
        System.out.println("DB file after migration: "
                + dbFile.getAbsolutePath() + ", exists: " + dbFile.exists());
        printDbTables(dbPath); // Print tables after migration

        var config = new ClasspathYamlConfig(CONFIG_PATH);
        config.getNetworkConfig().getJoin().getMulticastConfig()
                .setEnabled(false);

        // Override the CRAWLER map JDBC URL to use temp directory
        var mapConfig = config.getMapConfig("CRAWLER");
        var mapStoreConfig = mapConfig.getMapStoreConfig();
        mapStoreConfig.setProperty("jdbcUrl", "jdbc:h2:file:" + dbPath
                + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
        System.out.println("Updated CRAWLER MapStore jdbcUrl to: jdbc:h2:file:"
                + dbPath + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");

        System.out.println("Multicast enabled (forced): " +
                config.getNetworkConfig().getJoin().getMulticastConfig()
                        .isEnabled());
        return Hazelcast.newHazelcastInstance(config);
    }

    void shutdown(HazelcastInstance hz) {
        if (hz != null) {
            hz.shutdown();
        }
    }

    void printDbContents(String dbPath) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:file:" + dbPath
                        + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "")) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM CRAWLER");
            System.out.println("DB contents:");
            while (rs.next()) {
                System.out.println("map_key=" + rs.getString("map_key")
                        + ", map_value=" + rs.getString("map_value"));
            }
        } catch (Exception e) {
            System.out.println("DB query error: " + e.getMessage());
        }
    }

    void printDbTables(String dbPath) {
        try {
            // Small delay to ensure Liquibase commits
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:file:" + dbPath
                        + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "")) {
            // Query INFORMATION_SCHEMA directly
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'TABLE' "
                    + "ORDER BY TABLE_NAME";
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("DB tables after migration:");
                int count = 0;
                while (rs.next()) {
                    System.out.println(
                            "Table: " + rs.getString("TABLE_NAME"));
                    count++;
                }
                System.out.println("Total tables found: " + count);
            }
        } catch (Exception e) {
            System.out.println("DB table query error: " + e.getMessage());
        }
    }

    @Test
    void testJdbcPersist() {
        String dbPath = getDbPath();
        // first
        var hz = start(true); // clean DB only for first instance
        IMap<String, String> map = hz.getMap("CRAWLER");

        // Check if MapStore is configured
        var mapConfig = hz.getConfig().getMapConfig("CRAWLER");
        System.out.println("MapStore enabled: "
                + mapConfig.getMapStoreConfig().isEnabled());
        System.out.println("MapStore class: "
                + mapConfig.getMapStoreConfig().getClassName());
        System.out.println("MapStore write-delay: "
                + mapConfig.getMapStoreConfig().getWriteDelaySeconds());

        map.put("mykey", "myvalue");
        System.out.println("first time -> value: " + map.get("mykey"));
        map.flush(); // Ensure value is persisted to DB

        // Force eviction to ensure MapStore writes
        map.evict("mykey");
        System.out.println("After evict, in-memory value: " + map.get("mykey"));

        shutdown(hz);
        var dbFile = new File(dbPath + ".mv.db");
        System.out.println(
                "DB file size after first shutdown: " + dbFile.length());
        printDbContents(dbPath); // Print DB contents for debug

        // second:
        var hz2 = start(false); // do NOT clean DB for second instance
        IMap<String, String> map2 = hz2.getMap("CRAWLER");
        System.out.println("second time -> value: " + map2.get("mykey"));
        shutdown(hz2);
    }

}
