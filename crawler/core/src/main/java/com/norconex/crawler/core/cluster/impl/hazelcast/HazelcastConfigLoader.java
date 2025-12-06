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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.ClasspathYamlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.FileSystemYamlConfig;
import com.norconex.crawler.core.cluster.ClusterException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class HazelcastConfigLoader {

    private HazelcastConfigLoader() {
    }

    static Config load(
            HazelcastClusterConfig hzClusterConfig, boolean isClustered) {
        LOG.info("Executing in standalone mode: {}", !isClustered);
        var cfg = doLoad(hzClusterConfig);
        //        configureQueuePersistence(cfg);
        validateClusterMode(cfg, hzClusterConfig, isClustered);
        return cfg;
    }

    private static Config doLoad(
            HazelcastClusterConfig hzClusterConfig) {
        var configPath =
                StringUtils.trimToNull(hzClusterConfig.getConfigFile());
        if (configPath == null) {
            configPath = hzClusterConfig.getPreset().getPath();
        }

        LOG.info("Using configuration preset: {}", configPath);

        var isClasspath = configPath.startsWith("classpath:");
        var resourceName = isClasspath
                ? configPath.substring("classpath:".length())
                : configPath;
        var lowerName = resourceName.toLowerCase();
        try {
            if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
                if (isClasspath || !Files.exists(Path.of(resourceName))) {
                    return new ClasspathYamlConfig(resourceName);
                }
                return new FileSystemYamlConfig(resourceName);
            }
            if (lowerName.endsWith(".xml")) {
                if (isClasspath || !Files.exists(Path.of(resourceName))) {
                    return new ClasspathXmlConfig(resourceName);
                }
                return new FileSystemXmlConfig(resourceName);
            }
            throw new IllegalArgumentException(
                    "Unknown Hazelcast config file type: " + configPath);
        } catch (Exception e) {
            throw new ClusterException(
                    "Failed to load Hazelcast config: " + configPath, e);
        }
    }

    //TODO reconstruct queue from map on startup instead
    //    private static void configureQueuePersistence(Config config) {
    //        // Configure RocksDB persistence for the crawlQueue
    //        var queueConfig = config.getQueueConfig("crawlQueue");
    //        var queueStoreConfig = new com.hazelcast.config.QueueStoreConfig();
    //        queueStoreConfig.setEnabled(true);
    //        queueStoreConfig.setClassName(RocksDBQueueStore.class.getName());
    //        // The database.dir property will be read from system property
    //        // set in HazelcastCluster.init()
    //        var props = new java.util.Properties();
    //        props.setProperty("database.dir",
    //            System.getProperty("hazelcast.persistence.dir", "data/rocksdb"));
    //        queueStoreConfig.setProperties(props);
    //        queueConfig.setQueueStoreConfig(queueStoreConfig);
    //
    //        LOG.info("Configured RocksDB persistence for crawlQueue");
    //    }

    private static void validateClusterMode(
            Config hzConfig, HazelcastClusterConfig hzClusterConfig,
            boolean isClustered) {
        var configIsClustered = false;
        var join = hzConfig.getNetworkConfig().getJoin();
        // Consider clustered if multicast or tcp-ip join is enabled
        if ((join.getMulticastConfig() != null
                && join.getMulticastConfig().isEnabled()) ||
                (join.getTcpIpConfig() != null
                        && join.getTcpIpConfig().isEnabled())) {
            configIsClustered = true;
        }
        // Also consider backup-count > 0 as a sign of clustering
        var backupCount = hzConfig.getMapConfig("default") != null
                ? hzConfig.getMapConfig("default").getBackupCount()
                : 0;
        if (backupCount > 0) {
            configIsClustered = true;
        }
        var intendedClustered = hzClusterConfig
                .getPreset() == HazelcastClusterConfig.Preset.CLUSTER;
        // If configFile is set, use isClustered() method
        if (StringUtils.isNotBlank(hzClusterConfig.getConfigFile())) {
            intendedClustered = isClustered;
        }
        if (configIsClustered != intendedClustered) {
            var msg = String.format("""
                Hazelcast config mode mismatch: Config is %s but \
                intended mode is %s. Check your Hazelcast config and \
                application settings.""",
                    configIsClustered ? "CLUSTERED" : "STANDALONE",
                    intendedClustered ? "CLUSTERED" : "STANDALONE");
            //            LOG.warn(msg);
            // Optionally, throw exception to fail fast
            throw new ClusterException(msg);
        }
    }
}
