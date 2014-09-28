/* Copyright 2010-2014 Norconex Inc.
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
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.AbstractCrawlerConfig;
import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.core.crawler.event.ICrawlerEventListener;
import com.norconex.collector.http.checksum.IHttpDocumentChecksummer;
import com.norconex.collector.http.checksum.IHttpHeadersChecksummer;
import com.norconex.collector.http.checksum.impl.DefaultHttpDocumentChecksummer;
import com.norconex.collector.http.checksum.impl.DefaultHttpHeadersChecksummer;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.client.impl.DefaultHttpClientFactory;
import com.norconex.collector.http.data.store.impl.mapdb.DefaultCrawlDataStoreFactory;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.DefaultDelayResolver;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.fetch.impl.DefaultDocumentFetcher;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.robot.IRobotsMetaProvider;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.impl.DefaultRobotsMetaProvider;
import com.norconex.collector.http.robot.impl.DefaultRobotsTxtProvider;
import com.norconex.collector.http.sitemap.ISitemapResolverFactory;
import com.norconex.collector.http.sitemap.impl.DefaultSitemapResolverFactory;
import com.norconex.collector.http.url.IURLExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.impl.DefaultURLExtractor;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterConfigLoader;


/**
 * HTTP Crawler configuration.
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfig extends AbstractCrawlerConfig {

    private static final long serialVersionUID = -3350877963428801802L;
    private static final Logger LOG = 
            LogManager.getLogger(HttpCrawlerConfig.class);
    
    
    private int maxDepth = -1;
    private File workDir = new File("./work");
    private String[] startURLs;
    private int numThreads = 2;
    private int maxURLs = -1;
    
    private boolean ignoreRobotsTxt;
    private boolean ignoreRobotsMeta;
    private boolean ignoreSitemap;
    private boolean keepDownloads;
    private boolean deleteOrphans;
    private String userAgent;

    private IURLNormalizer urlNormalizer;

    private IDelayResolver delayResolver = new DefaultDelayResolver();
    
    private IHttpClientFactory httpClientFactory =
            new DefaultHttpClientFactory();
    
    private IHttpDocumentFetcher httpDocumentFetcher =
            new DefaultDocumentFetcher();

    private IHttpHeadersFetcher httpHeadersFetcher;

    private IURLExtractor urlExtractor = new DefaultURLExtractor();

    private IRobotsTxtProvider robotsTxtProvider =
            new DefaultRobotsTxtProvider();
    private IRobotsMetaProvider robotsMetaProvider =
            new DefaultRobotsMetaProvider();
    private ISitemapResolverFactory sitemapResolverFactory =
            new DefaultSitemapResolverFactory();

    private ImporterConfig importerConfig = new ImporterConfig();

    private IHttpDocumentFilter[] documentfilters;
	
    private IURLFilter[] urlFilters;	

    private IHttpHeadersFilter[] httpHeadersFilters;
    private IHttpHeadersChecksummer httpHeadersChecksummer = 
    		new DefaultHttpHeadersChecksummer();
	
    private IHttpDocumentProcessor[] preImportProcessors;
    private IHttpDocumentProcessor[] postImportProcessors;

    private IHttpDocumentChecksummer httpDocumentChecksummer =
    		new DefaultHttpDocumentChecksummer();
	
    private ICrawlerEventListener[] crawlerListeners;
    
    private ICommitter committer;
    
    public HttpCrawlerConfig() {
        super();
        setReferenceStoreFactory(new DefaultCrawlDataStoreFactory());
    }
    public String[] getStartURLs() {
        return startURLs;
    }
    public void setStartURLs(String[] startURLs) {
        this.startURLs = ArrayUtils.clone(startURLs);
    }
    public void setMaxDepth(int depth) {
        this.maxDepth = depth;
    }
    public int getMaxDepth() {
        return maxDepth;
    }
    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }
    public File getWorkDir() {
        return workDir;
    }
    public int getNumThreads() {
        return numThreads;
    }
    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }
    public int getMaxURLs() {
        return maxURLs;
    }
    public void setMaxURLs(int maxURLs) {
        this.maxURLs = maxURLs;
    }
    public IHttpDocumentFilter[] getHttpDocumentfilters() {
        return documentfilters;
    }
    public void setHttpDocumentfilters(IHttpDocumentFilter[] documentfilters) {
        this.documentfilters = ArrayUtils.clone(documentfilters);
    }
    public IURLFilter[] getURLFilters() {
        return urlFilters;
    }
    public void setURLFilters(IURLFilter[] urlFilters) {
        this.urlFilters = ArrayUtils.clone(urlFilters);
    }
    public ImporterConfig getImporterConfig() {
        return importerConfig;
    }
    public void setImporterConfig(ImporterConfig importerConfig) {
        this.importerConfig = importerConfig;
    }
    public IHttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }
    public void setHttpClientFactory(IHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }
    public IHttpDocumentFetcher getHttpDocumentFetcher() {
        return httpDocumentFetcher;
    }
    public void setHttpDocumentFetcher(
    		IHttpDocumentFetcher httpDocumentFetcher) {
        this.httpDocumentFetcher = httpDocumentFetcher;
    }
    public IHttpHeadersFetcher getHttpHeadersFetcher() {
        return httpHeadersFetcher;
    }
    public void setHttpHeadersFetcher(IHttpHeadersFetcher httpHeadersFetcher) {
        this.httpHeadersFetcher = httpHeadersFetcher;
    }
    public IURLExtractor getUrlExtractor() {
        return urlExtractor;
    }
    public void setUrlExtractor(IURLExtractor urlExtractor) {
        this.urlExtractor = urlExtractor;
    }
    public IRobotsTxtProvider getRobotsTxtProvider() {
        return robotsTxtProvider;
    }
    public void setRobotsTxtProvider(IRobotsTxtProvider robotsTxtProvider) {
        this.robotsTxtProvider = robotsTxtProvider;
    }
    public IURLNormalizer getUrlNormalizer() {
        return urlNormalizer;
    }
    public void setUrlNormalizer(IURLNormalizer urlNormalizer) {
        this.urlNormalizer = urlNormalizer;
    }
    public boolean isDeleteOrphans() {
        return deleteOrphans;
    }
    public void setDeleteOrphans(boolean deleteOrphans) {
        this.deleteOrphans = deleteOrphans;
    }
    public IDelayResolver getDelayResolver() {
        return delayResolver;
    }
    public void setDelayResolver(IDelayResolver delayResolver) {
        this.delayResolver = delayResolver;
    }
    public ICrawlerEventListener[] getCrawlerListeners() {
        return crawlerListeners;
    }
    public void setCrawlerListeners(
            ICrawlerEventListener[] crawlerListeners) {
        this.crawlerListeners = ArrayUtils.clone(crawlerListeners);
    }
    public IHttpHeadersFilter[] getHttpHeadersFilters() {
        return httpHeadersFilters;
    }
    public void setHttpHeadersFilters(IHttpHeadersFilter[] httpHeadersFilters) {
        this.httpHeadersFilters = ArrayUtils.clone(httpHeadersFilters);
    }

    public IHttpDocumentProcessor[] getPreImportProcessors() {
        return preImportProcessors;
    }
    public void setPreImportProcessors(
    		IHttpDocumentProcessor[] httpPreProcessors) {
        this.preImportProcessors = ArrayUtils.clone(httpPreProcessors);
    }
    public IHttpDocumentProcessor[] getPostImportProcessors() {
        return postImportProcessors;
    }
    public void setPostImportProcessors(
    		IHttpDocumentProcessor[] httpPostProcessors) {
        this.postImportProcessors = ArrayUtils.clone(httpPostProcessors);
    }
    public boolean isIgnoreRobotsTxt() {
        return ignoreRobotsTxt;
    }
    public void setIgnoreRobotsTxt(boolean ignoreRobotsTxt) {
        this.ignoreRobotsTxt = ignoreRobotsTxt;
    }
    
    public ICommitter getCommitter() {
        return committer;
    }
    public void setCommitter(ICommitter committer) {
        this.committer = committer;
    }
    public boolean isKeepDownloads() {
        return keepDownloads;
    }
    public void setKeepDownloads(boolean keepDownloads) {
        this.keepDownloads = keepDownloads;
    }
    public IHttpHeadersChecksummer getHttpHeadersChecksummer() {
		return httpHeadersChecksummer;
	}
	public void setHttpHeadersChecksummer(
			IHttpHeadersChecksummer httpHeadersChecksummer) {
		this.httpHeadersChecksummer = httpHeadersChecksummer;
	}
	public IHttpDocumentChecksummer getHttpDocumentChecksummer() {
		return httpDocumentChecksummer;
	}
	public void setHttpDocumentChecksummer(
			IHttpDocumentChecksummer httpDocumentChecksummer) {
		this.httpDocumentChecksummer = httpDocumentChecksummer;
	}
	public boolean isIgnoreRobotsMeta() {
        return ignoreRobotsMeta;
    }
    public void setIgnoreRobotsMeta(boolean ignoreRobotsMeta) {
        this.ignoreRobotsMeta = ignoreRobotsMeta;
    }
    public IRobotsMetaProvider getRobotsMetaProvider() {
        return robotsMetaProvider;
    }
    public void setRobotsMetaProvider(IRobotsMetaProvider robotsMetaProvider) {
        this.robotsMetaProvider = robotsMetaProvider;
    }
    public boolean isIgnoreSitemap() {
        return ignoreSitemap;
    }
    public void setIgnoreSitemap(boolean ignoreSitemap) {
        this.ignoreSitemap = ignoreSitemap;
    }
    public ISitemapResolverFactory getSitemapResolverFactory() {
        return sitemapResolverFactory;
    }
    public void setSitemapResolverFactory(
            ISitemapResolverFactory sitemapResolverFactory) {
        this.sitemapResolverFactory = sitemapResolverFactory;
    }
    public String getUserAgent() {
        return userAgent;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    @Override
    protected void saveCrawlerConfigToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeElementString("userAgent", getUserAgent());
            writer.writeElementInteger("numThreads", getNumThreads());
            writer.writeElementInteger("maxDepth", getMaxDepth());
            writer.writeElementInteger("maxURLs", getMaxURLs());
            writer.writeElementString("workDir", getWorkDir().toString());
            writer.writeElementBoolean("keepDownloads", isKeepDownloads());
            writer.writeElementBoolean("deleteOrphans", isDeleteOrphans());
            writer.writeStartElement("startURLs");
            for (String url : getStartURLs()) {
                writer.writeStartElement("url");
                writer.writeCharacters(url);
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.flush();
            
            writeObject(out, "urlNormalizer", getUrlNormalizer());
            writeObject(out, "delay", getDelayResolver());
            writeArray(out, "crawlerListeners", "listener", 
                    getCrawlerListeners());
            writeObject(out, "httpClientFactory", getHttpClientFactory());
            writeArray(out, "httpURLFilters", "filter", getURLFilters());
            writeObject(out, "robotsTxt", 
                    getRobotsTxtProvider(), isIgnoreRobotsTxt());
            writeObject(out, "sitemapResolverFactory", 
                    getSitemapResolverFactory(), isIgnoreSitemap());
            writeObject(out, "httpHeadersFetcher", getHttpHeadersFetcher());
            writeArray(out, "httpHeadersFilters", 
                    "filter", getHttpHeadersFilters());
            writeObject(out, "httpHeadersChecksummer", 
                    getHttpHeadersChecksummer());
            writeObject(out, "httpDocumentFetcher", getHttpDocumentFetcher());
            writeObject(out, "robotsMeta", 
                    getRobotsMetaProvider(), isIgnoreRobotsMeta());
            writeObject(out, "urlExtractor", getUrlExtractor());
            writeArray(out, "httpDocumentFilters", 
                    "filter", getHttpDocumentfilters());
            writeArray(out, "preImportProcessors", 
                    "processor", getPreImportProcessors());
            writeObject(out, "importer", getImporterConfig());
            writeArray(out, "postImportProcessors", 
                    "processor", getPostImportProcessors());
            writeObject(out, "httpDocumentChecksummer", 
                    getHttpDocumentChecksummer());
            writeObject(out, "committer", getCommitter());
        } catch (XMLStreamException e) {
            throw new IOException(
                    "Could not write to XML config: " + getId(), e);
        }
    }

    private void writeArray(Writer out, String listTagName, 
            String objectTagName, Object[] array) throws IOException {
        if (ArrayUtils.isEmpty(array)) {
            return;
        }
        out.write("<" + listTagName + ">"); 
        for (Object obj : array) {
            writeObject(out, objectTagName, obj);
        }
        out.write("</" + listTagName + ">"); 
        out.flush();
    }
    private void writeObject(
            Writer out, String tagName, Object object) throws IOException {
        writeObject(out, tagName, object, false);
    }
    private void writeObject(
            Writer out, String tagName, Object object, boolean ignore) 
                    throws IOException {
        if (object == null) {
            if (ignore) {
                out.write("<" + tagName + " ignore=\"" + ignore + "\" />");
            }
            return;
        }
        StringWriter w = new StringWriter();
        if (object instanceof IXMLConfigurable) {
            ((IXMLConfigurable) object).saveToXML(w);
        } else {
            w.write("<" + tagName + " class=\"" 
                    + object.getClass().getCanonicalName() + "\" />");
        }
        String xml = w.toString();
        if (ignore) {
            xml = xml.replace("<" + tagName + " class=\"" , 
                    "<" + tagName + " ignore=\"true\" class=\"" );
        }
        out.write(xml);
        out.flush();
    }
    
    @Override
    protected void loadCrawlerConfigFromXML(XMLConfiguration xml) {
        loadSimpleSettings(xml);

        //--- Crawler Listeners ------------------------------------------------
        ICrawlerEventListener[] crawlerListeners = loadListeners(xml,
                "crawlerListeners.listener");
        setCrawlerListeners(defaultIfEmpty(crawlerListeners,
                getCrawlerListeners()));

        //--- HTTP Client Factory ----------------------------------------------
        setHttpClientFactory(ConfigurationUtil.newInstance(xml,
                "httpClientFactory", getHttpClientFactory()));

        //--- URL Filters ------------------------------------------------------
        IURLFilter[] urlFilters = loadURLFilters(xml, "httpURLFilters.filter");
        setURLFilters(defaultIfEmpty(urlFilters, getURLFilters()));

        //--- RobotsTxt provider -----------------------------------------------
        setRobotsTxtProvider(ConfigurationUtil.newInstance(xml,
                "robotsTxt", getRobotsTxtProvider()));
        setIgnoreRobotsTxt(xml.getBoolean("robotsTxt[@ignore]",
                isIgnoreRobotsTxt()));

        //--- Sitemap Resolver -------------------------------------------------
        setSitemapResolverFactory(ConfigurationUtil.newInstance(xml,
                "sitemap", getSitemapResolverFactory()));
        setIgnoreSitemap(xml.getBoolean("sitemap[@ignore]",
                isIgnoreSitemap()));

        //--- HTTP Headers Fetcher ---------------------------------------------
        setHttpHeadersFetcher(ConfigurationUtil.newInstance(xml,
                "httpHeadersFetcher", getHttpHeadersFetcher()));

        //--- HTTP Headers Filters ---------------------------------------------
        IHttpHeadersFilter[] headersFilters = loadHeadersFilters(xml,
                "httpHeadersFilters.filter");
        setHttpHeadersFilters(defaultIfEmpty(headersFilters,
                getHttpHeadersFilters()));

        //--- HTTP Headers Checksummer -----------------------------------------
        setHttpHeadersChecksummer(ConfigurationUtil.newInstance(xml,
                "httpHeadersChecksummer", getHttpHeadersChecksummer()));

        //--- HTTP Document Fetcher --------------------------------------------
        setHttpDocumentFetcher(ConfigurationUtil.newInstance(xml,
                "httpDocumentFetcher", getHttpDocumentFetcher()));

        //--- RobotsMeta provider ----------------------------------------------
        setRobotsMetaProvider(ConfigurationUtil.newInstance(xml,
                "robotsMeta", getRobotsMetaProvider()));
        setIgnoreRobotsMeta(xml.getBoolean("robotsMeta[@ignore]",
                isIgnoreRobotsMeta()));

        //--- URL Extractor ----------------------------------------------------
        setUrlExtractor(ConfigurationUtil.newInstance(xml,
                "urlExtractor", getUrlExtractor()));

        //--- Document Filters -------------------------------------------------
        IHttpDocumentFilter[] docFilters = loadDocumentFilters(xml,
                "httpDocumentFilters.filter");
        setHttpDocumentfilters(defaultIfEmpty(docFilters,
                getHttpDocumentfilters()));

        //--- HTTP Pre-Processors ----------------------------------------------
        IHttpDocumentProcessor[] preProcFilters = loadProcessors(xml,
                "preImportProcessors.processor");
        setPreImportProcessors(defaultIfEmpty(preProcFilters,
                getPreImportProcessors()));

        //--- IMPORTER ---------------------------------------------------------
        XMLConfiguration importerNode = ConfigurationUtil.getXmlAt(xml,
                "importer");
        ImporterConfig importerConfig = ImporterConfigLoader
                .loadImporterConfig(importerNode);
        setImporterConfig(ObjectUtils.defaultIfNull(importerConfig,
                getImporterConfig()));

        //--- HTTP Post-Processors ---------------------------------------------
        IHttpDocumentProcessor[] postProcFilters = loadProcessors(xml,
                "postImportProcessors.processor");
        setPostImportProcessors(defaultIfEmpty(postProcFilters,
                getPostImportProcessors()));

        //--- HTTP Document Checksummer ----------------------------------------
        setHttpDocumentChecksummer(ConfigurationUtil.newInstance(xml,
                "httpDocumentChecksummer", getHttpDocumentChecksummer()));

        //--- Document Committers ----------------------------------------------
        setCommitter(ConfigurationUtil.newInstance(xml, "committer",
                getCommitter()));
    }

    private void loadSimpleSettings(XMLConfiguration xml) {
        setUserAgent(xml.getString("userAgent", getUserAgent()));
        setUrlNormalizer(ConfigurationUtil.newInstance(
                xml, "urlNormalizer", getUrlNormalizer()));
        setDelayResolver(ConfigurationUtil.newInstance(
                xml, "delay", getDelayResolver()));
        setNumThreads(xml.getInt("numThreads", getNumThreads()));
        setMaxDepth(xml.getInt("maxDepth", getMaxDepth()));
        setMaxURLs(xml.getInt("maxURLs", getMaxURLs()));
        setWorkDir(new File(xml.getString("workDir", getWorkDir().toString())));
        setKeepDownloads(xml.getBoolean("keepDownloads", isKeepDownloads()));
        setDeleteOrphans(xml.getBoolean("deleteOrphans", isDeleteOrphans()));

        String[] startURLs = xml.getStringArray("startURLs.url");
        setStartURLs(defaultIfEmpty(startURLs, getStartURLs()));
    }

    // TODO consider moving to Norconex Commons Lang
    private <T> T[] defaultIfEmpty(T[] array, T[] defaultArray) {
        if (ArrayUtils.isEmpty(array)) {
            return defaultArray;
        }
        return array;
    }

    private IURLFilter[] loadURLFilters(XMLConfiguration xml, String xmlPath) {
        List<IURLFilter> urlFilters = new ArrayList<>();
        List<HierarchicalConfiguration> filterNodes = 
                xml.configurationsAt(xmlPath);
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
        return urlFilters.toArray(new IURLFilter[] {});
    }

    private IHttpHeadersFilter[] loadHeadersFilters(XMLConfiguration xml,
            String xmlPath) {
        List<IHttpHeadersFilter> filters = new ArrayList<>();
        List<HierarchicalConfiguration> filterNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpHeadersFilter filter = ConfigurationUtil
                    .newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP headers filter loaded: " + filter);
        }
        return filters.toArray(new IHttpHeadersFilter[] {});
    }

    private IHttpDocumentFilter[] loadDocumentFilters(XMLConfiguration xml,
            String xmlPath) {
        List<IHttpDocumentFilter> filters = new ArrayList<>();
        List<HierarchicalConfiguration> filterNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpDocumentFilter filter = ConfigurationUtil
                    .newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP document filter loaded: " + filter);
        }
        return filters.toArray(new IHttpDocumentFilter[] {});
    }

    private ICrawlerEventListener[] loadListeners(XMLConfiguration xml,
            String xmlPath) {
        List<ICrawlerEventListener> listeners = new ArrayList<>();
        List<HierarchicalConfiguration> listenerNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration listenerNode : listenerNodes) {
            ICrawlerEventListener listener = ConfigurationUtil
                    .newInstance(listenerNode);
            listeners.add(listener);
            LOG.info("HTTP Crawler event listener loaded: " + listener);
        }
        return listeners.toArray(new ICrawlerEventListener[] {});
    }

    private IHttpDocumentProcessor[] loadProcessors(XMLConfiguration xml,
            String xmlPath) {
        List<IHttpDocumentProcessor> filters = new ArrayList<>();
        List<HierarchicalConfiguration> filterNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpDocumentProcessor filter = ConfigurationUtil
                    .newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP document processor loaded: " + filter);
        }
        return filters.toArray(new IHttpDocumentProcessor[] {});
    }

    @Override
    public ICrawlerConfig safeClone() {
        try {
            return (HttpCrawlerConfig) BeanUtils.cloneBean(this);
        } catch (Exception e) {
            throw new CollectorException(
                    "Cannot clone crawler configuration.", e);
        }
    }
}
