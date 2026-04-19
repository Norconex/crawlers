/* Copyright 2025-2026 Norconex Inc.
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
import org.apache.commons.lang3.Strings;
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

        if (!Strings.CI.endsWithAny(cfgPath, ".yaml", ".yml", ".xml")) {
            throw new IllegalArgumentException(
                    "Unknown Hazelcast config file type: " + cfgPath);
        }

        try {
            var content = interpolate(cfgPath, variables);
            LOG.debug("Loaded interpolated Hazelcast configuration from: {}",
                    cfgPath);
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
}
