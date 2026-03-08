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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.config.YamlConfigBuilder;
import com.norconex.crawler.core.cluster.ClusterException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class HazelcastConfigLoader {

    private HazelcastConfigLoader() {
    }

    public static Config load(String configFile,
            Map<String, Object> variables) {
        var cfgPath = StringUtils.trimToNull(configFile);

        if (!StringUtils.endsWithAny(
                cfgPath.toLowerCase(), ".yaml", ".yml", ".xml")) {
            throw new IllegalArgumentException(
                    "Unknown Hazelcast config file type: " + cfgPath);
        }

        try {
            var content = interpolate(cfgPath, variables);
            LOG.info("Interpolated content: {}", content);
            return createHzConfig(cfgPath, content);
        } catch (IOException e) {
            throw new ClusterException(
                    "Could not load Hazelcast configuration.", e);
        }
    }

    private static Config createHzConfig(String cfgPath, String content)
            throws IOException {
        var lcPath = cfgPath.toLowerCase();
        try (var in = new java.io.ByteArrayInputStream(content.getBytes())) {
            if (lcPath.endsWith(".yaml") || lcPath.endsWith(".yml")) {
                return new YamlConfigBuilder(in).build();
            }
            // it has to be XML
            return new XmlConfigBuilder(in).build();
        }
    }

    private static String interpolate(
            String cfgPath, Map<String, Object> variables)
            throws IOException {

        var isClasspath = cfgPath.startsWith("classpath:");
        var resourceName = isClasspath
                ? cfgPath.substring("classpath:".length())
                : cfgPath;

        String content;
        if (isClasspath) {
            try (var in = HazelcastConfigLoader.class.getClassLoader()
                    .getResourceAsStream(resourceName)) {
                if (in == null) {
                    throw new FileNotFoundException(
                            "Classpath resource not found: " + resourceName);
                }
                content = new String(in.readAllBytes());
            }
        } else {
            content = Files.readString(Path.of(resourceName));
        }

        return new StringSubstitutor(variables)
                .setValueDelimiter('|')
                .replace(content);

    }
    //
    //    public static Config load2(String configFile, Path workDir) {
    //
    //        Config config;
    //        try {
    //            config = doLoad2(configFile);
    //        } catch (IOException e) {
    //            throw new ClusterException(
    //                    "Could not load Hazelcast configuration.", e);
    //        }
    //
    //        config.getDataConnectionConfigs().forEach((k, cfg) -> {
    //            var url = cfg.getProperty("jdbcUrl");
    //            if (StringUtils.isNotBlank(url)) {
    //                cfg.setProperty("jdbcUrl",
    //                        url.replace("{workDir}", workDir.toString()));
    //            }
    //        });
    //
    //        return config;
    //    }

    //    static Config doLoad2(String configFile)
    //            throws FileNotFoundException {
    //        var cfgPath = StringUtils.trimToNull(configFile);
    //
    //        var isClasspath = cfgPath.startsWith("classpath:");
    //        var resourceName = isClasspath
    //                ? cfgPath.substring("classpath:".length())
    //                : cfgPath;
    //        var lowerName = resourceName.toLowerCase();
    //        if (lowerName.endsWith(".yaml")
    //                || lowerName.endsWith(".yml")) {
    //            if (isClasspath
    //                    || !Files.exists(Path.of(resourceName))) {
    //                return new ClasspathYamlConfig(resourceName);
    //            }
    //            return new FileSystemYamlConfig(resourceName);
    //        }
    //        if (lowerName.endsWith(".xml")) {
    //            if (isClasspath
    //                    || !Files.exists(Path.of(resourceName))) {
    //                return new ClasspathXmlConfig(resourceName);
    //            }
    //            return new FileSystemXmlConfig(resourceName);
    //        }
    //        throw new IllegalArgumentException(
    //                "Unknown Hazelcast config file type: " + cfgPath);
    //    }
    //
    //    /**
    //     * Loads a Hazelcast Config from a YAML file (classpath or filesystem),
    //     * performs variable expansion using the provided map, and returns the Config.
    //     * Only supports YAML files.
    //     *
    //     * @param configFile path or classpath to YAML config
    //     * @param variables map of variable names to values for expansion
    //     * @return Hazelcast Config
    //     */
    //    public static Config loadYamlWithExpansion(String configFile,
    //            Map<String, String> variables) {
    //        String yamlContent;
    //        try {
    //            var resourceName = configFile;
    //            var isClasspath = resourceName.startsWith("classpath:");
    //            if (isClasspath) {
    //                resourceName = resourceName.substring("classpath:".length());
    //                try (var in = HazelcastConfigLoader.class.getClassLoader()
    //                        .getResourceAsStream(resourceName)) {
    //                    if (in == null) {
    //                        throw new FileNotFoundException(
    //                                "Classpath resource not found: "
    //                                        + resourceName);
    //                    }
    //                    yamlContent = new String(in.readAllBytes());
    //                }
    //            } else {
    //                yamlContent = Files.readString(Path.of(resourceName));
    //            }
    //        } catch (Exception e) {
    //            throw new ClusterException(
    //                    "Could not read Hazelcast YAML config for expansion.", e);
    //        }
    //        // Perform variable expansion using Apache Commons Text
    //        yamlContent = StringSubstitutor.replace(yamlContent, variables);
    //        // Build Hazelcast Config from expanded YAML
    //        try (var in =
    //                new java.io.ByteArrayInputStream(yamlContent.getBytes())) {
    //            return new com.hazelcast.config.YamlConfigBuilder(in).build();
    //        } catch (Exception e) {
    //            throw new ClusterException(
    //                    "Could not build Hazelcast config from expanded YAML.", e);
    //        }
    //    }
}
