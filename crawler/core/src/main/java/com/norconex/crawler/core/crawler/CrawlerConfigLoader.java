/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.xml.XML;


/**
 * HTTP Crawler configuration loader.
 */
public class CrawlerConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(
            CrawlerConfigLoader.class);

    private final Class<? extends CrawlerConfig> crawlerConfigClass;


    public CrawlerConfigLoader(
            Class<? extends CrawlerConfig> crawlerConfigClass) {
        this.crawlerConfigClass = crawlerConfigClass;
    }

    /**
     * Loads crawler configurations.
     * @param configFile configuration file
     * @return crawler configs
     * @deprecated Since 2.0.0, use {@link #loadCrawlerConfigs(Path)} instead
     */
    @Deprecated
    public List<CrawlerConfig> loadCrawlerConfigs(File configFile) {
        return loadCrawlerConfigs(configFile, null);
    }
    /**
     * Loads crawler configurations.
     * @param configFile configuration file
     * @return crawler configs
         */
    public List<CrawlerConfig> loadCrawlerConfigs(Path configFile) {
        return loadCrawlerConfigs(configFile, null);
    }
    /**
     * Loads crawler configurations.
     * @param configFile configuration file
     * @param configVariables variables file
     * @return crawler configs
     * @deprecated Since 2.0.0, use {@link #loadCrawlerConfigs(Path, Path)}
     *             instead
     */
    @Deprecated
    public List<CrawlerConfig> loadCrawlerConfigs(
            File configFile, File configVariables) {
        Path cfg = null;
        Path vars = null;
        if (configFile != null) {
            cfg = configFile.toPath();
        }
        if (configVariables != null) {
            vars = configVariables.toPath();
        }
        return loadCrawlerConfigs(cfg, vars);
    }
    /**
     * Loads crawler configurations.
     * @param configFile configuration file
     * @param configVariables variables file
     * @return crawler configs
         */
    public List<CrawlerConfig> loadCrawlerConfigs(
            Path configFile, Path configVariables) {
        return loadCrawlerConfigs(new ConfigurationLoader().setVariablesFile(
                configVariables).loadXML(configFile));
    }
    public List<CrawlerConfig> loadCrawlerConfigs(XML xml) {
        try {
            var crawlerDefaultsXML = xml.getXML("crawlerDefaults");

            var crawlersXML = xml.getXMLList("crawlers/crawler");
            List<CrawlerConfig> configs = new ArrayList<>();
            for (XML crawlerXML : crawlersXML) {
                CrawlerConfig  config = crawlerConfigClass.newInstance();
                if (crawlerDefaultsXML != null) {
                    loadCrawlerConfig(config, crawlerDefaultsXML);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Crawler defaults loaded for new crawler.");
                    }
                }
                loadCrawlerConfig(config, crawlerXML);
                configs.add(config);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Crawler configuration loaded: "
                            + config.getId());
                }
            }
            return configs;
        } catch (Exception e) {
            throw new CrawlerException(
                    "Cannot load crawler configurations.", e);
        }
    }

    /**
     * Loads a crawler configuration, which can be either the default
     * crawler or real crawler configuration instances
     * (keeping defaults).
     * @param config crawler configuration to populate/overwrite
     * @param xml the XML representing the crawler configuration.
     * @throws IOException problem loading crawler configuration
     */
    public void loadCrawlerConfig(
            CrawlerConfig config, XML xml) throws IOException {
        if (xml == null) {
            LOG.warn("Passing a null configuration for "
                    + config.getId() + ", skipping.");
            return;
        }
        var loadingDefaults =
                "crawlerDefaults".equalsIgnoreCase(xml.getName());

        if (!loadingDefaults) {
            var crawlerId = xml.getString("@id", null);
            if (StringUtils.isBlank(crawlerId)) {
                throw new CrawlerException(
                        "Crawler ID is missing in configuration.");
            }
        }

        xml.populate(config);
    }
}
