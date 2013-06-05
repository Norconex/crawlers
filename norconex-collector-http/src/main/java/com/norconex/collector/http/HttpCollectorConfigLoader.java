/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http;

import java.io.File;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfigLoader;
import com.norconex.commons.lang.config.ConfigurationLoader;

/**
 * HTTP Collector configuration loader.  Configuration options are defined
 * as part of general product documentation.
 * @author Pascal Essiembre
 */
public final class HttpCollectorConfigLoader {

    private static final Logger LOG = LogManager.getLogger(
            HttpCollectorConfigLoader.class);
    
    private HttpCollectorConfigLoader() {
        super();
    }

    public static HttpCollectorConfig loadCollectorConfig(
            File configFile, File configVariables) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading configuration file: " + configFile);
        }
        if (!configFile.exists()) {
            return null;
        }
        
        ConfigurationLoader configLoader = new ConfigurationLoader();
        XMLConfiguration xml = configLoader.loadXML(
                configFile, configVariables);

        String collectorID = xml.getString("[@id]");
        HttpCrawlerConfig[] crawlers = 
                HttpCrawlerConfigLoader.loadCrawlerConfigs(xml);

        HttpCollectorConfig config = new HttpCollectorConfig(collectorID);
        config.setCrawlerConfigs(crawlers);

        config.setLogsDir(xml.getString("logsDir", config.getLogsDir()));
        config.setProgressDir(
                xml.getString("progressDir", config.getProgressDir()));

        if (LOG.isInfoEnabled()) {
            LOG.info("Configuration loaded: id=" + collectorID
                    + "; logsDir=" + config.getLogsDir()
                    + "; progressDir=" + config.getProgressDir());
        }
        return config;
    }

    
}
