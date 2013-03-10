package com.norconex.collector.http;

import java.io.File;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfigLoader;
import com.norconex.commons.lang.config.ConfigurationLoader;


public final class HttpCollectorConfigLoader {

    private static final Logger LOG = LogManager.getLogger(
            HttpCollectorConfigLoader.class);
    
    public static HttpCollectorConfig loadConnectorConfig(
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

        String connectorID = xml.getString("[@id]");
        HttpCrawlerConfig[] crawlers = 
                HttpCrawlerConfigLoader.loadCrawlerConfigs(xml);

        HttpCollectorConfig config = new HttpCollectorConfig(connectorID);
        config.setCrawlerConfigs(crawlers);

        config.setLogsDir(xml.getString("logsDir"));
        config.setProgressDir(xml.getString("progressDir"));

        if (LOG.isInfoEnabled()) {
            LOG.info("Configuration loaded: id=" + connectorID
                    + "; logsDir=" + config.getLogsDir()
                    + "; progressDir=" + config.getProgressDir());
        }
        return config;
    }

    
}
