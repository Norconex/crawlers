/* Copyright 2010-2015 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.collector.core.crawler.AbstractCrawlerConfig;
import com.norconex.collector.core.data.store.impl.mapdb.MapDBCrawlDataStoreFactory;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.collector.http.fetch.impl.GenericDocumentFetcher;
import com.norconex.collector.http.robot.IRobotsMetaProvider;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.impl.StandardRobotsMetaProvider;
import com.norconex.collector.http.robot.impl.StandardRobotsTxtProvider;
import com.norconex.collector.http.sitemap.ISitemapResolverFactory;
import com.norconex.collector.http.sitemap.impl.StandardSitemapResolverFactory;
import com.norconex.collector.http.url.ICanonicalLinkDetector;
import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.impl.GenericCanonicalLinkDetector;
import com.norconex.collector.http.url.impl.GenericLinkExtractor;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * HTTP Crawler configuration.
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfig extends AbstractCrawlerConfig {

    private static final Logger LOG = 
            LogManager.getLogger(HttpCrawlerConfig.class);
    
    private int maxDepth = -1;
    private String[] startURLs;
    private String[] urlsFiles;
    
    private boolean ignoreRobotsTxt;
    private boolean ignoreRobotsMeta;
    private boolean ignoreSitemap;
    private boolean keepDownloads;
    private boolean ignoreCanonicalLinks;
    
    private String userAgent;

    private IURLNormalizer urlNormalizer;

    private IDelayResolver delayResolver = new GenericDelayResolver();
    
    private IHttpClientFactory httpClientFactory =
            new GenericHttpClientFactory();
    
    private IHttpDocumentFetcher documentFetcher =
            new GenericDocumentFetcher();

    private ICanonicalLinkDetector canonicalLinkDetector =
            new GenericCanonicalLinkDetector();
    
    private IHttpMetadataFetcher metadataFetcher;

    private ILinkExtractor[] linkExtractors = new ILinkExtractor[] {
            new GenericLinkExtractor()
    };

    private IRobotsTxtProvider robotsTxtProvider =
            new StandardRobotsTxtProvider();
    private IRobotsMetaProvider robotsMetaProvider =
            new StandardRobotsMetaProvider();
    private ISitemapResolverFactory sitemapResolverFactory =
            new StandardSitemapResolverFactory();

    private IMetadataChecksummer metadataChecksummer = 
    		new LastModifiedMetadataChecksummer();
	
    private IHttpDocumentProcessor[] preImportProcessors;
    private IHttpDocumentProcessor[] postImportProcessors;

    public HttpCrawlerConfig() {
        super();
        setCrawlDataStoreFactory(new MapDBCrawlDataStoreFactory());
    }
    public String[] getStartURLs() {
        return ArrayUtils.clone(startURLs);
    }
    public void setStartURLs(String[] startURLs) {
        this.startURLs = ArrayUtils.clone(startURLs);
    }
    public String[] getUrlsFiles() {
        return ArrayUtils.clone(urlsFiles);
    }
    public void setUrlsFiles(String[] urlsFiles) {
        this.urlsFiles = ArrayUtils.clone(urlsFiles);
    }
    public void setMaxDepth(int depth) {
        this.maxDepth = depth;
    }
    public int getMaxDepth() {
        return maxDepth;
    }
    public IHttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }
    public void setHttpClientFactory(IHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }
    public IHttpDocumentFetcher getDocumentFetcher() {
        return documentFetcher;
    }
    public void setDocumentFetcher(
    		IHttpDocumentFetcher documentFetcher) {
        this.documentFetcher = documentFetcher;
    }
    public IHttpMetadataFetcher getMetadataFetcher() {
        return metadataFetcher;
    }
    public void setMetadataFetcher(IHttpMetadataFetcher metadataFetcher) {
        this.metadataFetcher = metadataFetcher;
    }
    /**
     * Gets the canonical link detector.
     * @return the canonical link detector, or <code>null</code> if none
     *         are defined.
     * @since 2.2.0
     */
    public ICanonicalLinkDetector getCanonicalLinkDetector() {
        return canonicalLinkDetector;
    }
    /**
     * Sets the canonical link detector. To disable canonical link detection,
     * either pass a <code>null</code> argument, or invoke 
     * {@link #setIgnoreCanonicalLinks(boolean)} with a <code>true</code> value.
     * @param canonicalLinkDetector the canonical link detector
     * @since 2.2.0
     */
    public void setCanonicalLinkDetector(
            ICanonicalLinkDetector canonicalLinkDetector) {
        this.canonicalLinkDetector = canonicalLinkDetector;
    }
    public ILinkExtractor[] getLinkExtractors() {
        return ArrayUtils.clone(linkExtractors);
    }
    public void setLinkExtractors(ILinkExtractor[] linkExtractors) {
        this.linkExtractors = ArrayUtils.clone(linkExtractors);
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
    public IDelayResolver getDelayResolver() {
        return delayResolver;
    }
    public void setDelayResolver(IDelayResolver delayResolver) {
        this.delayResolver = delayResolver;
    }

    public IHttpDocumentProcessor[] getPreImportProcessors() {
        return ArrayUtils.clone(preImportProcessors);
    }
    public void setPreImportProcessors(
    		IHttpDocumentProcessor[] httpPreProcessors) {
        this.preImportProcessors = ArrayUtils.clone(httpPreProcessors);
    }
    public IHttpDocumentProcessor[] getPostImportProcessors() {
        return ArrayUtils.clone(postImportProcessors);
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
    public boolean isKeepDownloads() {
        return keepDownloads;
    }
    public void setKeepDownloads(boolean keepDownloads) {
        this.keepDownloads = keepDownloads;
    }
    /**
     * Gets the metadata checksummer. Default implementation is 
     * {@link LastModifiedMetadataChecksummer} (since 2.2.0).
     * @return metadata checksummer
     */
    public IMetadataChecksummer getMetadataChecksummer() {
		return metadataChecksummer;
	}
	public void setMetadataChecksummer(
	        IMetadataChecksummer metadataChecksummer) {
		this.metadataChecksummer = metadataChecksummer;
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
    
    /**
     * Whether canonical links found in HTTP headers and in HTML files
     * &lt;head&gt; section should be ignored or processed. When processed
     * (default), URL pages with a canonical URL pointer in them are not 
     * processed.
     * @since 2.2.0
     * @return <code>true</code> if ignoring canonical links
     */
    public boolean isIgnoreCanonicalLinks() {
        return ignoreCanonicalLinks;
    }
    /**
     * Sets whether canonical links found in HTTP headers and in HTML files
     * &lt;head&gt; section should be ignored or processed. If <code>true</code>
     * URL pages with a canonical URL pointer in them are not 
     * processed.
     * @since 2.2.0
     * @param ignoreCanonicalLinks <code>true</code> if ignoring canonical links
     */
    public void setIgnoreCanonicalLinks(boolean ignoreCanonicalLinks) {
        this.ignoreCanonicalLinks = ignoreCanonicalLinks;
    }
    @Override
    protected void saveCrawlerConfigToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeElementString("userAgent", getUserAgent());
            writer.writeElementInteger("maxDepth", getMaxDepth());
            writer.writeElementBoolean("keepDownloads", isKeepDownloads());
            writer.writeElementBoolean(
                    "ignoreCanonicalLinks", isIgnoreCanonicalLinks());
            writer.writeStartElement("startURLs");
            for (String url : getStartURLs()) {
                writer.writeStartElement("url");
                writer.writeCharacters(url);
                writer.writeEndElement();
            }
            for (String path : getUrlsFiles()) {
                writer.writeStartElement("urlsFile");
                writer.writeCharacters(path);
                writer.writeEndElement();
            }
            writer.writeEndElement();
            
            writeObject(out, "urlNormalizer", getUrlNormalizer());
            writeObject(out, "delay", getDelayResolver());
            writeObject(out, "httpClientFactory", getHttpClientFactory());
            writeObject(out, "robotsTxt", 
                    getRobotsTxtProvider(), isIgnoreRobotsTxt());
            writeObject(out, "sitemapResolverFactory", 
                    getSitemapResolverFactory(), isIgnoreSitemap());
            writeObject(out, "canonicalLinkDetector", 
                    getCanonicalLinkDetector());
            writeObject(out, "metadataFetcher", getMetadataFetcher());
            writeObject(out, "metadataChecksummer", getMetadataChecksummer());
            writeObject(out, "documentFetcher", getDocumentFetcher());
            writeObject(out, "robotsMeta", 
                    getRobotsMetaProvider(), isIgnoreRobotsMeta());
            writeArray(out, "linkExtractors", "extractor", getLinkExtractors());
            writeArray(out, "preImportProcessors", 
                    "processor", getPreImportProcessors());
            writeArray(out, "postImportProcessors", 
                    "processor", getPostImportProcessors());
        } catch (XMLStreamException e) {
            throw new IOException(
                    "Could not write to XML config: " + getId(), e);
        }
    }

    @Override
    protected void loadCrawlerConfigFromXML(XMLConfiguration xml) {
        //--- Simple Settings --------------------------------------------------
        loadSimpleSettings(xml);

        //--- HTTP Client Factory ----------------------------------------------
        setHttpClientFactory(ConfigurationUtil.newInstance(xml,
                "httpClientFactory", getHttpClientFactory()));

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

        //--- Canonical Link Detector ------------------------------------------
        setCanonicalLinkDetector(ConfigurationUtil.newInstance(xml,
                "canonicalLinkDetector", getCanonicalLinkDetector()));
        setIgnoreCanonicalLinks(xml.getBoolean("canonicalLinkDetector[@ignore]",
                isIgnoreCanonicalLinks()));

        //--- HTTP Headers Fetcher ---------------------------------------------
        setMetadataFetcher(ConfigurationUtil.newInstance(xml,
                "metadataFetcher", getMetadataFetcher()));

        //--- Metadata Checksummer -----------------------------------------
        setMetadataChecksummer(ConfigurationUtil.newInstance(xml,
                "metadataChecksummer", getMetadataChecksummer()));

        //--- HTTP Document Fetcher --------------------------------------------
        setDocumentFetcher(ConfigurationUtil.newInstance(xml,
                "documentFetcher", getDocumentFetcher()));

        //--- RobotsMeta provider ----------------------------------------------
        setRobotsMetaProvider(ConfigurationUtil.newInstance(xml,
                "robotsMeta", getRobotsMetaProvider()));
        setIgnoreRobotsMeta(xml.getBoolean("robotsMeta[@ignore]",
                isIgnoreRobotsMeta()));

        //--- Link Extractors --------------------------------------------------
        ILinkExtractor[] linkExtractors = loadLinkExtractors(
                xml, "linkExtractors.extractor");
        setLinkExtractors(defaultIfEmpty(linkExtractors, getLinkExtractors()));
        
        //--- HTTP Pre-Processors ----------------------------------------------
        IHttpDocumentProcessor[] preProcFilters = loadProcessors(xml,
                "preImportProcessors.processor");
        setPreImportProcessors(defaultIfEmpty(preProcFilters,
                getPreImportProcessors()));

        //--- HTTP Post-Processors ---------------------------------------------
        IHttpDocumentProcessor[] postProcFilters = loadProcessors(xml,
                "postImportProcessors.processor");
        setPostImportProcessors(defaultIfEmpty(postProcFilters,
                getPostImportProcessors()));

    }

    private void loadSimpleSettings(XMLConfiguration xml) {
        setUserAgent(xml.getString("userAgent", getUserAgent()));
        setUrlNormalizer(ConfigurationUtil.newInstance(
                xml, "urlNormalizer", getUrlNormalizer()));
        setDelayResolver(ConfigurationUtil.newInstance(
                xml, "delay", getDelayResolver()));
        setMaxDepth(xml.getInt("maxDepth", getMaxDepth()));
        setKeepDownloads(xml.getBoolean("keepDownloads", isKeepDownloads()));
        setIgnoreCanonicalLinks(xml.getBoolean(
                "ignoreCanonicalLinks", isIgnoreCanonicalLinks()));
        
        String[] startURLs = xml.getStringArray("startURLs.url");
        setStartURLs(defaultIfEmpty(startURLs, getStartURLs()));
        
        String[] urlsFiles = xml.getStringArray("startURLs.urlsFile");
        setUrlsFiles(defaultIfEmpty(urlsFiles, getUrlsFiles()));
    }

    private IHttpDocumentProcessor[] loadProcessors(
            XMLConfiguration xml, String xmlPath) {
        List<IHttpDocumentProcessor> filters = new ArrayList<>();
        List<HierarchicalConfiguration> filterNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpDocumentProcessor filter = 
                    ConfigurationUtil.newInstance(filterNode);
            filters.add(filter);
            LOG.info("HTTP document processor loaded: " + filter);
        }
        return filters.toArray(new IHttpDocumentProcessor[] {});
    }
    
    private ILinkExtractor[] loadLinkExtractors(
            XMLConfiguration xml, String xmlPath) {
        List<ILinkExtractor> extractors = new ArrayList<>();
        List<HierarchicalConfiguration> extractorNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration extractorNode : extractorNodes) {
            ILinkExtractor extractor = ConfigurationUtil.newInstance(
                    extractorNode, new GenericLinkExtractor());
            if (extractor != null) {
                extractors.add(extractor);
                LOG.info("Link extractor loaded: " + extractor);
            }
        }
        return extractors.toArray(new ILinkExtractor[] {});
    }
    
}
