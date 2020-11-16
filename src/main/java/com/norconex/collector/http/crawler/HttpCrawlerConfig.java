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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.checksum.IDocumentChecksummer;
import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.core.spoil.ISpoiledReferenceStrategizer;
import com.norconex.collector.core.store.IDataStoreEngine;
import com.norconex.collector.core.store.impl.mvstore.MVStoreDataStoreEngine;
import com.norconex.collector.http.canon.ICanonicalLinkDetector;
import com.norconex.collector.http.canon.impl.GenericCanonicalLinkDetector;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.fetch.IHttpFetcher;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcherConfig;
import com.norconex.collector.http.fetch.impl.webdriver.WebDriverHttpFetcher;
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
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.ImporterConfig;

/**
 * <p>
 * HTTP Crawler configuration.
 * </p>
 * <h3>Start URLs</h3>
 * <p>
 * Crawling begins with one or more "start" URLs.  Multiple start URLs can be
 * defined, in a combination of ways:
 * </p>
 * <ul>
 *   <li><b>url:</b> A start URL directly in the configuration
 *       (see {@link #setStartURLs(List)}).</li>
 *   <li><b>urlsFile:</b> A path to a file containing a list of start URLs
 *       (see {@link #setStartURLsFiles(List)}). One per line.</li>
 *   <li><b>sitemap:</b> A URL pointing to a sitemap XML file that contains
 *       the URLs to crawl (see {@link #setStartSitemapURLs(List)}).</li>
 *   <li><b>provider:</b> Your own class implementing
 *       {@link IStartURLsProvider} to dynamically provide a list of start
 *       URLs (see {@link #setStartURLsProviders(List)}).</li>
 * </ul>
 * <p>
 * <b>Scope: </b> To limit crawling to specific web domains, and avoid creating
 * many filters to that effect, you can tell the crawler to "stay" within
 * the web site "scope" with
 * {@link #setUrlCrawlScopeStrategy(URLCrawlScopeStrategy)}.
 * </p>
 *
 * <h3>URL Normalization</h3>
 * <p>
 * Pages on web sites are often referenced using different URL
 * patterns. Such URL variations can fool the crawler into downloading the
 * same document multiple times. To avoid this, URLs are "normalized". That is,
 * they are converted so they are always formulated the same way.
 * By default, the crawler only applies normalization in ways that are
 * semantically equivalent (see {@link GenericURLNormalizer}).
 * </p>
 *
 * <h3>Crawl Speed</h3>
 * <p>
 * <b>Be kind</b> to web sites you crawl. Being too aggressive can be
 * perceived as a cyber-attack by the targeted web site (e.g., DoS attack).
 * This can lead to your crawler being blocked.
 * </p>
 * <p>
 * For this reason, the crawler plays nice by default.  It will wait a
 * few seconds between each page download, regardless of the maximum
 * number of threads specified or whether pages crawled are on different
 * web sites. This can of course be changed to be as fast as you want.
 * See {@link GenericDelayResolver})
 * for changing default options. You can also provide your own "delay resolver"
 * by supplying a class implementing {@link IDelayResolver}.
 * </p>
 *
 * <h3>Crawl Depth</h3>
 * <p>
 * The crawl depth represents how many level from the start URL the crawler
 * goes. From a browser user perspective, it can be seen as the number of
 * link "clicks" required from a start URL in order to get to a specific page.
 * The crawler will crawl as deep for as long as it discovers new URLs
 * not getting rejected by your configuration.  This is not always desirable.
 * For instance, a web site could have dynamically generated URLs with infinite
 * possibilities (e.g., dynamically generated web calendars). To avoid
 * infinite crawls, it is recommended to limit the maximum depth to something
 * reasonable for your site with {@link #setMaxDepth(int)}.
 * </p>
 *
 * <h3>Keeping downloaded files</h3>
 * <p>
 * Downloaded files are deleted after being processed. Set
 * {@link #setKeepDownloads(boolean)} to <code>true</code> in order to preserve
 * them. Files will be kept under a new "downloads" folder found under
 * your working directory.  Keep in mind this is not a method for cloning a
 * site. Use with caution on large sites as it can quickly
 * fill up the local disk space.
 * </p>
 *
 * <h3>Keeping Referenced Links</h3>
 * <p>
 * By default the crawler stores, as metadata, URLs extracted from
 * documents that are in scope. Exceptions
 * are pages discovered at the configured maximum depth
 * ({@link #setMaxDepth(int)}).
 * This can be changed using the
 * {@link #setKeepReferencedLinks(Set)} method.
 * Changing this setting has no incidence on what page gets crawled.
 * Possible options are:
 * </p>
 * <ul>
 *   <li><b>INSCOPE:</b> Default. Store "in-scope" links as
 *       {@link HttpDocMetadata#REFERENCED_URLS}.</li>
 *   <li><b>OUTSCOPE:</b> Store "out-of-scope" links as
 *       {@link HttpDocMetadata#REFERENCED_URLS_OUT_OF_SCOPE}.</li>
 *   <li><b>MAXDEPTH:</b> Also store links extracted on pages at max depth.
 *       Must be used with at least one other option to have any effect.</li>
 * </ul>
 *
 * <h3>Orphan documents</h3>
 * <p>
 * Orphans are valid documents, which on subsequent crawls can no longer be
 * reached (e.g. there are no links pointing to that page anymore). This is
 * regardless whether the file has been deleted or not. You can tell the
 * crawler how to handle those with
 * {@link #setOrphansStrategy(OrphansStrategy)}. Possible options are:
 * </p>
 * <ul>
 *   <li><b>PROCESS:</b> Default. Tries to crawl orphan URLs normally
 *       as if they were still reachable by the crawler.</li>
 *   <li><b>IGNORE:</b> Does nothing with orphans
 *       (not deleted, not processed)..</li>
 *   <li><b>DELETE:</b> Orphans are sent to your Committer for deletion.</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <p>
 * By default the crawler tries report exceptions while preventing them
 * from terminating a crawling session. There might be cases where you want
 * the crawler to halt upon encountering some types of exceptions.
 * You can do so with {@link #setStopOnExceptions(List)}.
 * </p>
 *
 * <h3>Crawler Events</h3>
 * <p>
 * The crawler fires all kind of events to notify interested parties of such
 * things as when a document is rejected, imported, committed, etc.).
 * You can listen to crawler events using {@link #setEventListeners(List)}.
 * </p>
 *
 * <h3>Data Store (Cache)</h3>
 * <p>
 * During and between crawl sessions, the crawler needs to preserve
 * specific information in order to keep track of
 * things such as a queue of URLs to process, URLs already processed,
 * whether a document has been modified since last crawled,
 * caching of document checksums, etc.
 * For this the crawler uses a database we call a crawl data store engine.
 * The default implementation uses the local file system to store these
 * (see {@link MVStoreDataStoreEngine}). While very capable and suitable
 * for most sites, if you need a larger storage system, you can provide your
 * own implementation with {@link #setDataStoreEngine(IDataStoreEngine)}.
 * </p>
 *
 * <h3>HTTP Fetcher</h3>
 * <p>
 * Two crawl and parse a document, it needs to be downloaded. This is the
 * role of one or more HTTP Fetchers.  {@link GenericHttpFetcher} is the
 * default implementation and can handle most web sites.
 * There might be cases where a more specialized way of obtaining web resources
 * is needed. For instance, JavaScript-generated web pages are often best
 * handled by web browsers. In such case you can use the
 * {@link WebDriverHttpFetcher}. You can also use
 * {@link #setHttpFetchers(List)} to supply own fetcher implementation.
 * </p>
 * <p>
 * A fetcher typically issues an HTTP GET request to obtain a document.
 * There might be cases where you first want to issue a HEAD request first
 * (e.g., for filtering before download). You can enable HEAD requests for
 * fetchers supporting it using {@link #setFetchHttpHead(boolean)}.
 * </p>
 *
 * <h3>Filtering Unwanted Documents</h3>
 * <p>
 * If can often process URLs you are not interested in.  In other cases,
 * you may want to download an HTML page just for the links it contains to be
 * followed, but otherwise not send that page to your Committer.  For these
 * reasons and more, you will likely have to explicitly create filters
 * to restrict crawling to only what you are interested in.
 * There are different types filtering offered to you, occurring at different
 * type during a URL crawling process. The sooner in a URL processing
 * life-cycle you filter out a document the more you can improve the
 * crawler performance.  It may be important for you to
 * understand the differences:
 * </p>
 * <ul>
 *   <li>
 *     <b>Reference filters:</b> The fastest way to exclude a document.
 *     The filtering rule applies on the URL, before any HTTP request is made
 *     for that URL. Rejected documents are not queued for processing.
 *     They are not be downloaded (thus no URLs are extracted). The
 *     specified "delay" between downloads is not applied (i.e. no delay
 *     for rejected documents).
 *   </li>
 *   <li>
 *     <p>
 *     <b>Metadata filters:</b> Applies filtering on a document metadata fields.
 *     </p>
 *     <p>
 *     If {@link #isFetchHttpHead()} returns <code>true</code>, these filters
 *     will be invoked after the crawler performs a distinct HTTP HEAD request.
 *     It gives you the opportunity to filter documents based on the HTTP HEAD
 *     response to potentially save a more expensive HTTP GET request for
 *     download (but results in two HTTP requests for valid documents --
 *     HEAD and GET). Filtering occurs before URLs are extracted.
 *     </p>
 *     <p>
 *     When {@link #isFetchHttpHead()} is <code>false</code>, these filters
 *     will be invoked on the metadata of the HTTP response
 *     obtained from an HTTP GET request (as the document is downloaded).
 *     Filtering occurs after URLs are extracted.
 *     </p>
 *   </li>
 *   <li>
 *     <b>Document filters:</b> Use when having access to the document itself
 *     (and its content) is required to apply filtering. Always triggered
 *     after a document is downloaded and after URLs are extracted,
 *     but before it is imported (Importer module).
 *   </li>
 *   <li>
 *     <b>Importer filters:</b> The Importer module also offers document
 *     filtering options. At that point a document is already downloaded
 *     and its links extracted.  There are two types of filtering offered
 *     by the Importer: before and after document parsing.  Use
 *     filters before parsing if you need to filter on raw content or
 *     want to prevent a more expensive parsing. Use filters after parsing
 *     when you need to read the content as plain text.
 *   </li>
 * </ul>
 *
 * <h3>Robot Directives</h3>
 * <p>
 * By default, the crawler tries to respect instructions a web site as put
 * in place for the benefit of crawlers. Here is a list of some of the
 * popular ones that can be turned off or supports your own implementation.
 * </p>
 * <ul>
 *   <li>
 *     <b>Robot rules:</b> Rules defined in a "robots.txt" file at the
 *     root of a web site, or via <code>X-Robots-Tag</code>. See:
 *     {@link #setIgnoreRobotsTxt(boolean)},
 *     {@link #setRobotsTxtProvider(IRobotsTxtProvider)},
 *     {@link #setIgnoreRobotsMeta(boolean)},
 *     {@link #setRobotsMetaProvider(IRobotsMetaProvider)}
 *   </li>
 *   <li>
 *     <b>HTML "nofollow":</b> Most HTML-oriented link extractors support
 *     the <code>rel="nofollow"</code> attribute set on HTML links.
 *     See: {@link HtmlLinkExtractor#setIgnoreNofollow(boolean)}
 *   </li>
 *   <li>
 *     <b>Sitemap:</b> Sitemaps XML files are auto-detected and used to find
 *     a list of URLs to crawl.  To disable detection, use
 *     {@link #setIgnoreSitemap(boolean)}.</li>
 *   <li>
 *     <b>Canonical URLs:</b> The crawler will reject URLs that are
 *     non-canonical, as per HTML <code>&lt;meta ...&gt;</code> or
 *     HTTP response instructions.  To crawl non-canonical pages, use
 *     {@link #setIgnoreCanonicalLinks(boolean)}.
 *     </li>
 *   <li>
 *     <b>If Modified Since:</b> The default HTTP Fetcher
 *     ({@link GenericHttpFetcher}) uses the <code>If-Modified-Since</code>
 *     feature as part of its HTTP requests for web sites supporting it
 *     (only affects incremental crawls). To turn that off, use
 *     {@link GenericHttpFetcherConfig#setDisableIfModifiedSince(boolean)}.
 *   </li>
 * </ul>
 *
 * <h3>Re-crawl Frequency</h3>
 * <p>
 * The crawler will crawl any given URL at most one time per crawling session.
 * It is possible to skip documents that are not yet "ready" to be re-crawled
 * to speed up each crawling sessions.
 * Sitemap.xml directives to that effect are respected by default
 * ("frequency" and "lastmod"). You can have your own conditions for re-crawl
 * with {@link #setRecrawlableResolver(IRecrawlableResolver)}.
 * This feature can be used for instance, to crawl a "news" section of your
 * site more frequently than let's say, an "archive" section of your site.
 * </p>
 *
 * <h3>Document Checksum</h3>
 * <p>
 * To find out if a document has changed from one crawling session to another,
 * the crawler creates and keeps a digital signature, or checksum of each
 * crawled documents. Upon crawling the same URL again, a new checksum
 * is created and compared against the previous one. Any difference indicates
 * a modified document. There are two checksums at play, tested at
 * different times. One obtained from
 * a document metadata (default is {@link LastModifiedMetadataChecksummer},
 * and one from the document itself {@link MD5DocumentChecksummer}. You can
 * provide your own implementation. See:
 * {@link #setMetadataChecksummer(IMetadataChecksummer)} and
 * {@link #setDocumentChecksummer(IDocumentChecksummer)}.
 * </p>
 *
 * <h3>URL Extraction</h3>
 * <p>
 * To be able to crawl a web site, links need to be extracted from
 * web pages.  It is the job of a link extractor.  It is possible to use
 * multiple link extractor for different type of content.  By default,
 * the {@link HtmlLinkExtractor} is used, but you can add others or
 * provide your own with {@link #setLinkExtractors(List)}.
 * </p>
 * <p>
 * There might be
 * cases where you want a document to be parsed by the Importer and establish
 * which links to process yourself during the importing phase (for more
 * advanced use cases). In such cases, you can identify a document metadata
 * field to use as a URL holding tanks after importing has occurred.
 * URLs in that field will become eligible for crawling.
 * See {@link #setPostImportLinks(TextMatcher)}.
 * </p>
 *
 * <h3>Document Importing</h3>
 * <p>
 * The process of transforming, enhancing, parsing to extracting plain text
 * and many other document-specific processing activities are handled by the
 * Norconex Importer module. See {@link ImporterConfig} for many
 * additional configuration options.
 * </p>
 * <p>
 * There might be
 * cases where you want a document to be parsed by the Importer and establish
 * which links to process yourself during the importing phase (for more
 * advanced use cases). In such cases, you can identify a document metadata
 * field to use as a URL holding tanks after importing has occurred.
 * URLs in that field will become eligible for crawling.
 * See {@link #setPostImportLinks(TextMatcher)}.
 * </p>
 *
 * <h3>Bad Documents</h3>
 * <p>
 * On a fresh crawl, documents that are not found or not returned successfully
 * from the web server are simply logged and ignored.  On the other hand,
 * documents that were successfully crawled on a previous crawl and are
 * suddenly failing on a subsequent crawl are considered "spoiled".
 * You can decide whether to grace (retry next time), delete, or ignore
 * those spoiled documents with
 * {@link #setSpoiledReferenceStrategizer(ISpoiledReferenceStrategizer)}.
 * </p>
 *
 * <h3>Committing Documents</h3>
 * <p>
 * The last step of a successful processing of a web page or document is to
 * store it in your preferred target repository (or repositories).
 * For this to happen, you have to configure one or more Committers
 * corresponding to your needs or create a custom one.
 * You can have a look at available Committers here:
 * <a href="https://opensource.norconex.com/committers/">
 * https://opensource.norconex.com/committers/</a>
 * See {@link #setCommitters(List)}.
 * </p>
 *
 * {@nx.xml.usage
 * <crawler id="(crawler unique identifier)">
 *
 *   <startURLs
 *       stayOnDomain="[false|true]"
 *       includeSubdomains="[false|true]"
 *       stayOnPort="[false|true]"
 *       stayOnProtocol="[false|true]"
 *       async="[false|true]">
 *     <!-- All the following tags are repeatable. -->
 *     <url>(a URL)</url>
 *     <urlsFile>(local path to a file containing URLs)</urlsFile>
 *     <sitemap>(URL to a sitemap XML)</sitemap>
 *     <provider class="(IStartURLsProvider implementation)"/>
 *   </startURLs>
 *
 *   <urlNormalizer class="(IURLNormalizer implementation)" />
 *
 *   <delay class="(IDelayResolver implementation)"/>
 *
 *   <numThreads>(maximum number of threads)</numThreads>
 *   <maxDepth>(maximum crawl depth)</maxDepth>
 *   <maxDocuments>(maximum number of documents to crawl)</maxDocuments>
 *   <keepDownloads>[false|true]</keepDownloads>
 *   <keepReferencedLinks>[INSCOPE|OUTSCOPE|MAXDEPTH]</keepReferencedLinks>
 *   <orphansStrategy>[PROCESS|IGNORE|DELETE]</orphansStrategy>
 *
 *   <stopOnExceptions>
 *     <!-- Repeatable -->
 *     <exception>(fully qualified class name of a an exception)</exception>
 *   </stopOnExceptions>
 *
 *   <eventListeners>
 *     <!-- Repeatable -->
 *     <listener class="(IEventListener implementation)"/>
 *   </eventListeners>
 *
 *   <crawlDataStoreEngine class="(ICrawlURLDatabaseFactory implementation)" />
 *
 *   <httpFetchers>
 *     <!-- Repeatable -->
 *     <fetcher
 *         class="(IHttpFetcher implementation)" maxRetries="0" retryDelay="0"/>
 *   </httpFetchers>
 *
 *   <referenceFilters>
 *     <!-- Repeatable -->
 *     <filter
 *         class="(IReferenceFilter implementation)"
 *         onMatch="[include|exclude]" />
 *   </referenceFilters>
 *
 *   <robotsTxt
 *       ignore="[false|true]"
 *       class="(IRobotsMetaProvider implementation)"/>
 *
 *   <sitemapResolver
 *       ignore="[false|true]"
 *       class="(ISitemapResolver implementation)"/>
 *
 *   <redirectURLProvider class="(IRedirectURLProvider implementation)" />
 *
 *   <recrawlableResolver class="(IRecrawlableResolver implementation)" />
 *
 *   <metadataFilters>
 *     <!-- Repeatable -->
 *     <filter
 *         class="(IMetadataFilter implementation)"
 *         onMatch="[include|exclude]" />
 *   </metadataFilters>
 *
 *   <canonicalLinkDetector
 *       ignore="[false|true]"
 *       class="(ICanonicalLinkDetector implementation)"/>
 *
 *   <metadataChecksummer class="(IMetadataChecksummer implementation)" />
 *
 *   <robotsMeta
 *       ignore="[false|true]"
 *       class="(IRobotsMetaProvider implementation)" />
 *
 *   <linkExtractors>
 *     <!-- Repeatable -->
 *     <extractor class="(ILinkExtractor implementation)" />
 *   </linkExtractors>
 *
 *   <documentFilters>
 *     <!-- Repeatable -->
 *     <filter class="(IDocumentFilter implementation)" />
 *   </documentFilters>
 *
 *   <preImportProcessors>
 *     <!-- Repeatable -->
 *     <processor class="(IHttpDocumentProcessor implementation)"></processor>
 *   </preImportProcessors>
 *
 *   <importer>
 *     <preParseHandlers>
 *       <!-- Repeatable -->
 *       <handler class="(an handler class from the Importer module)"/>
 *     </preParseHandlers>
 *     <documentParserFactory class="(IDocumentParser implementation)" />
 *     <postParseHandlers>
 *       <!-- Repeatable -->
 *       <handler class="(an handler class from the Importer module)"/>
 *     </postParseHandlers>
 *     <responseProcessors>
 *       <!-- Repeatable -->
 *       <responseProcessor
 *              class="(IImporterResponseProcessor implementation)" />
 *     </responseProcessors>
 *   </importer>
 *
 *   <documentChecksummer class="(IDocumentChecksummer implementation)" />
 *
 *   <postImportProcessors>
 *     <!-- Repeatable -->
 *     <processor class="(IHttpDocumentProcessor implementation)"></processor>
 *   </postImportProcessors>
 *
 *   <postImportLinks keep="[false|true]">
 *     <fieldMatcher
 *       {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes} />
 *   </postImportLinks>
 *
 *   <spoiledReferenceStrategizer
 *       class="(ISpoiledReferenceStrategizer implementation)" />
 *
 *   <committers>
 *     <committer class="(ICommitter implementation)" />
 *   </committers>
 *
 * </crawler>
 * }
 *
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfig extends CrawlerConfig {

    // By default do not include URLs on docs at max depth
    // (and do not extract them).  Include MAXDEPTH for this.
    public enum ReferencedLinkType {
        INSCOPE, OUTSCOPE, MAXDEPTH;
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
    private final Set<ReferencedLinkType> keepReferencedLinks =
            new HashSet<>(Arrays.asList(ReferencedLinkType.INSCOPE));
	private boolean startURLsAsync;

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


    private boolean postImportLinksKeep;
    private TextMatcher postImportLinks = new TextMatcher();

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

    /**
     * Gets whether the start URLs should be loaded asynchronously. When
     * <code>true</code>, the crawler will start processing URLs in the queue
     * even if start URLs are still being loaded. While this may speed up
     * crawling, it may have an unexpected effect on accuracy of
     * {@link HttpDocMetadata#DEPTH}.  Use of this option is only
     * recommended when start URLs takes a significant time to load (e.g.,
     * large sitemaps).
     * @return <code>true</code> if async.
     * @since 3.0.0
     */
    public boolean isStartURLsAsync() {
        return startURLsAsync;
    }
    /**
     * Sets whether the start URLs should be loaded asynchronously. When
     * <code>true</code>, the crawler will start processing URLs in the queue
     * even if start URLs are still being loaded. While this may speed up
     * crawling, it may have an unexpected effect on accuracy of
     * {@link HttpDocMetadata#DEPTH}.  Use of this option is only
     * recommended when start URLs takes a significant time to load (e.g.,
     * large sitemaps).
     * @param asyncStartURLs <code>true</code> if async.
     * @since 3.0.0
     */
    public void setStartURLsAsync(boolean asyncStartURLs) {
        this.startURLsAsync = asyncStartURLs;
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
        return keepReferencedLinks.contains(ReferencedLinkType.OUTSCOPE);
    }
	/**
	 * Sets whether links not in scope should be stored as metadata
     * under {@link HttpDocMetadata#REFERENCED_URLS_OUT_OF_SCOPE}
     * @param keepOutOfScopeLinks <code>true</code> if keeping URLs not in scope
     * @since 2.8.0
     * @deprecated Since 3.0.0, use {@link #setKeepReferencedLinks(Set)}.
	 */
    @Deprecated
    public void setKeepOutOfScopeLinks(boolean keepOutOfScopeLinks) {
        if (keepOutOfScopeLinks) {
            keepReferencedLinks.add(ReferencedLinkType.OUTSCOPE);
        } else {
            keepReferencedLinks.remove(ReferencedLinkType.OUTSCOPE);
        }
    }

    /**
     * Gets what type of referenced links to keep, if any.
     * Those links are URLs extracted by link extractors. See class
     * documentation for more details.
     * @return preferences for keeping links
     * @since 3.0.0
     */
    public Set<ReferencedLinkType> getKeepReferencedLinks() {
        return Collections.unmodifiableSet(keepReferencedLinks);
    }
    /**
     * Sets whether to keep referenced links and what to keep.
     * Those links are URLs extracted by link extractors. See class
     * documentation for more details.
     * @param keepReferencedLinks option for keeping links
     * @since 3.0.0
     */
    public void setKeepReferencedLinks(
            Set<ReferencedLinkType> keepReferencedLinks) {
        CollectionUtil.setAll(this.keepReferencedLinks, keepReferencedLinks);
    }
    /**
     * Sets whether to keep referenced links and what to keep.
     * Those links are URLs extracted by link extractors. See class
     * documentation for more details.
     * @param keepReferencedLinks option for keeping links
     * @since 3.0.0
     */
    public void setKeepReferencedLinks(
            ReferencedLinkType... keepReferencedLinks) {
        CollectionUtil.setAll(this.keepReferencedLinks, keepReferencedLinks);
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

    /**
     * Gets a field matcher used to identify post-import metadata fields
     * holding URLs to consider for crawling.
     * @return field matcher
     * @since 3.0.0
     */
    public TextMatcher getPostImportLinks() {
        return postImportLinks;
    }
    /**
     * Set a field matcher used to identify post-import metadata fields
     * holding URLs to consider for crawling.
     * @param fieldMatcher field matcher
     * @since 3.0.0
     */
    public void setPostImportLinks(TextMatcher fieldMatcher) {
        this.postImportLinks.copyFrom(fieldMatcher);
    }
    /**
     * Gets whether to keep the importer-generated field holding URLs to
     * consider for crawling.
     * @return <code>true</code> if keeping
     * @since 3.0.0
     */
    public boolean isPostImportLinksKeep() {
        return postImportLinksKeep;
    }
    /**
     * Sets whether to keep the importer-generated field holding URLs to
     * consider for crawling.
     * @param postImportLinksKeep <code>true</code> if keeping
     * @since 3.0.0
     */
    public void setPostImportLinksKeep(boolean postImportLinksKeep) {
        this.postImportLinksKeep = postImportLinksKeep;
    }

    @Override
    protected void saveCrawlerConfigToXML(XML xml) {
        xml.addElement("maxDepth", maxDepth);
        xml.addElement("keepDownloads", keepDownloads);
        xml.addDelimitedElementList("keepReferencedLinks",
                new ArrayList<>(keepReferencedLinks));
        xml.addElement("fetchHttpHead", fetchHttpHead);

		XML startXML = xml.addElement("startURLs")
		        .setAttribute("stayOnProtocol",
		                urlCrawlScopeStrategy.isStayOnProtocol())
                .setAttribute("stayOnDomain",
                        urlCrawlScopeStrategy.isStayOnDomain())
                .setAttribute("includeSubdomains",
                        urlCrawlScopeStrategy.isIncludeSubdomains())
                .setAttribute("stayOnPort",
                        urlCrawlScopeStrategy.isStayOnPort())
                .setAttribute("async", startURLsAsync);
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

        postImportLinks.saveToXML(
                xml.addElement("postImportLinks")
                        .setAttribute("keep", postImportLinksKeep)
                        .addElement("fieldMatcher"));
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

        postImportLinks.loadFromXML(xml.getXML("postImportLinks/fieldMatcher"));
        postImportLinksKeep =
                xml.getBoolean("postImportLinks/@keep", postImportLinksKeep);

        // Removed/replaced version 2.x configuration options:
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

        setKeepReferencedLinks(new HashSet<>(xml.getDelimitedEnumList(
                "keepReferencedLinks", ReferencedLinkType.class,
                        new ArrayList<>(keepReferencedLinks))));
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
        setStartURLsAsync(xml.getBoolean("startURLs/@async", startURLsAsync));
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
