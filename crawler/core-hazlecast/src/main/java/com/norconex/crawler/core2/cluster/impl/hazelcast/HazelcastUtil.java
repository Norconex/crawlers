/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;

/**
 * Hazelcast utility methods.
 */
public final class HazelcastUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastUtil.class);
    private static final String DEFAULT_CONFIG_RESOURCE = "/default-hazelcast.xml";
    
    private HazelcastUtil() {
        // Utility class, do not instantiate
    }
    
    /**
     * Creates a Hazelcast instance using the provided configuration.
     * @param config Hazelcast cluster configuration
     * @return Hazelcast instance
     */
    public static HazelcastInstance createHazelcastInstance(HazelcastClusterConfig config) {
        Objects.requireNonNull(config, "Hazelcast cluster config cannot be null.");
        
        // If a config file is provided, use it to configure Hazelcast
        if (config.getConfigFile() != null && !config.getConfigFile().isBlank()) {
            try {
                File configFile = new File(config.getConfigFile());
                LOG.info("Initializing Hazelcast from config file: {}", 
                        configFile.getAbsolutePath());
                
                // If using client mode (connecting to existing cluster)
                if (!config.getMemberAddresses().isEmpty()) {
                    ClientConfig clientConfig = new XmlClientConfigBuilder(configFile).build();
                    clientConfig.setClusterName(config.getClusterName());
                    return HazelcastClient.newHazelcastClient(clientConfig);
                } 
                // If starting a new cluster node
                else {
                    Config hazelcastConfig = new XmlConfigBuilder(configFile).build();
                    hazelcastConfig.setClusterName(config.getClusterName());
                    return com.hazelcast.core.Hazelcast.newHazelcastInstance(hazelcastConfig);
                }
            } catch (IOException e) {
                LOG.error("Could not load Hazelcast configuration file: " 
                        + config.getConfigFile(), e);
                throw new IllegalArgumentException(
                        "Invalid Hazelcast configuration file: " 
                                + config.getConfigFile(), e);
            }
        } 
        // Otherwise use default configuration
        else {
            // If using client mode (connecting to existing cluster)
            if (!config.getMemberAddresses().isEmpty()) {
                try {
                    // Load default client configuration
                    ClientConfig clientConfig = loadDefaultClientConfig();
                    clientConfig.setClusterName(config.getClusterName());
                    clientConfig.getNetworkConfig().addAddress(
                            config.getMemberAddresses().toArray(new String[0]));
                    return HazelcastClient.newHazelcastClient(clientConfig);
                } catch (IOException e) {
                    LOG.error("Failed to load default Hazelcast client configuration", e);
                    throw new IllegalArgumentException(
                            "Failed to initialize Hazelcast client", e);
                }
            } 
            // If starting a new cluster node with default settings
            else {
                try {
                    // Load default server configuration
                    Config hazelcastConfig = loadDefaultConfig();
                    hazelcastConfig.setClusterName(config.getClusterName());
                    return com.hazelcast.core.Hazelcast.newHazelcastInstance(hazelcastConfig);
                } catch (IOException e) {
                    LOG.error("Failed to load default Hazelcast configuration", e);
                    throw new IllegalArgumentException(
                            "Failed to initialize Hazelcast", e);
                }
            }
        }
    }

    /**
     * Loads the default Hazelcast configuration from the classpath.
     * @return Default Hazelcast configuration
     * @throws IOException if loading fails
     */
    private static Config loadDefaultConfig() throws IOException {
        File tempConfig = extractDefaultConfigToTempFile();
        return new XmlConfigBuilder(tempConfig).build();
    }
    
    /**
     * Loads the default Hazelcast client configuration from the classpath.
     * @return Default Hazelcast client configuration
     * @throws IOException if loading fails
     */
    private static ClientConfig loadDefaultClientConfig() throws IOException {
        File tempConfig = extractDefaultConfigToTempFile();
        return new XmlClientConfigBuilder(tempConfig).build();
    }
    
    /**
     * Extracts the default configuration from the classpath to a temporary file.
     * @return Temporary file containing the default configuration
     * @throws IOException if extraction fails
     */
    private static File extractDefaultConfigToTempFile() throws IOException {
        try (InputStream is = HazelcastUtil.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (is == null) {
                throw new IOException("Default Hazelcast configuration not found on classpath: " 
                        + DEFAULT_CONFIG_RESOURCE);
            }
            
            Path tempFile = Files.createTempFile("hazelcast-default-", ".xml");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Schedule for deletion on JVM exit
            tempFile.toFile().deleteOnExit();
            
            return tempFile.toFile();
        }
    }

    /**
     * Serializes an object to JSON string.
     * @param obj the object to serialize
     * @return JSON string representation
     * @throws IOException if serialization fails
     */
    public static String toJson(Object obj) throws IOException {
        return new ObjectMapper().writeValueAsString(obj);
    }

    /**
     * Deserializes a JSON string to an object.
     * @param <T> the type of object to deserialize to
     * @param json the JSON string to deserialize
     * @param clazz the class of the object to deserialize to
     * @return deserialized object
     * @throws IOException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws IOException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return new ObjectMapper().readValue(json, clazz);
    }
}