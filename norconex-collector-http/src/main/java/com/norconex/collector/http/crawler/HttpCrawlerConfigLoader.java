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
package com.norconex.collector.http.crawler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterConfigLoader;

/**
 * HTTP Crawler configuration loader.
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public final class HttpCrawlerConfigLoader {

    private static final Logger LOG = LogManager.getLogger(
            HttpCrawlerConfigLoader.class);
    
    private HttpCrawlerConfigLoader() {
        super();
    }

    public static HttpCrawlerConfig[] loadCrawlerConfigs(
            File configFile, File configVariables) {
        ConfigurationLoader configLoader = new ConfigurationLoader();
        XMLConfiguration xml = configLoader.loadXML(
                configFile, configVariables);
        return loadCrawlerConfigs(xml);
    }
    
    public static HttpCrawlerConfig[] loadCrawlerConfigs(
            HierarchicalConfiguration xml) {
        try {
            XMLConfiguration defaults = 
                    ConfigurationUtil.getXmlAt(xml, "crawlerDefaults");
            HttpCrawlerConfig defaultConfig = new HttpCrawlerConfig();
            if (defaults != null) {
                loadCrawlerConfig(defaultConfig, defaults);
            }
            
            List<HierarchicalConfiguration> nodes = 
                    xml.configurationsAt("crawlers.crawler");
            List<HttpCrawlerConfig> configs = new ArrayList<HttpCrawlerConfig>();
            for (HierarchicalConfiguration node : nodes) {
                HttpCrawlerConfig config = 
                        (HttpCrawlerConfig) defaultConfig.clone();
                loadCrawlerConfig(config, new XMLConfiguration(node));
                configs.add(config);
            }
            return configs.toArray(new HttpCrawlerConfig[]{});
        } catch (Exception e) {
            throw new HttpCollectorException(
                    "Cannot load crawler configurations.", e);
        }
    }

    /**
     * Loads a crawler configuration, which can be either the default
     * crawler or real crawler configuration instances
     * (keeping defaults).
     * @param config crawler configuration to populate/overwrite
     * @param node the node representing the crawler configuration.
     * @throws HttpCollectorException problem parsing crawler configuration
     */
    @SuppressWarnings("deprecation")
    private static void loadCrawlerConfig(
            HttpCrawlerConfig config, XMLConfiguration node) {
        //--- General Configuration --------------------------------------------
        if (node == null) {
            LOG.warn("Passing a null configuration for " 
                    + config.getId() + ", skipping.");
            return;
        }
        boolean loadingDefaults = 
                "crawlerDefaults".equalsIgnoreCase(node.getRootElementName());
        
        if (!loadingDefaults) {
            String crawlerId = node.getString("[@id]", null);
            if (StringUtils.isBlank(crawlerId)) {
                throw new HttpCollectorException(
                        "Crawler ID is missing in configuration.");
            }
            config.setId(crawlerId);
        }

        config.setUserAgent(node.getString("userAgent", config.getUserAgent()));
        config.setUrlNormalizer(ConfigurationUtil.newInstance(
                node, "urlNormalizer", config.getUrlNormalizer()));
        config.setDelayResolver(ConfigurationUtil.newInstance(
                node, "delay", config.getDelayResolver()));
        config.setNumThreads(node.getInt("numThreads", config.getNumThreads()));
        config.setMaxDepth(node.getInt("maxDepth", config.getMaxDepth()));
        config.setMaxURLs(node.getInt("maxURLs", config.getMaxURLs()));
        config.setWorkDir(new File(node.getString(
                "workDir", config.getWorkDir().toString())));
        config.setKeepDownloads(node.getBoolean(
                "keepDownloads", config.isKeepDownloads()));
        config.setDeleteOrphans(node.getBoolean(
                "deleteOrphans", config.isDeleteOrphans()));
        
        String[] startURLs = node.getStringArray("startURLs.url");
        config.setStartURLs(defaultIfEmpty(startURLs, config.getStartURLs()));

        IHttpCrawlerEventListener[] crawlerListeners = 
                loadListeners(node, "crawlerListeners.listener");
        config.setCrawlerListeners(
                defaultIfEmpty(crawlerListeners, config.getCrawlerListeners()));
        
        config.setCrawlURLDatabaseFactory(ConfigurationUtil.newInstance(
                node, "crawlURLDatabaseFactory", 
                config.getCrawlURLDatabaseFactory()));
        
        //--- HTTP Initializer -------------------------------------------------
        config.setHttpClientInitializer(ConfigurationUtil.newInstance(
                node, "httpClientInitializer",
                config.getHttpClientInitializer()));

        //--- HTTP Client Factory ----------------------------------------------
        config.setHttpClientFactory(ConfigurationUtil.newInstance(
                node, "httpClientFactory", config.getHttpClientFactory()));
        
        //--- URL Filters ------------------------------------------------------
        IURLFilter[] urlFilters = loadURLFilters(node, "httpURLFilters.filter");
        config.setURLFilters(
                defaultIfEmpty(urlFilters, config.getURLFilters()));

        //--- RobotsTxt provider -----------------------------------------------
        config.setRobotsTxtProvider(ConfigurationUtil.newInstance(
                node, "robotsTxt", config.getRobotsTxtProvider()));
        config.setIgnoreRobotsTxt(node.getBoolean(
                "robotsTxt[@ignore]", config.isIgnoreRobotsTxt()));
        
        //--- Sitemap Resolver -------------------------------------------------
        config.setSitemapResolver(ConfigurationUtil.newInstance(
                node, "sitemap", config.getSitemapResolver()));
        config.setIgnoreSitemap(node.getBoolean(
                "sitemap[@ignore]", config.isIgnoreSitemap()));
        
        //--- HTTP Headers Fetcher ---------------------------------------------
        config.setHttpHeadersFetcher(ConfigurationUtil.newInstance(
                node, "httpHeadersFetcher", config.getHttpHeadersFetcher()));
        
        //--- HTTP Headers Filters ---------------------------------------------
        IHttpHeadersFilter[] headersFilters = 
                loadHeadersFilters(node, "httpHeadersFilters.filter");
        config.setHttpHeadersFilters(
                defaultIfEmpty(headersFilters, config.getHttpHeadersFilters()));

        //--- HTTP Headers Checksummer -----------------------------------------
        config.setHttpHeadersChecksummer(ConfigurationUtil.newInstance(
        		node, "httpHeadersChecksummer", 
        		config.getHttpHeadersChecksummer()));
        
        //--- HTTP Document Fetcher --------------------------------------------
        config.setHttpDocumentFetcher(ConfigurationUtil.newInstance(
                node, "httpDocumentFetcher",
                config.getHttpDocumentFetcher()));
        
        //--- RobotsMeta provider ----------------------------------------------
        config.setRobotsMetaProvider(ConfigurationUtil.newInstance(
                node, "robotsMeta", config.getRobotsMetaProvider()));
        config.setIgnoreRobotsMeta(node.getBoolean(
                "robotsMeta[@ignore]", config.isIgnoreRobotsMeta()));
        
        //--- URL Extractor ----------------------------------------------------
        config.setUrlExtractor(ConfigurationUtil.newInstance(
                node, "urlExtractor", config.getUrlExtractor()));

        //--- Document Filters -------------------------------------------------
        IHttpDocumentFilter[] docFilters = 
                loadDocumentFilters(node, "httpDocumentFilters.filter");
        config.setHttpDocumentfilters(
                defaultIfEmpty(docFilters, config.getHttpDocumentfilters()));

        //--- HTTP Pre-Processors ----------------------------------------------
        IHttpDocumentProcessor[] preProcFilters = 
                loadProcessors(node, "preImportProcessors.processor");
        config.setPreImportProcessors(defaultIfEmpty(
                preProcFilters, config.getPreImportProcessors()));

        //--- IMPORTER ---------------------------------------------------------
        XMLConfiguration importerNode = 
                ConfigurationUtil.getXmlAt(node, "importer");
        ImporterConfig importerConfig = 
                ImporterConfigLoader.loadImporterConfig(importerNode);
        config.setImporterConfig(ObjectUtils.defaultIfNull(
                importerConfig, config.getImporterConfig()));
        
        //--- HTTP Post-Processors ---------------------------------------------
        IHttpDocumentProcessor[] postProcFilters = 
                loadProcessors(node, "postImportProcessors.processor");
        config.setPostImportProcessors(defaultIfEmpty(
                postProcFilters, config.getPostImportProcessors()));

        //--- HTTP Document Checksummer -----------------------------------------
        config.setHttpDocumentChecksummer(ConfigurationUtil.newInstance(
                node, "httpDocumentChecksummer",
        		config.getHttpDocumentChecksummer()));

        //--- Document Committers ----------------------------------------------
        config.setCommitter(ConfigurationUtil.newInstance(
                node, "committer", config.getCommitter()));
    }
    
    //TODO consider moving to Norconex Commons Lang
    private static <T> T[] defaultIfEmpty(T[] array, T[] defaultArray) {
        if (ArrayUtils.isEmpty(array)) {
            return defaultArray;
        }
        return array;
    }
    
    private static IURLFilter[] loadURLFilters(
            XMLConfiguration node, String xmlPath) {
        List<IURLFilter> urlFilters = new ArrayList<IURLFilter>();
        List<HierarchicalConfiguration> filterNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IURLFilter urlFilter = ConfigurationUtil.newInstance(filterNode);
            if (urlFilter != null) {
                urlFilters.add(urlFilter);
                LOG.info("URL filter loaded: " + urlFilter);
            } else {
                LOG.error("Problem loading filter, "
                        + "please check for other log messages.");
            }
        }
        return urlFilters.toArray(new IURLFilter[]{});
    }
    private static IHttpHeadersFilter[] loadHeadersFilters(
            XMLConfiguration node, String xmlPath) {
        List<IHttpHeadersFilter> filters = 
                new ArrayList<IHttpHeadersFilter>();
        List<HierarchicalConfiguration> filterNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpHeadersFilter filter = 
                    ConfigurationUtil.newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP headers filter loaded: " + filter);
        }
        return filters.toArray(new IHttpHeadersFilter[]{});
    }
    private static IHttpDocumentFilter[] loadDocumentFilters(
            XMLConfiguration node, String xmlPath) {
        List<IHttpDocumentFilter> filters = 
                new ArrayList<IHttpDocumentFilter>();
        List<HierarchicalConfiguration> filterNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpDocumentFilter filter = 
                    ConfigurationUtil.newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP document filter loaded: " + filter);
        }
        return filters.toArray(new IHttpDocumentFilter[]{});
    }
    private static IHttpCrawlerEventListener[] loadListeners(
            XMLConfiguration node, String xmlPath) {
        List<IHttpCrawlerEventListener> listeners = 
                new ArrayList<IHttpCrawlerEventListener>();
        List<HierarchicalConfiguration> listenerNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration listenerNode : listenerNodes) {
            IHttpCrawlerEventListener listener = 
                    ConfigurationUtil.newInstance(listenerNode);
            listeners.add(listener);
            LOG.info("HTTP Crawler event listener loaded: " + listener);
        }
        return listeners.toArray(new IHttpCrawlerEventListener[]{});
    }
    private static IHttpDocumentProcessor[] loadProcessors(
            XMLConfiguration node, String xmlPath) {
        List<IHttpDocumentProcessor> filters = 
                new ArrayList<IHttpDocumentProcessor>();
        List<HierarchicalConfiguration> filterNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpDocumentProcessor filter = 
                    ConfigurationUtil.newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP document processor loaded: " + filter);
        }
        return filters.toArray(new IHttpDocumentProcessor[]{});
    }
}
