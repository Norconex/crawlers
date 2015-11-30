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
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
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
import com.norconex.collector.http.url.impl.GenericURLNormalizer;
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
    private String[] startURLsFiles;
    private String[] startSitemapURLs;
    
    private boolean ignoreRobotsTxt;
    private boolean ignoreRobotsMeta;
    private boolean ignoreSitemap;
    private boolean keepDownloads;
    private boolean ignoreCanonicalLinks;
    
    private String userAgent;

    //TODO make configurable via interface instead?
    private final URLCrawlScopeStrategy urlCrawlScopeStrategy = 
            new URLCrawlScopeStrategy();
    
    private IURLNormalizer urlNormalizer = new GenericURLNormalizer();

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
    public void setStartURLs(String... startURLs) {
        this.startURLs = ArrayUtils.clone(startURLs);
    }
    /**
     * Gets the file paths of seed files containing URLs to be used as
     * "start URLs".
     * @deprecated Since 2.3.0, use {@link #getStartURLsFiles()} instead.
     * @return file paths of seed files containing URLs
     */
    @Deprecated
    public String[] getUrlsFiles() {
        return getStartURLsFiles();
    }
    /**
     * Sets the file paths of seed files containing URLs to be used as
     * "start URLs".
     * @deprecated Since 2.3.0, use {@link #setStartURLsFiles(String...)} 
     *             instead.
     * @param urlsFiles file paths of seed files containing URLs
     */
    @Deprecated
    public void setUrlsFiles(String... urlsFiles) {
        setStartURLsFiles(urlsFiles);
    }
    /**
     * Gets the file paths of seed files containing URLs to be used as
     * "start URLs".
     * @return file paths of seed files containing URLs
     * @since 2.3.0
     */
    public String[] getStartURLsFiles() {
        return startURLsFiles;
    }
    /**
     * Sets the file paths of seed files containing URLs to be used as
     * "start URLs".
     * @param startURLsFiles file paths of seed files containing URLs
     * @since 2.3.0
     */
    public void setStartURLsFiles(String... startURLsFiles) {
        this.startURLsFiles = ArrayUtils.clone(startURLsFiles);
    }
    /**
     * Gets sitemap URLs to be used as starting points for crawling.
     * @return sitemap URLs
     * @since 2.3.0
     */
    public String[] getStartSitemapURLs() {
        return startSitemapURLs;
    }
    /**
     * Sets the sitemap URLs used as starting points for crawling.
     * @param startSitemapURLs sitemap URLs
     * @since 2.3.0
     */
    public void setStartSitemapURLs(String... startSitemapURLs) {
        this.startSitemapURLs = ArrayUtils.clone(startSitemapURLs);
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
    		IHttpDocumentProcessor... httpPreProcessors) {
        this.preImportProcessors = ArrayUtils.clone(httpPreProcessors);
    }
    public IHttpDocumentProcessor[] getPostImportProcessors() {
        return ArrayUtils.clone(postImportProcessors);
    }
    public void setPostImportProcessors(
    		IHttpDocumentProcessor... httpPostProcessors) {
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
    /**
     * Whether to ignore sitemap detection and resolving for URLs processed.
     * Sitemaps specified as start URLs 
     * ({@link #getStartSitemapURLs()}) are never ignored.
     * @return <code>true</code> to ignore sitemaps
     */
    public boolean isIgnoreSitemap() {
        return ignoreSitemap;
    }
    /**
     * Sets whether to ignore sitemap detection and resolving for URLs 
     * processed. Sitemaps specified as start URLs 
     * ({@link #getStartSitemapURLs()}) are never ignored.
     * @param ignoreSitemap <code>true</code> to ignore sitemaps
     */
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

    public URLCrawlScopeStrategy getURLCrawlScopeStrategy() {
        return urlCrawlScopeStrategy;
    }
    
    @Override
    protected void saveCrawlerConfigToXML(Writer out) throws IOException {
        try {
            out.flush();
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeElementString("userAgent", getUserAgent());
            writer.writeElementInteger("maxDepth", getMaxDepth());
            writer.writeElementBoolean("keepDownloads", isKeepDownloads());

            writer.writeStartElement("startURLs");
            writer.writeAttributeBoolean("stayOnProtocol", 
                    urlCrawlScopeStrategy.isStayOnProtocol());
            writer.writeAttributeBoolean("stayOnDomain", 
                    urlCrawlScopeStrategy.isStayOnDomain());
            writer.writeAttributeBoolean("stayOnPort", 
                    urlCrawlScopeStrategy.isStayOnPort());
            String[] urls = getStartURLs();
            if (urls != null) {
                for (String url : urls) {
                    writer.writeElementString("url", url);
                }
            }
            String[] urlsFiles = getStartURLsFiles();
            if (urlsFiles != null) {
                for (String path : urlsFiles) {
                    writer.writeElementString("urlsFile", path);
                }
            }
            String[] sitemapURLs = getStartSitemapURLs();
            if (sitemapURLs != null) {
                for (String sitemapURL : sitemapURLs) {
                    writer.writeElementString("sitemap", sitemapURL);
                }
            }
            writer.writeEndElement();
            writer.flush();
            
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
        ISitemapResolverFactory sitemapFactory = ConfigurationUtil.newInstance(
                xml, "sitemapResolverFactory", getSitemapResolverFactory());
        setIgnoreSitemap(xml.getBoolean(
                "sitemapResolverFactory[@ignore]", isIgnoreSitemap()));
        
        List<HierarchicalConfiguration> maps = xml.configurationsAt("sitemap");
        if (sitemapFactory == null && maps != null && !maps.isEmpty()) {
            SubnodeConfiguration xmlSitemap = 
                    xml.configurationAt("sitemap");
            if (xmlSitemap != null) {
                LOG.warn("The <sitemap ...> tag used as a crawler setting "
                        + "is deprecated, use <sitemapResolverFactory...> "
                        + "instead. The <sitemap> tag can now be used as a "
                        + "start URL.");
                sitemapFactory = ConfigurationUtil.newInstance(xml, "sitemap");
                setIgnoreSitemap(
                        xml.getBoolean("sitemap[@ignore]", isIgnoreSitemap()));
            }
        }
        if (sitemapFactory == null) {
            sitemapFactory = getSitemapResolverFactory();
        }

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
        urlCrawlScopeStrategy.setStayOnProtocol(xml.getBoolean(
                "startURLs[@stayOnProtocol]", 
                urlCrawlScopeStrategy.isStayOnProtocol()));
        urlCrawlScopeStrategy.setStayOnDomain(xml.getBoolean(
                "startURLs[@stayOnDomain]", 
                urlCrawlScopeStrategy.isStayOnDomain()));
        urlCrawlScopeStrategy.setStayOnPort(xml.getBoolean(
                "startURLs[@stayOnPort]", 
                urlCrawlScopeStrategy.isStayOnPort()));
        
        String[] startURLs = xml.getStringArray("startURLs.url");
        setStartURLs(defaultIfEmpty(startURLs, getStartURLs()));
        
        String[] urlsFiles = xml.getStringArray("startURLs.urlsFile");
        setStartURLsFiles(defaultIfEmpty(urlsFiles, getStartURLsFiles()));

        String[] sitemapURLs = xml.getStringArray("startURLs.sitemap");
        setStartSitemapURLs(defaultIfEmpty(sitemapURLs, getStartSitemapURLs()));
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
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof HttpCrawlerConfig)) {
            return false;
        }
        HttpCrawlerConfig castOther = (HttpCrawlerConfig) other;
        return new EqualsBuilder()
                .appendSuper(super.equals(castOther))
                .append(maxDepth, castOther.maxDepth)
                .append(startURLs, castOther.startURLs)
                .append(startURLsFiles, castOther.startURLsFiles)
                .append(startSitemapURLs, castOther.startSitemapURLs)
                .append(ignoreRobotsTxt, castOther.ignoreRobotsTxt)
                .append(ignoreRobotsMeta, castOther.ignoreRobotsMeta)
                .append(ignoreSitemap, castOther.ignoreSitemap)
                .append(keepDownloads, castOther.keepDownloads)
                .append(ignoreCanonicalLinks, castOther.ignoreCanonicalLinks)
                .append(userAgent, castOther.userAgent)
                .append(urlCrawlScopeStrategy, castOther.urlCrawlScopeStrategy)
                .append(urlNormalizer, castOther.urlNormalizer)
                .append(delayResolver, castOther.delayResolver)
                .append(httpClientFactory, castOther.httpClientFactory)
                .append(documentFetcher, castOther.documentFetcher)
                .append(canonicalLinkDetector, castOther.canonicalLinkDetector)
                .append(metadataFetcher, castOther.metadataFetcher)
                .append(linkExtractors, castOther.linkExtractors)
                .append(robotsTxtProvider, castOther.robotsTxtProvider)
                .append(robotsMetaProvider, castOther.robotsMetaProvider)
                .append(sitemapResolverFactory, 
                        castOther.sitemapResolverFactory)
                .append(metadataChecksummer, castOther.metadataChecksummer)
                .append(preImportProcessors, castOther.preImportProcessors)
                .append(postImportProcessors, castOther.postImportProcessors)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(maxDepth)
                .append(startURLs)
                .append(startURLsFiles)
                .append(startSitemapURLs)
                .append(ignoreRobotsTxt)
                .append(ignoreRobotsMeta)
                .append(ignoreSitemap)
                .append(keepDownloads)
                .append(ignoreCanonicalLinks)
                .append(userAgent)
                .append(urlCrawlScopeStrategy)
                .append(urlNormalizer)
                .append(delayResolver)
                .append(httpClientFactory)
                .append(documentFetcher)
                .append(canonicalLinkDetector)
                .append(metadataFetcher)
                .append(linkExtractors)
                .append(robotsTxtProvider)
                .append(robotsMetaProvider)
                .append(sitemapResolverFactory)
                .append(metadataChecksummer)
                .append(preImportProcessors)
                .append(postImportProcessors)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("maxDepth", maxDepth)
                .append("startURLs", startURLs)
                .append("startURLsFiles", startURLsFiles)
                .append("startSitemapURLs", startSitemapURLs)
                .append("ignoreRobotsTxt", ignoreRobotsTxt)
                .append("ignoreRobotsMeta", ignoreRobotsMeta)
                .append("ignoreSitemap", ignoreSitemap)
                .append("keepDownloads", keepDownloads)
                .append("ignoreCanonicalLinks", ignoreCanonicalLinks)
                .append("userAgent", userAgent)
                .append("urlCrawlScopeStrategy", urlCrawlScopeStrategy)
                .append("urlNormalizer", urlNormalizer)
                .append("delayResolver", delayResolver)
                .append("httpClientFactory", httpClientFactory)
                .append("documentFetcher", documentFetcher)
                .append("canonicalLinkDetector", canonicalLinkDetector)
                .append("metadataFetcher", metadataFetcher)
                .append("linkExtractors", linkExtractors)
                .append("robotsTxtProvider", robotsTxtProvider)
                .append("robotsMetaProvider", robotsMetaProvider)
                .append("sitemapResolverFactory", sitemapResolverFactory)
                .append("metadataChecksummer", metadataChecksummer)
                .append("preImportProcessors", preImportProcessors)
                .append("postImportProcessors", postImportProcessors)
                .toString();
    }  
}
