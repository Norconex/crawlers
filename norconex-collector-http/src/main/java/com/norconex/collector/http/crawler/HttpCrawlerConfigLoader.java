package com.norconex.collector.http.crawler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterConfigLoader;

@SuppressWarnings("nls")
public final class HttpCrawlerConfigLoader {

    private static final Logger LOG = LogManager.getLogger(
            HttpCrawlerConfigLoader.class);
    
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
     * @throws Exception problem parsing crawler configuration
     */
    private static void loadCrawlerConfig(
            HttpCrawlerConfig config, XMLConfiguration node)
            throws Exception {
        //--- General Configuration --------------------------------------------
        if (node == null) {
            LOG.warn("Passing a null configuration for " 
                    + config.getId() + ", skipping.");
            return;
        }
        boolean loadingDefaults = 
                "crawlerDefaults".equalsIgnoreCase(node.getRootElementName());
        
        if (!loadingDefaults) {
            String collectorId = node.getString("[@id]", null);
            if (StringUtils.isBlank(collectorId)) {
                throw new HttpCollectorException(
                        "Collector ID is missing in configuration.");
            }
            config.setId(collectorId);
        }

        config.setUrlNormalizer(ConfigurationUtil.newInstance(
                node, "urlNormalizer", config.getUrlNormalizer()));
        config.setDelayResolver(ConfigurationUtil.newInstance(
                node, "delay", config.getDelayResolver()));
        config.setNumThreads(node.getInt("numThreads", config.getNumThreads()));
        config.setDepth(node.getInt("depth", config.getDepth()));
        config.setWorkDir(new File(node.getString(
                "workDir", config.getWorkDir().toString())));
        config.setKeepDownloads(node.getBoolean(
                "keepDownloads", config.isKeepDownloads()));
        config.setDeleteOrphans(node.getBoolean(
                "deleteOrphans", config.isDeleteOrphans()));
        
        String[] startURLs = node.getStringArray("startURLs.url");
        config.setStartURLs(ArrayUtils.isEmpty(startURLs) 
                ? config.getStartURLs() : startURLs);
        IHttpCrawlerEventListener[] crawlerListeners = 
                loadListeners(node, "crawlerListeners.listener");
        config.setCrawlerListeners(
                crawlerListeners.length == 0 
                        ? config.getCrawlerListeners() : crawlerListeners);
        
        config.setCrawlURLDatabaseFactory(ConfigurationUtil.newInstance(
                node, "crawlURLDatabaseFactory", 
                config.getCrawlURLDatabaseFactory()));
        
        //--- HTTP Initializer -------------------------------------------------
        config.setHttpClientInitializer(ConfigurationUtil.newInstance(
                node, "httpClientInitializer",
                config.getHttpClientInitializer()));

        //--- URL Filters ------------------------------------------------------
        IURLFilter[] urlFilters = loadURLFilters(node, "httpURLFilters.filter");
        config.setURLFilters(
                urlFilters.length == 0 ? config.getURLFilters() : urlFilters);

        //--- RobotsTxt provider -----------------------------------------------
        config.setRobotsTxtProvider(ConfigurationUtil.newInstance(
                node, "robotsTxt", config.getRobotsTxtProvider()));
        config.setIgnoreRobotsTxt(node.getBoolean(
                "robotsTxt[@ignore]", config.isIgnoreRobotsTxt()));
        
        //--- HTTP Headers Fetcher ---------------------------------------------
        config.setHttpHeadersFetcher(ConfigurationUtil.newInstance(
                node, "httpHeadersFetcher", config.getHttpHeadersFetcher()));
        
        //--- HTTP Headers Filters ---------------------------------------------
        IHttpHeadersFilter[] headersFilters = 
                loadHeadersFilters(node, "httpHeadersFilters.filter");
        config.setHttpHeadersFilters(
                headersFilters.length == 0 
                        ? config.getHttpHeadersFilters() : headersFilters);

        //--- HTTP Headers Checksummer -----------------------------------------
        config.setHttpHeadersChecksummer(ConfigurationUtil.newInstance(
        		node, "httpHeadersChecksummer", 
        		config.getHttpHeadersChecksummer()));
        
        //--- HTTP Document Fetcher --------------------------------------------
        config.setHttpDocumentFetcher(ConfigurationUtil.newInstance(
                node, "httpDocumentFetcher",
                config.getHttpDocumentFetcher()));
        
        //--- URL Extractor ----------------------------------------------------
        config.setUrlExtractor(ConfigurationUtil.newInstance(
                node, "urlExtractor", config.getUrlExtractor()));

        //--- Document Filters -------------------------------------------------
        IHttpDocumentFilter[] docFilters = 
                loadDocumentFilters(node, "httpDocumentFilters.filter");
        config.setHttpDocumentfilters(
                docFilters.length == 0 
                        ? config.getHttpDocumentfilters() : docFilters);

        //--- HTTP Pre-Processors ----------------------------------------------
        IHttpDocumentProcessor[] preProcFilters = 
                loadProcessors(node, "httpPreProcessors.processor");
        config.setHttpPreProcessors(
                preProcFilters.length == 0 
                        ? config.getHttpPreProcessors() : preProcFilters);

        //--- IMPORTER ---------------------------------------------------------
        XMLConfiguration importerNode = 
                ConfigurationUtil.getXmlAt(node, "importer");
        ImporterConfig importerConfig = 
                ImporterConfigLoader.loadImporterConfig(importerNode);
        config.setImporterConfig(importerConfig == null
                ? config.getImporterConfig() : importerConfig);
        
        
        //--- HTTP Post-Processors ---------------------------------------------
        IHttpDocumentProcessor[] postProcFilters = 
                loadProcessors(node, "httpPostProcessors.processor");
        config.setHttpPostProcessors(
                postProcFilters.length == 0 
                        ? config.getHttpPostProcessors() : postProcFilters);

        //--- HTTP Document Checksummer -----------------------------------------
        config.setHttpDocumentChecksummer(ConfigurationUtil.newInstance(
                node, "httpDocumentChecksummer",
        		config.getHttpDocumentChecksummer()));

        //--- Document Committers ----------------------------------------------
        config.setCommitter(ConfigurationUtil.newInstance(
                node, "committer", config.getCommitter()));

    }
    
    private static IURLFilter[] loadURLFilters(
            XMLConfiguration node, String xmlPath)
            throws Exception {
        List<IURLFilter> urlFilters = new ArrayList<IURLFilter>();
        List<HierarchicalConfiguration> filterNodes = 
                node.configurationsAt(xmlPath);
        
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IURLFilter urlFilter = ConfigurationUtil.newInstance(filterNode);
            urlFilters.add(urlFilter);
            LOG.info("URL filter loaded: " + urlFilter);
        }
        return urlFilters.toArray(new IURLFilter[]{});
    }
    private static IHttpHeadersFilter[] loadHeadersFilters(
            XMLConfiguration node, String xmlPath)
            throws Exception {
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
            XMLConfiguration node, String xmlPath)
            throws Exception {
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
            XMLConfiguration node, String xmlPath)
            throws Exception {
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
            XMLConfiguration node, String xmlPath)
            throws Exception {
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
