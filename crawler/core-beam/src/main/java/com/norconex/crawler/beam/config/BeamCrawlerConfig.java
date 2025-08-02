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
package com.norconex.crawler.beam.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.norconex.crawler.beam.frontier.FrontierConfig;
import com.norconex.crawler.beam.scheduler.SchedulerConfig;
import com.norconex.crawler.beam.discovery.DiscoveryConfig;
import com.norconex.crawler.beam.deduplication.DeduplicationConfig;

import lombok.Data;

/**
 * Main configuration for the Beam Crawler.
 * @author Norconex Inc.
 */
@Data
public class BeamCrawlerConfig {
    
    private String name;
    private String description;
    private String version = "1.0";
    
    private FrontierConfig frontier = new FrontierConfig();
    private SchedulerConfig scheduler = new SchedulerConfig();
    private DiscoveryConfig discovery = new DiscoveryConfig();
    private DeduplicationConfig deduplication = new DeduplicationConfig();
    
    private boolean multiTenantEnabled = false;
    private List<String> startUrls = new ArrayList<>();
    private List<String> excludePatterns = new ArrayList<>();
    private List<String> includePatterns = new ArrayList<>();
    
    private CommitterConfig committer = new CommitterConfig();
    private ImporterConfig importer = new ImporterConfig();
    
    /**
     * Load configuration from a YAML file.
     * @param file the configuration file
     * @return the loaded configuration
     * @throws IOException if an error occurs while loading the file
     */
    public static BeamCrawlerConfig loadFromFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(file, BeamCrawlerConfig.class);
    }
    
    @Data
    public static class CommitterConfig {
        private String type = "solr";
        private String endpoint;
        private int batchSize = 100;
        private boolean useNiFiIntegration = false;
        private boolean useCamelIntegration = false;
    }
    
    @Data
    public static class ImporterConfig {
        private boolean extractEmbeddedResources = true;
        private boolean parseContent = true;
        private List<String> transformerClasses = new ArrayList<>();
    }
}