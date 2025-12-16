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

import com.hazelcast.config.Config;
import com.hazelcast.config.DataConnectionConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.map.IMap;

public class HazelcastJDBCMapStoreExample {

    public static void main(String[] args) throws Exception {
        // Create Hazelcast configuration
        var config = new Config();
        config.setClusterName("product-cache-cluster");

        // ========== JDBC DATA CONNECTION ==========
        // This replaces the need for connection handling in custom MapStore
        var dataConnectionConfig =
                new DataConnectionConfig("mysql-products");
        dataConnectionConfig.setType("JDBC");
        dataConnectionConfig.setProperty("jdbcUrl",
                "jdbc:mysql://localhost:3306/hazelcast_demo");
        dataConnectionConfig.setProperty("username", "root");
        dataConnectionConfig.setProperty("password", "your_password");
        dataConnectionConfig.setProperty("maximumPoolSize", "10");
        dataConnectionConfig.setShared(true);
        config.addDataConnectionConfig(dataConnectionConfig);

        // ========== MAP CONFIGURATION WITH GENERIC MAPSTORE ==========
        var mapConfig = new MapConfig("products");

        // Configure the Generic MapStore
        var mapStoreConfig = new MapStoreConfig();
        mapStoreConfig.setEnabled(true);

        // Use Hazelcast's built-in Generic MapStore (no custom code needed!)
        mapStoreConfig.setClassName("com.hazelcast.mapstore.GenericMapStore");

        // ========== KEY CONFIGURATION FOR PRELOADING ==========
        // EAGER mode: Preload ALL data at startup - map is blocked until loading completes
        // LAZY mode: Load data on-demand when entries are first accessed
        mapStoreConfig.setInitialLoadMode(MapStoreConfig.InitialLoadMode.EAGER);

        // ========== WRITE-THROUGH CACHING ==========
        // write-delay-seconds = 0 means synchronous (write-through)
        // Any value > 0 means asynchronous (write-behind) with that delay
        mapStoreConfig.setWriteDelaySeconds(0);

        // ========== MAPSTORE PROPERTIES ==========
        // Reference the data connection
        mapStoreConfig.setProperty("data-connection-ref", "mysql-products");

        // Specify the database table name
        mapStoreConfig.setProperty("external-name", "products");

        // Specify which column is the primary key (maps to IMap key)
        mapStoreConfig.setProperty("id-column", "id");

        // Optionally specify which columns to load (if not all)
        // mapStoreConfig.setProperty("columns", "id,name,price,stock");

        // Set the compact type name for GenericRecord
        mapStoreConfig.setProperty("type-name", "Product");

        mapConfig.setMapStoreConfig(mapStoreConfig);

        // Add the map configuration
        config.addMapConfig(mapConfig);

        // ========== START HAZELCAST ==========
        System.out.println("Starting Hazelcast cluster...");
        var hz = Hazelcast.newHazelcastInstance(config);

        // ========== GET THE MAP ==========
        // With EAGER mode, this call blocks until ALL data is loaded from database
        System.out.println("Getting products map (will preload all data)...");
        var startTime = System.currentTimeMillis();

        IMap<Long, Object> productsMap = hz.getMap("products");

        var loadTime = System.currentTimeMillis() - startTime;
        System.out.println("Map loaded in " + loadTime + "ms");
        System.out.println("Initial map size: " + productsMap.size());

        // ========== READ DATA (FROM CACHE) ==========
        System.out.println("\n--- Reading cached data ---");
        for (Long key : productsMap.keySet()) {
            var product = productsMap.get(key);
            System.out.println("Product " + key + ": " + product);
        }

        // ========== WRITE DATA (SYNC TO DATABASE) ==========
        System.out.println(
                "\n--- Writing new product (will persist to database) ---");
        // Note: Values are stored as GenericRecord
        // For simplicity, you can put values as Maps
        java.util.Map<String, Object> newProduct = new java.util.HashMap<>();
        newProduct.put("id", 4L);
        newProduct.put("name", "Monitor");
        newProduct.put("price", 299.99);
        newProduct.put("stock", 75);

        // This put() will:
        // 1. Write to database (synchronously, because write-delay = 0)
        // 2. Store in the distributed map
        productsMap.put(4L, newProduct);
        System.out
                .println("Added new product. Map size: " + productsMap.size());

        // ========== UPDATE DATA ==========
        System.out.println(
                "\n--- Updating product (will persist to database) ---");
        java.util.Map<String, Object> updatedProduct =
                new java.util.HashMap<>();
        updatedProduct.put("id", 1L);
        updatedProduct.put("name", "Gaming Laptop");
        updatedProduct.put("price", 1299.99);
        updatedProduct.put("stock", 45);

        productsMap.put(1L, updatedProduct);
        System.out.println("Updated product 1");

        // ========== DELETE DATA ==========
        System.out.println(
                "\n--- Deleting product (will delete from database) ---");
        productsMap.remove(2L);
        System.out
                .println("Deleted product 2. Map size: " + productsMap.size());

        // ========== VERIFY PERSISTENCE ==========
        System.out.println("\n--- Simulating cluster restart ---");
        System.out.println("Shutting down cluster...");
        hz.shutdown();

        // Wait a moment
        Thread.sleep(2000);

        // Restart with same configuration
        System.out.println("Restarting cluster (will reload from database)...");
        var hz2 = Hazelcast.newHazelcastInstance(config);
        IMap<Long, Object> productsMap2 = hz2.getMap("products");

        System.out.println("After restart, map size: " + productsMap2.size());
        System.out.println("Data was persisted and reloaded successfully!");

        // Show the data
        System.out.println("\n--- Reloaded data ---");
        for (Long key : productsMap2.keySet()) {
            var product = productsMap2.get(key);
            System.out.println("Product " + key + ": " + product);
        }

        // Cleanup
        hz2.shutdown();
    }
}
