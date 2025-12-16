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

import java.io.FileNotFoundException;
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
public final class HazelcastConfigLoader {

    static {
        System.setProperty("hazelcast.phone.home.enabled", "false");
    }

    private HazelcastConfigLoader() {
    }

    public static Config load(String configFile, Path workDir) {

        Config config;
        try {
            config = doLoad(configFile, workDir);
        } catch (FileNotFoundException e) {
            throw new ClusterException(
                    "Could not load Hazelcast configuration.", e);
        }

        config.getDataConnectionConfigs().forEach((k, cfg) -> {
            var url = cfg.getProperty("jdbcUrl");
            if (StringUtils.isNotBlank(url)) {
                cfg.setProperty("jdbcUrl",
                        url.replace("{workDir}", workDir.toString()));
            }
        });

        return config;
    }

    static Config doLoad(String configFile, Path workDir)
            throws FileNotFoundException {
        var cfgPath = StringUtils.trimToNull(configFile);

        var isClasspath = cfgPath.startsWith("classpath:");
        var resourceName = isClasspath
                ? cfgPath.substring("classpath:".length())
                : cfgPath;
        var lowerName = resourceName.toLowerCase();
        if (lowerName.endsWith(".yaml")
                || lowerName.endsWith(".yml")) {
            if (isClasspath
                    || !Files.exists(Path.of(resourceName))) {
                return new ClasspathYamlConfig(resourceName);
            }
            return new FileSystemYamlConfig(resourceName);
        }
        if (lowerName.endsWith(".xml")) {
            if (isClasspath
                    || !Files.exists(Path.of(resourceName))) {
                return new ClasspathXmlConfig(resourceName);
            }
            return new FileSystemXmlConfig(resourceName);
        }
        throw new IllegalArgumentException(
                "Unknown Hazelcast config file type: " + cfgPath);
    }
}
