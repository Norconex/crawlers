/* Copyright 2010-2021 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * reached (e.g. there are no longer referenced). This is
 * regardless whether the file has been deleted or not at the source.
 * You can tell the crawler how to handle those with
 * {@link #setOrphansStrategy(OrphansStrategy)}. Possible options are:
 * </p>
 * <ul>
 *   <li><b>PROCESS:</b> Default. Tries to crawl orphans normally
 *       as if they were still reachable by the crawler.</li>
 *   <li><b>IGNORE:</b> Does nothing with orphans
 *       (not deleted, not processed)..</li>
 *   <li><b>DELETE:</b> Orphans are sent to your Committer for deletion.</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <p>
 * By default the crawler logs exceptions while trying to prevent them
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
 * things such as a queue of document reference to process,
 * those already processed, whether a document has been modified since last
 * crawled, caching of document checksums, etc.
 * For this, the crawler uses a database we call a crawl data store engine.
 * The default implementation uses the local file system to store these
 * (see {@link MVStoreDataStoreEngine}). While very capable and suitable
 * for most sites, if you need a larger storage system, you can provide your
 * own implementation with {@link #setDataStoreEngine(IDataStoreEngine)}.
 * </p>
 *
 * <h3>Document Importing</h3>
 * <p>
 * The process of transforming, enhancing, parsing to extracting plain text
 * and many other document-specific processing activities are handled by the
 * Norconex Importer module. See {@link ImporterConfig} for many
 * additional configuration options.
 * </p>
 *
 * <h3>Bad Documents</h3>
 * <p>
 * On a fresh crawl, documents that are unreachable or not obtained
 * successfully for some reason are simply logged and ignored.
 * On the other hand, documents that were successfully crawled once
 * and are suddenly failing on a subsequent crawl are considered "spoiled".
 * You can decide whether to grace (retry next time), delete, or ignore
 * those spoiled documents with
 * {@link #setSpoiledReferenceStrategizer(ISpoiledReferenceStrategizer)}.
 * </p>
 *
 * <h3>Committing Documents</h3>
 * <p>
 * The last step of a successful processing of a document is to
 * store it in your preferred target repository (or repositories).
 * For this to happen, you have to configure one or more Committers
 * corresponding to your needs or create a custom one.
 * You can have a look at available Committers here:
 * <a href="https://opensource.norconex.com/committers/">
 * https://opensource.norconex.com/committers/</a>
 * See {@link #setCommitters(List)}.
 * </p>
 *
 * <h3>HTTP Fetcher</h3>
 * <p>
 * To crawl and parse a document, it needs to be downloaded first. This is the
 * role of one or more HTTP Fetchers.  {@link GenericHttpFetcher} is the
 * default implementation and can handle most web sites.
 * There might be cases where a more specialized way of obtaining web resources
 * is needed. For instance, JavaScript-generated web pages are often best
 * handled by web browsers. In such case you can use the
 * {@link WebDriverHttpFetcher}. You can also use
 * {@link #setHttpFetchers(List)} to supply own fetcher implementation.
 * </p>
 *
 * <h3>HTTP Methods</h3>
 * <p>
 * A fetcher typically issues an HTTP GET request to obtain a document.
 * There might be cases where you first want to issue a separate HEAD request.
 * One example is to filter documents based on the HTTP HEAD response
 * information, thus possibly saving downloading large files you don't want.
 * </p>
 * <p>
 * You can tell the crawler how it should handle HTTP GET and HEAD requests
 * using using {@link #setFetchHttpGet(HttpMethodSupport)} and
 * {@link #setFetchHttpHead(HttpMethodSupport)} respectively.
 * For each, the options are:
 * </p>
 * <ul>
 *   <li><b>DISABLED:</b> No HTTP call willl be made using that method.</li>
 *   <li>
 *     <b>OPTIONAL:</b> If the HTTP method is not supported by any fetcher or
 *     the HTTP request for it was not successful, the document can still be
 *     processed successfully by the other HTTP method. Only relevant when
 *     both HEAD and GET are enabled.
 *   </li>
 *   <li>
 *     <b>REQUIRED:</b> If the HTTP method is not supported by any fetcher or
 *     the HTTP request for it was not successful, the document will be
 *     rejected and won't go any further,
 *     even if the other HTTP method was or could have been successful.
 *     Only relevant when both HEAD and GET are enabled.
 *   </li>
 * </ul>
 * <p>
 * If you enable only one HTTP method (default), then specifying
 * OPTIONAL or REQUIRED for it have the same effect.
 * At least one method needs to be enabled for an HTTP request to be attempted.
 * By default HEAD requests are DISABLED and GET are REQUIRED. If you are
 * unsure what settings to use, keep the defaults.
 * </p>
 *
 * <h3>Filtering Unwanted Documents</h3>
 * <p>
 * Without filtering, you would typically crawl many documents you are not
 * interested in.
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
 * <h3>Change Detection (Checksums)</h3>
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
 * <h3>Deduplication</h3>
 * <p>
 * <b>EXPERIMENTAL:</b>
 * The crawler can attempt to detect and reject documents considered as
 * duplicates within a crawler session.  A document will be considered
 * duplicate if there was already a document processed with the same
 * metadata or document checksum. To enable this feature, set
 * {@link #setMetadataDeduplicate(boolean)} and/or
 * {@link #setDocumentDeduplicate(boolean)} to <code>true</code>. Setting
 * those will have no effect if the corresponding checksummers are
 * not set (<code>null</code>).
 * </p>
 * <p>
 * Deduplication can impact crawl performance.  It is recommended you
 * use it only if you can't distinguish duplicates via other means
 * (URL normalizer, canonical URL support, etc.).  Also, you should only
 * enable this feature if you know your checksummer(s) will generate
 * a checksum that is acceptably unique to you.
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
 *   <urlNormalizers>
 *     <urlNormalizer class="(IURLNormalizer implementation)" />
 *   </urlNormalizers>
 *
 *   <delay class="(IDelayResolver implementation)"/>
 *
 *   <maxDepth>(maximum crawl depth)</maxDepth>
 *   <keepDownloads>[false|true]</keepDownloads>
 *   <keepReferencedLinks>[INSCOPE|OUTSCOPE|MAXDEPTH]</keepReferencedLinks>
 *
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#init}
 *
 *   <fetchHttpHead>[DISABLED|REQUIRED|OPTIONAL]</fetchHttpHead>
 *   <fetchHttpGet>[REQUIRED|DISABLED|OPTIONAL]</fetchHttpGet>
 *
 *   <httpFetchers
 *       maxRetries="(number of times to retry a failed fetch attempt)"
 *       retryDelay="(how many milliseconds to wait between re-attempting)">
 *     <!-- Repeatable -->
 *     <fetcher
 *         class="(IHttpFetcher implementation)"/>
 *   </httpFetchers>
 *
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#pipeline-queue}
 *
 *   <robotsTxt
 *       ignore="[false|true]"
 *       class="(IRobotsMetaProvider implementation)"/>
 *
 *   <sitemapResolver
 *       ignore="[false|true]"
 *       class="(ISitemapResolver implementation)"/>
 *
 *   <recrawlableResolver class="(IRecrawlableResolver implementation)" />
 *
 *   <canonicalLinkDetector
 *       ignore="[false|true]"
 *       class="(ICanonicalLinkDetector implementation)"/>
 *
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#checksum-meta}
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#dedup-meta}
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
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#pipeline-import}
 *
 *   <preImportProcessors>
 *     <!-- Repeatable -->
 *     <processor class="(IHttpDocumentProcessor implementation)"></processor>
 *   </preImportProcessors>
 *
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#import}
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#checksum-doc}
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#dedup-doc}
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
 *   {@nx.include com.norconex.collector.core.crawler.CrawlerConfig#pipeline-committer}
 * </crawler>
 * }
 *
 * @author Pascal Essiembre
 */
@SuppressWarnings("javadoc")
public class HttpCrawlerConfig extends CrawlerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(XML.class);

    // By default do not include URLs on docs at max depth
    // (and do not extract them).  Include MAXDEPTH for this.
    public enum ReferencedLinkType {
        INSCOPE, OUTSCOPE, MAXDEPTH;
    }

    public enum HttpMethodSupport {
        DISABLED, OPTIONAL, REQUIRED;
        public boolean is(HttpMethodSupport methodSupport) {
            // considers null as disabled.
            return (this == DISABLED && methodSupport == null)
                    || (this == methodSupport);
        }
        public static boolean isEnabled(HttpMethodSupport methodSupport) {
            return methodSupport == HttpMethodSupport.OPTIONAL
                    || methodSupport == HttpMethodSupport.REQUIRED;
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
    private final Set<ReferencedLinkType> keepReferencedLinks =
            new HashSet<>(Arrays.asList(ReferencedLinkType.INSCOPE));
	private boolean startURLsAsync;

	private HttpMethodSupport fetchHttpHead = HttpMethodSupport.DISABLED;
    private HttpMethodSupport fetchHttpGet = HttpMethodSupport.REQUIRED;

    private URLCrawlScopeStrategy urlCrawlScopeStrategy =
            new URLCrawlScopeStrategy();

    private final List<IURLNormalizer> urlNormalizers =
            new ArrayList<>(Arrays.asList(new GenericURLNormalizer()));

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

    private final List<IHttpDocumentProcessor> preImportProcessors =
            new ArrayList<>();
    private final List<IHttpDocumentProcessor> postImportProcessors =
            new ArrayList<>();

    private IRecrawlableResolver recrawlableResolver =
            new GenericRecrawlableResolver();

    public HttpCrawlerConfig() {
        setMetadataChecksummer(new LastModifiedMetadataChecksummer());
    }

    /**
     * Deprecated.
     * @deprecated Use {@link #getFetchHttpHead()}.
     * @return <code>true</code> if fetching HTTP response headers separately
     * @since 3.0.0-M1
     */
    @Deprecated
    public boolean isFetchHttpHead() {
        return fetchHttpHead != null
                && fetchHttpHead != HttpMethodSupport.DISABLED;
    }
    /**
     * Deprecated.
     * @deprecated Use {@link #setFetchHttpHead(HttpMethodSupport)}.
     * @param fetchHttpHead <code>true</code>
     *        if fetching HTTP response headers separately
     * @since 3.0.0-M1
     */
    @Deprecated
    public void setFetchHttpHead(boolean fetchHttpHead) {
        this.fetchHttpHead = HttpMethodSupport.REQUIRED;
    }

    /**
     * <p>
     * Gets whether to fetch HTTP response headers using an
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4">
     * HTTP HEAD</a> request.  That HTTP request is performed separately from
     * a document download request (HTTP "GET"). Useful when you need to filter
     * documents based on HTTP header values, without downloading them first
     * (e.g., to save bandwidth).
     * When dealing with small documents on average, it may be best to
     * avoid issuing two requests when a single one could do it.
     * </p>
     * <p>
     * {@link HttpMethodSupport#DISABLED} by default.
     * See class documentation for more details.
     * <p>
     * @return HTTP HEAD method support
     * @since 3.0.0
     */
    public HttpMethodSupport getFetchHttpHead() {
        return fetchHttpHead;
    }
    /**
     * <p>
     * Sets whether to fetch HTTP response headers using an
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4">
     * HTTP HEAD</a> request.
     * </p>
     * <p>
     * See class documentation for more details.
     * <p>
     * @param fetchHttpHead HTTP HEAD method support
     * @since 3.0.0
     */
    public void setFetchHttpHead(HttpMethodSupport fetchHttpHead) {
        this.fetchHttpHead = fetchHttpHead;
    }
    /**
     * <p>
     * Gets whether to fetch HTTP documents using an
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3">
     * HTTP GET</a> request.
     * Requests made using the HTTP GET method are usually required
     * to download a document and have its content extracted and links
     * discovered. It should never be disabled unless you have an
     * exceptional use case.
     * </p>
     * <p>
     * {@link HttpMethodSupport#REQUIRED} by default.
     * See class documentation for more details.
     * <p>
     * @return <code>true</code> if fetching HTTP response headers separately
     * @since 3.0.0
     */
    public HttpMethodSupport getFetchHttpGet() {
        return fetchHttpGet;
    }
    /**
     * <p>
     * Sets whether to fetch HTTP documents using an
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3">
     * HTTP GET</a> request.
     * Requests made using the HTTP GET method are usually required
     * to download a document and have its content extracted and links
     * discovered. It should never be disabled unless you have an
     * exceptional use case.
     * </p>
     * <p>
     * See class documentation for more details.
     * <p>
     * @param fetchHttpGet <code>true</code>
     *        if fetching HTTP response headers separately
     * @since 3.0.0
     */
    public void setFetchHttpGet(HttpMethodSupport fetchHttpGet) {
        this.fetchHttpGet = fetchHttpGet;
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
        startURLsAsync = asyncStartURLs;
    }

    public void setMaxDepth(int depth) {
        maxDepth = depth;
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

    /**
     * @deprecated Since 3.1.0, use {@link #getUrlNormalizers()} instead.
     * @return URL normalizer
     */
    @Deprecated(forRemoval = true, since = "3.1.0")
    public IURLNormalizer getUrlNormalizer() {
        if (urlNormalizers.isEmpty()) {
            return null;
        }
        return urlNormalizers.get(0);
    }
    /**
     * @deprecated Since 3.1.0, use {@link #setUrlNormalizers(List)} instead.
     * @param urlNormalizer URL normalizer
     */
    @Deprecated(forRemoval = true, since = "3.1.0")
    public void setUrlNormalizer(IURLNormalizer urlNormalizer) {
        urlNormalizers.clear();
        if (urlNormalizer != null) {
            urlNormalizers.add(urlNormalizer);
        }
    }
    /**
     * Gets URL normalizers. Defaults to a single
     * {@link GenericURLNormalizer} instance (with its default configuration).
     * @return URL normalizers or an empty list (never <code>null</code>)
     * @since 3.1.0
     */
    public List<IURLNormalizer> getUrlNormalizers() {
        return Collections.unmodifiableList(urlNormalizers);
    }
    /**
     * Sets URL normalizers.
     * @param urlNormalizers URL normalizers
     * @since 3.1.0
     */
    public void setUrlNormalizers(List<IURLNormalizer> urlNormalizers) {
        CollectionUtil.setAll(this.urlNormalizers, urlNormalizers);
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
        postImportLinks.copyFrom(fieldMatcher);
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
        xml.addElement("fetchHttpGet", fetchHttpGet);

		var startXML = xml.addElement("startURLs")
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

        xml.addElement("urlNormalizers")
                .addElementList("urlNormalizer", urlNormalizers);

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

//        // Metadata Checksummer
//        setMetadataChecksummer(xml.getObjectImpl(IMetadataChecksummer.class,
//                "metadataChecksummer", metadataChecksummer));

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

        xml.checkDeprecated(
                "urlNormalizer", "urlNormalizers/urlNormalizer", false);
        var deprectedNormalizer = xml.getObjectImpl(
                IURLNormalizer.class, "urlNormalizer");
        if (deprectedNormalizer != null) {
            setUrlNormalizers(
                    Arrays.asList((IURLNormalizer) deprectedNormalizer));
        } else {
            setUrlNormalizers(xml.getObjectListImpl(
                    IURLNormalizer.class,
                    "urlNormalizers/urlNormalizer",
                    urlNormalizers));
        }
        setDelayResolver(xml.getObjectImpl(
                IDelayResolver.class, "delay", delayResolver));
        setMaxDepth(xml.getInteger("maxDepth", maxDepth));
        setKeepDownloads(xml.getBoolean("keepDownloads", keepDownloads));

        var fetchHttpHeadValue = xml.getString("fetchHttpHead", null);
        if (StringUtils.equalsAnyIgnoreCase(
                fetchHttpHeadValue, "true", "false")) {
            LOG.warn("Configuring 'fetchHttpHead' with a boolean value has "
                    + "been deprecated. It now expects one of "
                    + "DISABLED, OPTIONAL, or REQUIRED.");
            setFetchHttpHead(Boolean.parseBoolean(fetchHttpHeadValue));
        }
        setFetchHttpHead(xml.getEnum(
                "fetchHttpHead", HttpMethodSupport.class, fetchHttpHead));
        setFetchHttpGet(xml.getEnum(
                "fetchHttpGet", HttpMethodSupport.class, fetchHttpGet));

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
        if ((other == null) || (this.getClass() != other.getClass())) {
            return false;
        }
        var that = (HttpCrawlerConfig) other;
        return new EqualsBuilder()
                .appendSuper(true)
                .append(canonicalLinkDetector, that.canonicalLinkDetector)
                .append(delayResolver, that.delayResolver)
                .append(fetchHttpGet, that.fetchHttpGet)
                .append(fetchHttpHead, that.fetchHttpHead)
                .append(httpFetchers, that.httpFetchers)
                .append(httpFetchersMaxRetries, that.httpFetchersMaxRetries)
                .append(httpFetchersRetryDelay, that.httpFetchersRetryDelay)
                .append(ignoreCanonicalLinks, that.ignoreCanonicalLinks)
                .append(ignoreRobotsMeta, that.ignoreRobotsMeta)
                .append(ignoreRobotsTxt, that.ignoreRobotsTxt)
                .append(ignoreSitemap, that.ignoreSitemap)
                .append(keepDownloads, that.keepDownloads)
                .append(keepReferencedLinks, that.keepReferencedLinks)
                .append(linkExtractors, that.linkExtractors)
                .append(maxDepth, that.maxDepth)
                .append(postImportLinks, that.postImportLinks)
                .append(postImportLinksKeep, that.postImportLinksKeep)
                .append(postImportProcessors, that.postImportProcessors)
                .append(preImportProcessors, that.preImportProcessors)
                .append(recrawlableResolver, that.recrawlableResolver)
                .append(robotsMetaProvider, that.robotsMetaProvider)
                .append(robotsTxtProvider, that.robotsTxtProvider)
                .append(sitemapResolver, that.sitemapResolver)
                .append(startSitemapURLs, that.startSitemapURLs)
                .append(startURLs, that.startURLs)
                .append(startURLsAsync, that.startURLsAsync)
                .append(startURLsFiles, that.startURLsFiles)
                .append(startURLsProviders, that.startURLsProviders)
                .append(urlCrawlScopeStrategy, that.urlCrawlScopeStrategy)
                .append(urlNormalizers, that.urlNormalizers)
                .build();
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
