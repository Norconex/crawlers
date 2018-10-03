/* Copyright 2010-2018 Norconex Inc.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpFetcher;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.collector.http.fetch.impl.GenericDocumentFetcher;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
import com.norconex.collector.http.recrawl.IRecrawlableResolver;
import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver;
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
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.xml.XML;

/**
 * HTTP Crawler configuration.
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfig extends CrawlerConfig {

    private int maxDepth = -1;
    private final List<String> startURLs = new ArrayList<>();
    private final List<Path> startURLsFiles = new ArrayList<>();
    private final List<String> startSitemapURLs = new ArrayList<>();
    private final List<IStartURLsProvider> startURLsProviders =
            new ArrayList<>();

    private boolean ignoreRobotsTxt;
    private boolean ignoreRobotsMeta;
    private boolean ignoreSitemap;
    private boolean keepDownloads;
    private boolean ignoreCanonicalLinks;
	private boolean keepOutOfScopeLinks;
	private boolean fetchHttpHead;

//    private String userAgent;
//
    private URLCrawlScopeStrategy urlCrawlScopeStrategy =
            new URLCrawlScopeStrategy();

    private IURLNormalizer urlNormalizer = new GenericURLNormalizer();

    private IDelayResolver delayResolver = new GenericDelayResolver();

    private final List<IHttpFetcher> httpFetchers =
            new ArrayList<>(Arrays.asList(new GenericHttpFetcher()));

    private IHttpClientFactory httpClientFactory =
            new GenericHttpClientFactory();

    private IHttpDocumentFetcher documentFetcher =
            new GenericDocumentFetcher();

    private ICanonicalLinkDetector canonicalLinkDetector =
            new GenericCanonicalLinkDetector();

//    private IRedirectURLProvider redirectURLProvider =
//            new GenericRedirectURLProvider();

    private IHttpMetadataFetcher metadataFetcher;

    private final List<ILinkExtractor> linkExtractors =
            new ArrayList<>(Arrays.asList(new GenericLinkExtractor()));

    private IRobotsTxtProvider robotsTxtProvider =
            new StandardRobotsTxtProvider();
    private IRobotsMetaProvider robotsMetaProvider =
            new StandardRobotsMetaProvider();
    private ISitemapResolverFactory sitemapResolverFactory =
            new StandardSitemapResolverFactory();

    private IMetadataChecksummer metadataChecksummer =
    		new LastModifiedMetadataChecksummer();

    private final List<IHttpDocumentProcessor> preImportProcessors =
            new ArrayList<>();
    private final List<IHttpDocumentProcessor> postImportProcessors =
            new ArrayList<>();

    private IRecrawlableResolver recrawlableResolver =
            new GenericRecrawlableResolver();

    public HttpCrawlerConfig() {
        super();
    }


    /**
     * Gets whether to fetch HTTP response headers using an
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4">
     * HTTP HEAD</a> request.  That HTTP request is performed separately from
     * a document download request. Usually useful when you need to filter
     * documents based on HTTP header values, without downloading them first
     * (e.g., to save bandwidth).
     * When dealing with small documents on average, it may be best to
     * avoid issuing two requests when a single one could do it.
     * @return <code>true</code> if fetching HTTP response headers separately
     * @since 3.0.0
     */
    public boolean isFetchHttpHead() {
        return fetchHttpHead;
    }
    /**
     * Sets whether to fetch HTTP response headers using an
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4">
     * HTTP HEAD</a> request.
     * @param fetchHttpHead <code>true</code>
     *        if fetching HTTP response headers separately
     * @since 3.0.0
     * @see #isFetchHttpHead()
     */
    public void setFetchHttpHead(boolean fetchHttpHead) {
        this.fetchHttpHead = fetchHttpHead;
    }

    /**
     * Gets URLs to initiate crawling from.
     * @return start URLs (never <code>null</code>)

     */
    public List<String> getStartURLs() {
        return Collections.unmodifiableList(startURLs);
    }
    /**
     * Sets URLs to initiate crawling from.
     * @param startURLs start URLs
     */
    public void setStartURLs(String... startURLs) {
        setStartURLs(Arrays.asList(startURLs));
    }
    /**
     * Sets URLs to initiate crawling from.
     * @param startURLs start URLs
     * @since 3.0.0
     */
    public void setStartURLs(List<String> startURLs) {
        CollectionUtil.setAll(this.startURLs, startURLs);
    }

    /**
     * Gets the file paths of seed files containing URLs to be used as
     * "start URLs".  Files are expected to have one URL per line.
     * Blank lines and lines starting with # (comment) are ignored.
     * @return file paths of seed files containing URLs
     *         (never <code>null</code>)
     * @since 2.3.0
     */
    public List<Path> getStartURLsFiles() {
        return Collections.unmodifiableList(startURLsFiles);
    }
    /**
     * Sets the file paths of seed files containing URLs to be used as
     * "start URLs". Files are expected to have one URL per line.
     * Blank lines and lines starting with # (comment) are ignored.
     * @param startURLsFiles file paths of seed files containing URLs
     * @since 2.3.0
     */
    public void setStartURLsFiles(Path... startURLsFiles) {
        setStartURLsFiles(Arrays.asList(startURLsFiles));
    }
    /**
     * Sets the file paths of seed files containing URLs to be used as
     * "start URLs". Files are expected to have one URL per line.
     * Blank lines and lines starting with # (comment) are ignored.
     * @param startURLsFiles file paths of seed files containing URLs
     * @since 3.0.0
     */
    public void setStartURLsFiles(List<Path> startURLsFiles) {
        CollectionUtil.setAll(this.startURLsFiles, startURLsFiles);
    }

    /**
     * Gets sitemap URLs to be used as starting points for crawling.
     * @return sitemap URLs (never <code>null</code>)
     * @since 2.3.0
     */
    public List<String> getStartSitemapURLs() {
        return Collections.unmodifiableList(startSitemapURLs);
    }
    /**
     * Sets the sitemap URLs used as starting points for crawling.
     * @param startSitemapURLs sitemap URLs
     * @since 2.3.0
     */
    public void setStartSitemapURLs(String... startSitemapURLs) {
        setStartSitemapURLs(Arrays.asList(startSitemapURLs));
    }
    /**
     * Sets the sitemap URLs used as starting points for crawling.
     * @param startSitemapURLs sitemap URLs
     * @since 3.0.0
     */
    public void setStartSitemapURLs(List<String> startSitemapURLs) {
        CollectionUtil.setAll(this.startSitemapURLs, startSitemapURLs);
    }

    /**
     * Gets the providers of URLs used as starting points for crawling.
     * Use this approach over other methods when URLs need to be provided
     * dynamicaly at launch time. URLs obtained by a provider are combined
     * with start URLs provided through other methods.
     * @return start URL providers (never <code>null</code>)

     * @since 2.7.0
     */
    public List<IStartURLsProvider> getStartURLsProviders() {
        return Collections.unmodifiableList(startURLsProviders);
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
        setStartURLsProviders(Arrays.asList(startURLsProviders));
    }
    /**
     * Sets the providers of URLs used as starting points for crawling.
     * Use this approach over other methods when URLs need to be provided
     * dynamicaly at launch time. URLs obtained by a provider are combined
     * with start URLs provided through other methods.
     * @param startURLsProviders start URL provider
     * @since 3.0.0
     */
    public void setStartURLsProviders(
            List<IStartURLsProvider> startURLsProviders) {
        CollectionUtil.setAll(this.startURLsProviders, startURLsProviders);
    }

    public void setMaxDepth(int depth) {
        this.maxDepth = depth;
    }
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Gets HTTP fetchers.
     * @return start URLs (never <code>null</code>)
     * @since 3.0.0
     */
    public List<IHttpFetcher> getHttpFetchers() {
        return Collections.unmodifiableList(httpFetchers);
    }
    /**
     * Sets HTTP fetchers.
     * @param httpFetchers list of HTTP fetchers
     * @since 3.0.0
     */
    public void setHttpFetchers(IHttpFetcher... httpFetchers) {
        setHttpFetchers(Arrays.asList(httpFetchers));
    }
    /**
     * Sets HTTP fetchers.
     * @param httpFetchers list of HTTP fetchers
     * @since 3.0.0
     */
    public void setHttpFetchers(List<IHttpFetcher> httpFetchers) {
        CollectionUtil.setAll(this.httpFetchers, httpFetchers);
    }

//    public IHttpFetcher getHttpFetcher() {
//        return httpFetcher;
//    }
//    public void setHttpFetcher(IHttpFetcher httpFetcher) {
//        this.httpFetcher = httpFetcher;
//    }

    @Deprecated
    public IHttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }
    @Deprecated
    public void setHttpClientFactory(IHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Deprecated
    public IHttpDocumentFetcher getDocumentFetcher() {
        return documentFetcher;
    }
    @Deprecated
    public void setDocumentFetcher(IHttpDocumentFetcher documentFetcher) {
        this.documentFetcher = documentFetcher;
    }

    @Deprecated
    public IHttpMetadataFetcher getMetadataFetcher() {
        return metadataFetcher;
    }
    @Deprecated
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

    /**
     * Gets link extractors.
     * @return link extractors
     */
    public List<ILinkExtractor> getLinkExtractors() {
        return Collections.unmodifiableList(linkExtractors);
    }
    /**
     * Sets link extractors.
     * @param linkExtractors link extractors
     */
    public void setLinkExtractors(ILinkExtractor... linkExtractors) {
        setLinkExtractors(Arrays.asList(linkExtractors));
    }
    /**
     * Sets link extractors.
     * @param linkExtractors link extractors
     * @since 3.0.0
     */
    public void setLinkExtractors(List<ILinkExtractor> linkExtractors) {
        CollectionUtil.setAll(this.linkExtractors, linkExtractors);
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

    /**
     * Gets pre-import processors.
     * @return pre-import processors
     */
    public List<IHttpDocumentProcessor> getPreImportProcessors() {
        return Collections.unmodifiableList(preImportProcessors);
    }
    /**
     * Sets pre-import processors.
     * @param preImportProcessors pre-import processors
     */
    public void setPreImportProcessors(
            IHttpDocumentProcessor... preImportProcessors) {
        setPreImportProcessors(Arrays.asList(preImportProcessors));
    }
    /**
     * Sets pre-import processors.
     * @param preImportProcessors pre-import processors
     * @since 3.0.0
     */
    public void setPreImportProcessors(
            List<IHttpDocumentProcessor> preImportProcessors) {
        CollectionUtil.setAll(this.preImportProcessors, preImportProcessors);
    }

    /**
     * Gets post-import processors.
     * @return post-import processors
     */
    public List<IHttpDocumentProcessor> getPostImportProcessors() {
        return Collections.unmodifiableList(postImportProcessors);
    }
    /**
     * Sets post-import processors.
     * @param postImportProcessors post-import processors
     */
    public void setPostImportProcessors(
    		IHttpDocumentProcessor... postImportProcessors) {
        setPostImportProcessors(Arrays.asList(postImportProcessors));
    }
    /**
     * Sets post-import processors.
     * @param postImportProcessors post-import processors
     * @since 3.0.0
     */
    public void setPostImportProcessors(
            List<IHttpDocumentProcessor> postImportProcessors) {
        CollectionUtil.setAll(this.postImportProcessors, postImportProcessors);
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

//    // Make it part of HttpFetcher
//    public String getUserAgent() {
//        return userAgent;
//    }
//    public void setUserAgent(String userAgent) {
//        this.userAgent = userAgent;
//    }

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

//    /**
//     * Gets the redirect URL provider.
//     * @return the redirect URL provider
//     * @since 2.4.0
//     */
//    public IRedirectURLProvider getRedirectURLProvider() {
//        return redirectURLProvider;
//    }
//    /**
//     * Sets the redirect URL provider
//     * @param redirectURLProvider redirect URL provider
//     * @since 2.4.0
//     */
//    public void setRedirectURLProvider(
//            IRedirectURLProvider redirectURLProvider) {
//        this.redirectURLProvider = redirectURLProvider;
//    }

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

    @Override
    protected void saveCrawlerConfigToXML(XML xml) {
//        xml.addElement("userAgent", userAgent);
        xml.addElement("maxDepth", maxDepth);
        xml.addElement("keepDownloads", keepDownloads);
		xml.addElement("keepOutOfScopeLinks", keepOutOfScopeLinks);
        xml.addElement("fetchHttpHead", fetchHttpHead);

		XML startXML = xml.addElement("startURLs")
		        .setAttribute("stayOnProtocol",
		                urlCrawlScopeStrategy.isStayOnProtocol())
                .setAttribute("stayOnDomain",
                        urlCrawlScopeStrategy.isStayOnDomain())
                .setAttribute("stayOnPort",
                        urlCrawlScopeStrategy.isStayOnPort());
		startXML.addElementList("url", startURLs);
        startXML.addElementList("urlsFile", startURLsFiles);
        startXML.addElementList("sitemap", startSitemapURLs);
        startXML.addElementList("provider", startURLsProviders);

        xml.addElement("urlNormalizer", urlNormalizer);
        xml.addElement("delay", delayResolver);
        xml.addElement("httpClientFactory", httpClientFactory);
        xml.addElement("robotsTxt", robotsTxtProvider)
                .setAttribute("ignore", ignoreRobotsTxt);
        xml.addElement("sitemapResolverFactory",
                sitemapResolverFactory).setAttribute("ignore", ignoreSitemap);
        xml.addElement("canonicalLinkDetector", canonicalLinkDetector);
//        xml.addElement("redirectURLProvider", redirectURLProvider);
        xml.addElement("recrawlableResolver", recrawlableResolver);

        xml.addElementList("httpFetchers", "fetcher", httpFetchers);
//        xml.addElement("metadataFetcher", metadataFetcher);
        xml.addElement("metadataChecksummer", metadataChecksummer);
//        xml.addElement("documentFetcher", documentFetcher);
        xml.addElement("robotsMeta", robotsMetaProvider)
                .setAttribute("ignore", ignoreRobotsMeta);
        xml.addElementList("linkExtractors", "extractor", linkExtractors);
        xml.addElementList(
                "preImportProcessors", "processor", preImportProcessors);
        xml.addElementList(
                "postImportProcessors", "processor", postImportProcessors);
    }

    @Override
    protected void loadCrawlerConfigFromXML(XML xml) {
        // Simple Settings
        loadSimpleSettings(xml);

        // HTTP Client Factory
        setHttpClientFactory(
                xml.getObject("httpClientFactory", httpClientFactory));

        // RobotsTxt provider
        setRobotsTxtProvider(xml.getObject("robotsTxt", robotsTxtProvider));
        setIgnoreRobotsTxt(
                xml.getBoolean("robotsTxt/@ignore", ignoreRobotsTxt));

        // Sitemap Resolver
        setSitemapResolverFactory(xml.getObject(
                "sitemapResolverFactory", sitemapResolverFactory));
        setIgnoreSitemap(xml.getBoolean(
                "sitemapResolverFactory/@ignore", ignoreSitemap));

        // Canonical Link Detector
        setCanonicalLinkDetector(
                xml.getObject("canonicalLinkDetector", canonicalLinkDetector));
        setIgnoreCanonicalLinks(xml.getBoolean(
                "canonicalLinkDetector/@ignore", ignoreCanonicalLinks));

        // Redirect URL Provider
//        setRedirectURLProvider(
//                xml.getObject("redirectURLProvider", redirectURLProvider));

        // Recrawlable resolver
        setRecrawlableResolver(
                xml.getObject("recrawlableResolver", recrawlableResolver));

        // HTTP Fetchers
        setHttpFetchers(xml.getObjectList(
                "httpFetchers/fetcher", httpFetchers));

        xml.checkDeprecated("metadataFetcher", "httpFetchers/fetcher", true);
//        // HTTP Headers Fetcher
//        setMetadataFetcher(xml.getObject("metadataFetcher", metadataFetcher));

        // Metadata Checksummer
        setMetadataChecksummer(
                xml.getObject("metadataChecksummer", metadataChecksummer));

        // HTTP Document Fetcher
        xml.checkDeprecated("metadataFetcher", "httpFetchers/fetcher", true);
//        setDocumentFetcher(xml.getObject("documentFetcher", documentFetcher));

        // RobotsMeta provider
        setRobotsMetaProvider(xml.getObject("robotsMeta", robotsMetaProvider));
        setIgnoreRobotsMeta(
                xml.getBoolean("robotsMeta/@ignore", ignoreRobotsMeta));

        // Link Extractors
        setLinkExtractors(xml.getObjectList(
                "linkExtractors/extractor", linkExtractors));

        // HTTP Pre-Processors
        setPreImportProcessors(xml.getObjectList(
                "preImportProcessors/processor", preImportProcessors));

        // HTTP Post-Processors
        setPostImportProcessors(xml.getObjectList(
                "postImportProcessors/processor", postImportProcessors));
    }

    private void loadSimpleSettings(XML xml) {
//        setUserAgent(xml.getString("userAgent", userAgent));
        setUrlNormalizer(xml.getObject("urlNormalizer", urlNormalizer));
        setDelayResolver(xml.getObject("delay", delayResolver));
        setMaxDepth(xml.getInteger("maxDepth", maxDepth));
        setKeepDownloads(xml.getBoolean("keepDownloads", keepDownloads));
        setFetchHttpHead(xml.getBoolean("fetchHttpHead", fetchHttpHead));

		setKeepOutOfScopeLinks(
		        xml.getBoolean("keepOutOfScopeLinks", keepOutOfScopeLinks));
        setIgnoreCanonicalLinks(xml.getBoolean(
                "ignoreCanonicalLinks", ignoreCanonicalLinks));
        urlCrawlScopeStrategy.setStayOnProtocol(xml.getBoolean(
                "startURLs/@stayOnProtocol",
                urlCrawlScopeStrategy.isStayOnProtocol()));
        urlCrawlScopeStrategy.setStayOnDomain(xml.getBoolean(
                "startURLs/@stayOnDomain",
                urlCrawlScopeStrategy.isStayOnDomain()));
        urlCrawlScopeStrategy.setStayOnPort(xml.getBoolean(
                "startURLs/@stayOnPort",
                urlCrawlScopeStrategy.isStayOnPort()));
        setStartURLs(xml.getStringList("startURLs/url", startURLs));
        setStartURLsFiles(
                xml.getPathList("startURLs/urlsFile", startURLsFiles));
        setStartSitemapURLs(
                xml.getStringList("startURLs/sitemap", startSitemapURLs));
        setStartURLsProviders(
                xml.getObjectList("startURLs/provider", startURLsProviders));
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
