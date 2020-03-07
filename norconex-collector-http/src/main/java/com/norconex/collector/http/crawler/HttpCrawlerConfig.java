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
import com.norconex.collector.http.canon.ICanonicalLinkDetector;
import com.norconex.collector.http.canon.impl.GenericCanonicalLinkDetector;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.fetch.IHttpFetcher;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.collector.http.link.impl.HtmlLinkExtractor;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
import com.norconex.collector.http.recrawl.IRecrawlableResolver;
import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver;
import com.norconex.collector.http.robot.IRobotsMetaProvider;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.impl.StandardRobotsMetaProvider;
import com.norconex.collector.http.robot.impl.StandardRobotsTxtProvider;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.impl.GenericSitemapResolver;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.impl.GenericURLNormalizer;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * HTTP Crawler configuration.
 * </p>
 * <h3>Keeping Referenced Links</h3>
 * <p>
 * By default the crawler will store as metadata all URLs extracted from
 * documents that are "in scope".  This can be changed using the
 * {@link #setKeepReferencedLinks(KeepLinks)} method. Changing this setting
 * has no incidence on what gets crawled.  Possible options are:
 * </p>
 * <ul>
 *   <li><b>NONE:</b> Do not keep extracted links.</li>
 *   <li><b>INSCOPE:</b> Only store "in-scope" links as
 *       {@link HttpDocMetadata#REFERENCED_URLS}.</li>
 *   <li><b>OUTSCOPE:</b> Only store "out-of-scope" links as
 *       {@link HttpDocMetadata#REFERENCED_URLS_OUT_OF_SCOPE}.</li>
 *   <li><b>ALL:</b> Store both in-scope and out-of-scope URLs in their
 *       respective target field names defined above.</li>
 * </ul>
 *
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfig extends CrawlerConfig {

    public enum KeepLinks {
        NONE, INSCOPE, OUTSCOPE, ALL;
        public boolean keepInScope() {
            return this.equals(INSCOPE) || this.equals(ALL);
        }
        public boolean keepOutScope() {
            return this.equals(OUTSCOPE) || this.equals(ALL);
        }
    }

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
	private KeepLinks keepReferencedLinks = KeepLinks.INSCOPE;

	private boolean fetchHttpHead;

    private URLCrawlScopeStrategy urlCrawlScopeStrategy =
            new URLCrawlScopeStrategy();

    private IURLNormalizer urlNormalizer = new GenericURLNormalizer();

    private IDelayResolver delayResolver = new GenericDelayResolver();

    private final List<IHttpFetcher> httpFetchers =
            new ArrayList<>(Arrays.asList(new GenericHttpFetcher()));
    private int httpFetchersMaxRetries;
    private long httpFetchersRetryDelay;

    private ICanonicalLinkDetector canonicalLinkDetector =
            new GenericCanonicalLinkDetector();

    private final List<ILinkExtractor> linkExtractors =
            new ArrayList<>(Arrays.asList(new HtmlLinkExtractor()));

    private IRobotsTxtProvider robotsTxtProvider =
            new StandardRobotsTxtProvider();
    private IRobotsMetaProvider robotsMetaProvider =
            new StandardRobotsMetaProvider();
    private ISitemapResolver sitemapResolver = new GenericSitemapResolver();

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
    /**
     * Gets the maximum number of times an HTTP fetcher will re-attempt fetching
     * a resource in case of failures.  Default is zero (won't retry).
     * @return number of times
     * @since 3.0.0
     */
    public int getHttpFetchersMaxRetries() {
        return httpFetchersMaxRetries;
    }
    /**
     * Sets the maximum number of times an HTTP fetcher will re-attempt fetching
     * a resource in case of failures.
     * @param httpFetchersMaxRetries maximum number of retries
     * @since 3.0.0
     */
    public void setHttpFetchersMaxRetries(int httpFetchersMaxRetries) {
        this.httpFetchersMaxRetries = httpFetchersMaxRetries;
    }
    /**
     * Gets how long to wait before a failing HTTP fetcher re-attempts fetching
     * a resource in case of failures (in milliseconds).
     * Default is zero (no delay).
     * @return retry delay
     * @since 3.0.0
     */
    public long getHttpFetchersRetryDelay() {
        return httpFetchersRetryDelay;
    }
    /**
     * Sets how long to wait before a failing HTTP fetcher re-attempts fetching
     * a resource in case of failures (in milliseconds).
     * @param httpFetchersRetryDelay retry delay
     * @since 3.0.0
     */
    public void setHttpFetchersRetryDelay(long httpFetchersRetryDelay) {
        this.httpFetchersRetryDelay = httpFetchersRetryDelay;
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
     * under {@link HttpDocMetadata#REFERENCED_URLS_OUT_OF_SCOPE}
     * @return <code>true</code> if keeping URLs not in scope.
     * @since 2.8.0
     * @deprecated Since 3.0.0, use {@link #getKeepReferencedLinks()}.
     */
	@Deprecated
    public boolean isKeepOutOfScopeLinks() {
        return EqualsUtil.equalsAny(
                keepReferencedLinks, KeepLinks.OUTSCOPE, KeepLinks.ALL);
    }
	/**
	 * Sets whether links not in scope should be stored as metadata
     * under {@link HttpDocMetadata#REFERENCED_URLS_OUT_OF_SCOPE}
     * @param keepOutOfScopeLinks <code>true</code> if keeping URLs not in scope
     * @since 2.8.0
     * @deprecated Since 3.0.0, use {@link #setKeepReferencedLinks(KeepLinks)}.
	 */
    @Deprecated
    public void setKeepOutOfScopeLinks(boolean keepOutOfScopeLinks) {
        if (keepOutOfScopeLinks) {
            if (EqualsUtil.equalsAny(
                    keepReferencedLinks, null, KeepLinks.NONE)) {
                setKeepReferencedLinks(KeepLinks.OUTSCOPE);
            } else if (EqualsUtil.equalsAny(
                    keepReferencedLinks, KeepLinks.INSCOPE)) {
                setKeepReferencedLinks(KeepLinks.ALL);
            }
        } else {
            if (EqualsUtil.equalsAny(
                    keepReferencedLinks, KeepLinks.ALL)) {
                setKeepReferencedLinks(KeepLinks.INSCOPE);
            } else if (EqualsUtil.equalsAny(
                    keepReferencedLinks, KeepLinks.OUTSCOPE)) {
                setKeepReferencedLinks(KeepLinks.NONE);
            }
        }
    }

    /**
     * Gets whether to keep referenced links and what to keep.
     * Those links are URLs extracted by link extractors. See class
     * documentation for more details.
     * @return option for keeping links
     * @since 3.0.0
     */
    public KeepLinks getKeepReferencedLinks() {
        return keepReferencedLinks;
    }
    /**
     * Sets whether to keep referenced links and what to keep.
     * Those links are URLs extracted by link extractors. See class
     * documentation for more details.
     * @param keepReferencedLinks option for keeping links
     * @since 3.0.0
     */
    public void setKeepReferencedLinks(KeepLinks keepReferencedLinks) {
        this.keepReferencedLinks = keepReferencedLinks;
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

    public ISitemapResolver getSitemapResolver() {
        return sitemapResolver;
    }
    public void setSitemapResolver(ISitemapResolver sitemapResolver) {
        this.sitemapResolver = sitemapResolver;
    }

    /**
     * Whether canonical links found in HTTP headers and in HTML files
     * &lt;head&gt; section should be ignored or processed. When processed
     * (default), URL pages with a canonical URL pointer in them are not
     * processed.
     * @since 2.2.0
     * @return <code>true</code> if ignoring canonical links
     * processed.
     */
    public boolean isIgnoreCanonicalLinks() {
        return ignoreCanonicalLinks;
    }
    /**
     * Sets whether canonical links found in HTTP headers and in HTML files
     * &lt;head&gt; section should be ignored or processed. If <code>true</code>
     * URL pages with a canonical URL pointer in them are not
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
        xml.addElement("maxDepth", maxDepth);
        xml.addElement("keepDownloads", keepDownloads);
		xml.addElement("keepReferencedLinks", keepReferencedLinks);
        xml.addElement("fetchHttpHead", fetchHttpHead);

		XML startXML = xml.addElement("startURLs")
		        .setAttribute("stayOnProtocol",
		                urlCrawlScopeStrategy.isStayOnProtocol())
                .setAttribute("stayOnDomain",
                        urlCrawlScopeStrategy.isStayOnDomain())
                .setAttribute("includeSubdomains",
                        urlCrawlScopeStrategy.isIncludeSubdomains())
                .setAttribute("stayOnPort",
                        urlCrawlScopeStrategy.isStayOnPort());
		startXML.addElementList("url", startURLs);
        startXML.addElementList("urlsFile", startURLsFiles);
        startXML.addElementList("sitemap", startSitemapURLs);
        startXML.addElementList("provider", startURLsProviders);

        xml.addElement("urlNormalizer", urlNormalizer);
        xml.addElement("delay", delayResolver);
        xml.addElement("robotsTxt", robotsTxtProvider)
                .setAttribute("ignore", ignoreRobotsTxt);
        xml.addElement("sitemapResolver",
                sitemapResolver).setAttribute("ignore", ignoreSitemap);
        xml.addElement("canonicalLinkDetector", canonicalLinkDetector);
        xml.addElement("recrawlableResolver", recrawlableResolver);

        xml.addElement("httpFetchers")
                .setAttribute("maxRetries", httpFetchersMaxRetries)
                .setAttribute("retryDelay", httpFetchersRetryDelay)
                .addElementList("fetcher", httpFetchers);

        xml.addElement("metadataChecksummer", metadataChecksummer);
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

        // RobotsTxt provider
        setRobotsTxtProvider(xml.getObjectImpl(
                IRobotsTxtProvider.class, "robotsTxt", robotsTxtProvider));
        setIgnoreRobotsTxt(
                xml.getBoolean("robotsTxt/@ignore", ignoreRobotsTxt));

        // Sitemap Resolver
        setSitemapResolver(xml.getObjectImpl(
                ISitemapResolver.class,
                "sitemapResolver", sitemapResolver));
        setIgnoreSitemap(xml.getBoolean(
                "sitemapResolver/@ignore", ignoreSitemap));

        // Canonical Link Detector
        setCanonicalLinkDetector(xml.getObjectImpl(ICanonicalLinkDetector.class,
                "canonicalLinkDetector", canonicalLinkDetector));
        setIgnoreCanonicalLinks(xml.getBoolean(
                "canonicalLinkDetector/@ignore", ignoreCanonicalLinks));

        // Recrawlable resolver
        setRecrawlableResolver(xml.getObjectImpl(IRecrawlableResolver.class,
                "recrawlableResolver", recrawlableResolver));

        // HTTP Fetchers
        setHttpFetchers(xml.getObjectListImpl(
                IHttpFetcher.class, "httpFetchers/fetcher", httpFetchers));
        setHttpFetchersMaxRetries(xml.getInteger(
                "httpFetchers/@maxRetries", httpFetchersMaxRetries));
        setHttpFetchersRetryDelay(xml.getDurationMillis(
                "httpFetchers/@retryDelay", httpFetchersRetryDelay));

        // Metadata Checksummer
        setMetadataChecksummer(xml.getObjectImpl(IMetadataChecksummer.class,
                "metadataChecksummer", metadataChecksummer));

        // RobotsMeta provider
        setRobotsMetaProvider(xml.getObjectImpl(
                IRobotsMetaProvider.class, "robotsMeta", robotsMetaProvider));
        setIgnoreRobotsMeta(
                xml.getBoolean("robotsMeta/@ignore", ignoreRobotsMeta));

        // Link Extractors
        setLinkExtractors(xml.getObjectListImpl(ILinkExtractor.class,
                "linkExtractors/extractor", linkExtractors));

        // HTTP Pre-Processors
        setPreImportProcessors(xml.getObjectListImpl(
                IHttpDocumentProcessor.class,
                "preImportProcessors/processor", preImportProcessors));

        // HTTP Post-Processors
        setPostImportProcessors(xml.getObjectListImpl(
                IHttpDocumentProcessor.class,
                "postImportProcessors/processor", postImportProcessors));

        // Removed version 2.x configuration options:
        xml.checkDeprecated("httpClientFactory", "httpFetchers/fetcher", true);
        xml.checkDeprecated("metadataFetcher", "httpFetchers/fetcher", true);
        xml.checkDeprecated("documentFetcher", "httpFetchers/fetcher", true);
        xml.checkDeprecated("redirectURLProvider",
                "'redirectURLProvider' under 'httpFetchers/fetcher' for "
                        + "com.norconex.collector.http.fetch.impl"
                        + ".GenericHttpFetcher", true);
        xml.checkDeprecated("userAgent",
                "'userAgent' under 'httpFetchers/fetcher' for "
                        + "com.norconex.collector.http.fetch.impl"
                        + ".GenericHttpFetcher and possibly other fetchers",
                        true);
    }

    private void loadSimpleSettings(XML xml) {
        xml.checkDeprecated("keepOutOfScopeLinks", "keepReferencedLinks", true);

        setUrlNormalizer(xml.getObjectImpl(
                IURLNormalizer.class, "urlNormalizer", urlNormalizer));
        setDelayResolver(xml.getObjectImpl(
                IDelayResolver.class, "delay", delayResolver));
        setMaxDepth(xml.getInteger("maxDepth", maxDepth));
        setKeepDownloads(xml.getBoolean("keepDownloads", keepDownloads));
        setFetchHttpHead(xml.getBoolean("fetchHttpHead", fetchHttpHead));
		setKeepReferencedLinks(xml.getEnum("keepReferencedLinks",
		        KeepLinks.class, keepReferencedLinks));
        setIgnoreCanonicalLinks(xml.getBoolean(
                "ignoreCanonicalLinks", ignoreCanonicalLinks));
        urlCrawlScopeStrategy.setStayOnProtocol(xml.getBoolean(
                "startURLs/@stayOnProtocol",
                urlCrawlScopeStrategy.isStayOnProtocol()));
        urlCrawlScopeStrategy.setStayOnDomain(xml.getBoolean(
                "startURLs/@stayOnDomain",
                urlCrawlScopeStrategy.isStayOnDomain()));
        urlCrawlScopeStrategy.setIncludeSubdomains(xml.getBoolean(
                "startURLs/@includeSubdomains",
                urlCrawlScopeStrategy.isIncludeSubdomains()));
        urlCrawlScopeStrategy.setStayOnPort(xml.getBoolean(
                "startURLs/@stayOnPort",
                urlCrawlScopeStrategy.isStayOnPort()));
        setStartURLs(xml.getStringList("startURLs/url", startURLs));
        setStartURLsFiles(
                xml.getPathList("startURLs/urlsFile", startURLsFiles));
        setStartSitemapURLs(
                xml.getStringList("startURLs/sitemap", startSitemapURLs));
        setStartURLsProviders(xml.getObjectListImpl(IStartURLsProvider.class,
                "startURLs/provider", startURLsProviders));
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
