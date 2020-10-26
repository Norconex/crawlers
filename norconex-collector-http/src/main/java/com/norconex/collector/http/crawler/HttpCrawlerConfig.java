/* Copyright 2010-2020 Norconex Inc.
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
import java.io.StringWriter;
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
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.collector.http.fetch.impl.GenericDocumentFetcher;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
import com.norconex.collector.http.recrawl.IRecrawlableResolver;
import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver;
import com.norconex.collector.http.redirect.IRedirectURLProvider;
import com.norconex.collector.http.redirect.impl.GenericRedirectURLProvider;
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
import com.norconex.commons.lang.config.XMLConfigurationUtil;
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
    private IStartURLsProvider[] startURLsProviders;

    private boolean ignoreRobotsTxt;
    private boolean ignoreRobotsMeta;
    private boolean ignoreSitemap;
    private boolean keepDownloads;
    private boolean ignoreCanonicalLinks;
	private boolean keepOutOfScopeLinks;
	private boolean skipMetaFetcherOnBadStatus;

    private String userAgent;

    private URLCrawlScopeStrategy urlCrawlScopeStrategy =
            new URLCrawlScopeStrategy();

    private IURLNormalizer urlNormalizer = new GenericURLNormalizer();

    private IDelayResolver delayResolver = new GenericDelayResolver();

    private IHttpClientFactory httpClientFactory =
            new GenericHttpClientFactory();

    private IHttpDocumentFetcher documentFetcher =
            new GenericDocumentFetcher();

    private ICanonicalLinkDetector canonicalLinkDetector =
            new GenericCanonicalLinkDetector();

    private IRedirectURLProvider redirectURLProvider =
            new GenericRedirectURLProvider();

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

    private IRecrawlableResolver recrawlableResolver =
            new GenericRecrawlableResolver();

    public HttpCrawlerConfig() {
        super();
    }

    public String[] getStartURLs() {
        return ArrayUtils.clone(startURLs);
    }
    public void setStartURLs(String... startURLs) {
        this.startURLs = ArrayUtils.clone(startURLs);
    }
    /**
     * Gets the file paths of seed files containing URLs to be used as
     * "start URLs".  Files are expected to have one URL per line.
     * Blank lines and lines starting with # (comment) are ignored.
     * @return file paths of seed files containing URLs
     * @since 2.3.0
     */
    public String[] getStartURLsFiles() {
        return startURLsFiles;
    }
    /**
     * Sets the file paths of seed files containing URLs to be used as
     * "start URLs". Files are expected to have one URL per line.
     * Blank lines and lines starting with # (comment) are ignored.
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
    /**
     * Gets the providers of URLs used as starting points for crawling.
     * Use this approach over other methods when URLs need to be provided
     * dynamicaly at launch time. URLs obtained by a provider are combined
     * with start URLs provided through other methods.
     * @return a start URL provider
     * @since 2.7.0
     */
    public IStartURLsProvider[] getStartURLsProviders() {
        return startURLsProviders;
    }
    /**
     * Sets the providers of URLs used as starting points for crawling.
     * Use this approach over other methods when URLs need to be provided
     * dynamicaly at launch time. URLs obtained by a provider are combined
     * with start URLs provided through other methods.
     * @param startURLsProviders start URL provider
     * @since 2.7.0
     */
    public void setStartURLsProviders(
            IStartURLsProvider... startURLsProviders) {
        this.startURLsProviders = startURLsProviders;
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
    public void setLinkExtractors(ILinkExtractor... linkExtractors) {
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
     * Whether links not in scope should be stored as metadata
     * under {@link HttpMetadata#COLLECTOR_REFERENCED_URLS_OUT_OF_SCOPE}
     * @return <code>true</code> if keeping URLs not in scope.
     * @since 2.8.0
     */
	public boolean isKeepOutOfScopeLinks() {
        return keepOutOfScopeLinks;
    }
	/**
	 * Sets whether links not in scope should be stored as metadata
     * under {@link HttpMetadata#COLLECTOR_REFERENCED_URLS_OUT_OF_SCOPE}
     * @param keepOutOfScopeLinks <code>true</code> if keeping URLs not in scope
     * @since 2.8.0
	 */
    public void setKeepOutOfScopeLinks(boolean keepOutOfScopeLinks) {
        this.keepOutOfScopeLinks = keepOutOfScopeLinks;
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
    //TODO rename this to something like: disableSitemapDiscovery ?
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

    /**
     * Gets the strategy to use to determine if a URL is in scope.
     * @return the strategy
     */
    public URLCrawlScopeStrategy getURLCrawlScopeStrategy() {
        return urlCrawlScopeStrategy;
    }
    /**
     * Sets the strategy to use to determine if a URL is in scope.
     * @param urlCrawlScopeStrategy strategy to use
     * @since 2.8.1
     */
    public void setUrlCrawlScopeStrategy(
            URLCrawlScopeStrategy urlCrawlScopeStrategy) {
        this.urlCrawlScopeStrategy = urlCrawlScopeStrategy;
    }

    /**
     * Gets the redirect URL provider.
     * @return the redirect URL provider
     * @since 2.4.0
     */
    public IRedirectURLProvider getRedirectURLProvider() {
        return redirectURLProvider;
    }
    /**
     * Sets the redirect URL provider
     * @param redirectURLProvider redirect URL provider
     * @since 2.4.0
     */
    public void setRedirectURLProvider(
            IRedirectURLProvider redirectURLProvider) {
        this.redirectURLProvider = redirectURLProvider;
    }

    /**
     * Gets the recrawlable resolver.
     * @return recrawlable resolver
     * @since 2.5.0
     */
    public IRecrawlableResolver getRecrawlableResolver() {
        return recrawlableResolver;
    }
    /**
     * Sets the recrawlable resolver.
     * @param recrawlableResolver the recrawlable resolver
     * @since 2.5.0
     */
    public void setRecrawlableResolver(
            IRecrawlableResolver recrawlableResolver) {
        this.recrawlableResolver = recrawlableResolver;
    }

    /**
     * Gets whether to skip metadata fetching activities instead of
     * rejecting a document on bad status.
     * @return <code>true</code> if skipping
     * @since 2.9.1
     */
    public boolean isSkipMetaFetcherOnBadStatus() {
        return skipMetaFetcherOnBadStatus;
    }
    /**
     * Sets whether to skip metadata fetching activities instead of
     * rejecting a document on bad status. If <code>true</code>, upon
     * receiving a bad HTTP status code, activities such as metadata filtering,
     * canonical URL resolution and metadata checksum creation are all skipped.
     * When applicable, those activites will be performed after the document
     * fetcher also had a chance to download metadata. Setting this flag to
     * <code>true</code> can be useful when the HTTP HEAD method is not
     * supported by some sites or pages.
     * @param skipMetaFetcherOnBadStatus <code>true</code> if skipping
     * @since 2.9.1
     */
    public void setSkipMetaFetcherOnBadStatus(
            boolean skipMetaFetcherOnBadStatus) {
        this.skipMetaFetcherOnBadStatus = skipMetaFetcherOnBadStatus;
    }

    @Override
    protected void saveCrawlerConfigToXML(Writer out) throws IOException {
        try {
            out.flush();
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeElementString("userAgent", getUserAgent());
            writer.writeElementInteger("maxDepth", getMaxDepth());
            writer.writeElementBoolean("keepDownloads", isKeepDownloads());

			writer.writeElementBoolean(
			        "keepOutOfScopeLinks", isKeepOutOfScopeLinks());
            writer.writeStartElement("startURLs");
            writer.writeAttributeBoolean("stayOnProtocol",
                    urlCrawlScopeStrategy.isStayOnProtocol());
            writer.writeAttributeBoolean("stayOnDomain",
                    urlCrawlScopeStrategy.isStayOnDomain());
            writer.writeAttributeBoolean("includeSubdomains",
                    urlCrawlScopeStrategy.isIncludeSubdomains());
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
            writer.flush();
            IStartURLsProvider[] startURLsProviders = getStartURLsProviders();
            if (startURLsProviders != null) {
                for (IStartURLsProvider provider : startURLsProviders) {
                    writeObject(out, "provider", provider);
                }
            }
            out.flush();
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
            writeObject(out, "redirectURLProvider", getRedirectURLProvider());
            writeObject(out, "recrawlableResolver", getRecrawlableResolver());


            //--- Metadata fetcher ---------------------------------------------
            out.flush();
            writer.flush();
            StringWriter metaOut = new StringWriter();
            writeObject(metaOut, "metadataFetcher", getMetadataFetcher());
            String metaXML = metaOut.toString();
            metaXML = metaXML.replaceFirst("^(<metadataFetcher)",
                    "$1 skipOnBadStatus=\"" + isSkipMetaFetcherOnBadStatus()
                    + "\"");
            out.write(metaXML);
            out.flush();

            //------------------------------------------------------------------
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
        setHttpClientFactory(XMLConfigurationUtil.newInstance(xml,
                "httpClientFactory", getHttpClientFactory()));

        //--- RobotsTxt provider -----------------------------------------------
        setRobotsTxtProvider(XMLConfigurationUtil.newInstance(xml,
                "robotsTxt", getRobotsTxtProvider()));
        setIgnoreRobotsTxt(xml.getBoolean("robotsTxt[@ignore]",
                isIgnoreRobotsTxt()));

        //--- Sitemap Resolver -------------------------------------------------
        ISitemapResolverFactory sitemapFactory = XMLConfigurationUtil.newInstance(
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
                sitemapFactory = XMLConfigurationUtil.newInstance(xml, "sitemap");
                setIgnoreSitemap(
                        xml.getBoolean("sitemap[@ignore]", isIgnoreSitemap()));
            }
        }
        if (sitemapFactory == null) {
            sitemapFactory = getSitemapResolverFactory();
        }
        setSitemapResolverFactory(sitemapFactory);

        //--- Canonical Link Detector ------------------------------------------
        setCanonicalLinkDetector(XMLConfigurationUtil.newInstance(xml,
                "canonicalLinkDetector", getCanonicalLinkDetector()));
        setIgnoreCanonicalLinks(xml.getBoolean("canonicalLinkDetector[@ignore]",
                isIgnoreCanonicalLinks()));

        //--- Redirect URL Provider --------------------------------------------
        setRedirectURLProvider(XMLConfigurationUtil.newInstance(xml,
                "redirectURLProvider", getRedirectURLProvider()));

        //--- Recrawlable resolver ---------------------------------------------
        setRecrawlableResolver(XMLConfigurationUtil.newInstance(xml,
                "recrawlableResolver", getRecrawlableResolver()));

        //--- HTTP Headers Fetcher ---------------------------------------------
        setSkipMetaFetcherOnBadStatus(xml.getBoolean(
                "metadataFetcher[@skipOnBadStatus]",
                isSkipMetaFetcherOnBadStatus()));
        setMetadataFetcher(XMLConfigurationUtil.newInstance(xml,
                "metadataFetcher", getMetadataFetcher()));

        //--- Metadata Checksummer ---------------------------------------------
        setMetadataChecksummer(XMLConfigurationUtil.newInstance(xml,
                "metadataChecksummer", getMetadataChecksummer()));

        //--- HTTP Document Fetcher --------------------------------------------
        setDocumentFetcher(XMLConfigurationUtil.newInstance(xml,
                "documentFetcher", getDocumentFetcher()));

        //--- RobotsMeta provider ----------------------------------------------
        setRobotsMetaProvider(XMLConfigurationUtil.newInstance(xml,
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
        setUrlNormalizer(XMLConfigurationUtil.newInstance(
                xml, "urlNormalizer", getUrlNormalizer()));
        setDelayResolver(XMLConfigurationUtil.newInstance(
                xml, "delay", getDelayResolver()));
        setMaxDepth(xml.getInt("maxDepth", getMaxDepth()));
        setKeepDownloads(xml.getBoolean("keepDownloads", isKeepDownloads()));
		setKeepOutOfScopeLinks(
		        xml.getBoolean("keepOutOfScopeLinks", isKeepOutOfScopeLinks()));
        setIgnoreCanonicalLinks(xml.getBoolean(
                "ignoreCanonicalLinks", isIgnoreCanonicalLinks()));
        urlCrawlScopeStrategy.setStayOnProtocol(xml.getBoolean(
                "startURLs[@stayOnProtocol]",
                urlCrawlScopeStrategy.isStayOnProtocol()));
        urlCrawlScopeStrategy.setStayOnDomain(xml.getBoolean(
                "startURLs[@stayOnDomain]",
                urlCrawlScopeStrategy.isStayOnDomain()));
        urlCrawlScopeStrategy.setIncludeSubdomains(xml.getBoolean(
                "startURLs[@includeSubdomains]",
                urlCrawlScopeStrategy.isIncludeSubdomains()));
        urlCrawlScopeStrategy.setStayOnPort(xml.getBoolean(
                "startURLs[@stayOnPort]",
                urlCrawlScopeStrategy.isStayOnPort()));

        String[] startURLs = xml.getStringArray("startURLs.url");
        setStartURLs(defaultIfEmpty(startURLs, getStartURLs()));

        String[] urlsFiles = xml.getStringArray("startURLs.urlsFile");
        setStartURLsFiles(defaultIfEmpty(urlsFiles, getStartURLsFiles()));

        String[] sitemapURLs = xml.getStringArray("startURLs.sitemap");
        setStartSitemapURLs(defaultIfEmpty(sitemapURLs, getStartSitemapURLs()));

        IStartURLsProvider[] startURLsProviders = loadStartURLsProviders(xml);
        setStartURLsProviders(
                defaultIfEmpty(startURLsProviders, getStartURLsProviders()));
    }

    private IStartURLsProvider[] loadStartURLsProviders(
            XMLConfiguration xml) {
        List<IStartURLsProvider> providers = new ArrayList<>();
        List<HierarchicalConfiguration> nodes =
                xml.configurationsAt("startURLs.provider");
        for (HierarchicalConfiguration node : nodes) {
            IStartURLsProvider p = XMLConfigurationUtil.newInstance(node);
            providers.add(p);
            LOG.info("Start URLs provider loaded: " + p);
        }
        return providers.toArray(new IStartURLsProvider[] {});
    }

    private IHttpDocumentProcessor[] loadProcessors(
            XMLConfiguration xml, String xmlPath) {
        List<IHttpDocumentProcessor> filters = new ArrayList<>();
        List<HierarchicalConfiguration> filterNodes = xml
                .configurationsAt(xmlPath);
        for (HierarchicalConfiguration filterNode : filterNodes) {
            IHttpDocumentProcessor filter =
                    XMLConfigurationUtil.newInstance(filterNode);
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
            ILinkExtractor extractor = XMLConfigurationUtil.newInstance(
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
                .append(startURLsProviders, castOther.startURLsProviders)
                .append(ignoreRobotsTxt, castOther.ignoreRobotsTxt)
                .append(ignoreRobotsMeta, castOther.ignoreRobotsMeta)
                .append(ignoreSitemap, castOther.ignoreSitemap)
                .append(keepDownloads, castOther.keepDownloads)
                .append(keepOutOfScopeLinks, castOther.keepOutOfScopeLinks)
                .append(ignoreCanonicalLinks, castOther.ignoreCanonicalLinks)
                .append(skipMetaFetcherOnBadStatus,
                        castOther.skipMetaFetcherOnBadStatus)
                .append(userAgent, castOther.userAgent)
                .append(urlCrawlScopeStrategy, castOther.urlCrawlScopeStrategy)
                .append(urlNormalizer, castOther.urlNormalizer)
                .append(delayResolver, castOther.delayResolver)
                .append(httpClientFactory, castOther.httpClientFactory)
                .append(documentFetcher, castOther.documentFetcher)
                .append(canonicalLinkDetector, castOther.canonicalLinkDetector)
                .append(redirectURLProvider, castOther.redirectURLProvider)
                .append(recrawlableResolver, castOther.recrawlableResolver)
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
                .append(startURLsProviders)
                .append(ignoreRobotsTxt)
                .append(ignoreRobotsMeta)
                .append(ignoreSitemap)
                .append(keepDownloads)
                .append(keepOutOfScopeLinks)
                .append(ignoreCanonicalLinks)
                .append(skipMetaFetcherOnBadStatus)
                .append(userAgent)
                .append(urlCrawlScopeStrategy)
                .append(urlNormalizer)
                .append(delayResolver)
                .append(httpClientFactory)
                .append(documentFetcher)
                .append(canonicalLinkDetector)
                .append(redirectURLProvider)
                .append(recrawlableResolver)
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
                .append("startURLsProviders", startURLsProviders)
                .append("ignoreRobotsTxt", ignoreRobotsTxt)
                .append("ignoreRobotsMeta", ignoreRobotsMeta)
                .append("ignoreSitemap", ignoreSitemap)
                .append("keepDownloads", keepDownloads)
                .append("keepOutOfScopeLinks", keepOutOfScopeLinks)
                .append("ignoreCanonicalLinks", ignoreCanonicalLinks)
                .append("skipMetaFetcherOnBadStatus",
                        skipMetaFetcherOnBadStatus)
                .append("userAgent", userAgent)
                .append("urlCrawlScopeStrategy", urlCrawlScopeStrategy)
                .append("urlNormalizer", urlNormalizer)
                .append("delayResolver", delayResolver)
                .append("httpClientFactory", httpClientFactory)
                .append("documentFetcher", documentFetcher)
                .append("canonicalLinkDetector", canonicalLinkDetector)
                .append("redirectURLProvider", redirectURLProvider)
                .append("recrawlableResolver", recrawlableResolver)
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
