/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.crawler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.checksum.DocumentChecksummer;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.ReferencesProvider;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.store.impl.mvstore.MVStoreDataStoreEngine;
import com.norconex.crawler.web.canon.CanonicalLinkDetector;
import com.norconex.crawler.web.canon.impl.GenericCanonicalLinkDetector;
import com.norconex.crawler.web.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.crawler.web.delay.DelayResolver;
import com.norconex.crawler.web.delay.impl.GenericDelayResolver;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcherConfig;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcher;
import com.norconex.crawler.web.link.LinkExtractor;
import com.norconex.crawler.web.link.impl.HtmlLinkExtractor;
import com.norconex.crawler.web.recrawl.RecrawlableResolver;
import com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver;
import com.norconex.crawler.web.robot.RobotsMetaProvider;
import com.norconex.crawler.web.robot.RobotsTxtProvider;
import com.norconex.crawler.web.robot.impl.StandardRobotsMetaProvider;
import com.norconex.crawler.web.robot.impl.StandardRobotsTxtProvider;
import com.norconex.crawler.web.sitemap.SitemapLocator;
import com.norconex.crawler.web.sitemap.SitemapResolver;
import com.norconex.crawler.web.sitemap.impl.GenericSitemapLocator;
import com.norconex.crawler.web.sitemap.impl.GenericSitemapResolver;
import com.norconex.crawler.web.url.WebURLNormalizer;
import com.norconex.crawler.web.url.impl.GenericURLNormalizer;
import com.norconex.importer.ImporterConfig;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * Web Crawler configuration.
 * </p>
 * <h3>Start URLs</h3>
 * <p>
 * Crawling begins with specifying one or more references to either documents
 * or starting points to documents you want to crawl. For a web crawl, those
 * references are URLs (e.g., web site home page, sitemap, etc.).
 * There are different ways to specify one or more "start" references
 * (repeatable, shortened to "ref"):
 * </p>
 * <ul>
 *   <li><b>ref:</b> Any regular URL
 *       (see {@link #setStartReferences(List)}).</li>
 *   <li><b>refsFile:</b> A path to a file containing a list of start URLs
 *       (see {@link #setStartReferencesFiles(java.nio.file.Path...)}).
 *       One per line.</li>
 *   <li><b>sitemap:</b> A URL pointing to a sitemap XML file that contains
 *       the URLs to crawl (see {@link #setStartReferencesSitemaps(List)}).</li>
 *   <li><b>provider:</b> Your own class implementing
 *       {@link ReferencesProvider} to dynamically provide a list of start
 *       URLs (see {@link #setStartReferencesProviders(List)}).</li>
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
 * by supplying a class implementing {@link DelayResolver}.
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
 *       {@link WebDocMetadata#REFERENCED_URLS}.</li>
 *   <li><b>OUTSCOPE:</b> Store "out-of-scope" links as
 *       {@link WebDocMetadata#REFERENCED_URLS_OUT_OF_SCOPE}.</li>
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
 * things such as a queue of document references to process,
 * those already processed, whether a document has been modified since last
 * crawled, caching of document checksums, etc.
 * For this, the crawler uses a database we refer to as a data store engine.
 * The default implementation uses the local file system to store these
 * (see {@link MVStoreDataStoreEngine}). While very capable and suitable
 * for most sites, if you need a larger storage system, you can change
 * the default implementation or provide your own
 * with {@link #setDataStoreEngine(IDataStoreEngine)}.
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
 * To crawl and parse a document, it first needs to be downloaded. This is the
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
 * using using {@link #setDocumentFetchSupport(FetchDirectiveSupport) and
 * {@link #setMetadataFetchSupport(FetchDirectiveSupport)} respectively.
 * For each, the options are:
 * </p>
 * <ul>
 *   <li><b>DISABLED:</b> No HTTP call will be made using that method.</li>
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
 * time during a URL crawling process. The sooner in a URL processing
 * life-cycle you filter out a document the more you can improve the
 * crawler performance.  It may be important for you to
 * understand the differences:
 * </p>
 * <ul>
 *   <li>
 *     <b>Reference filters:</b> The fastest way to exclude a document.
 *     The filtering rule applies on the URL, before any HTTP request is made
 *     for that URL. Rejected documents are not queued for processing.
 *     They are not downloaded (thus no URLs are extracted). The
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
 *     want to prevent an expensive parsing. Use filters after parsing
 *     when you need to read the content as plain text.
 *   </li>
 * </ul>
 *
 * <h3>Robot Directives</h3>
 * <p>
 * By default, the crawler tries to respect instructions a web site has put
 * in place for the benefit of crawlers. The following is a list of some of the
 * popular ones. Where <code>null</code> can be set to disable support
 * for specific instructions, you can achieve the equivalent in XML
 * configuration by declaring the corresponding option as a self-closed tag.
 * </p>
 * <ul>
 *   <li>
 *     <b>"robots.txt" rules:</b> Rules defined in a "robots.txt" file at the
 *     root of a web site.
 *     Defaults to {@link StandardRobotsTxtProvider}.
 *     Set to <code>null</code> via
 *     {@link #setRobotsTxtProvider(RobotsTxtProvider)} to disable
 *     support for "robots.txt" rules.
 *   </li>
 *   <li>
 *     <b>Robots metadata rules:</b> Rules provided via the HTTP response
 *     header <code>X-Robots-Tag</code> for a given document.
 *     Defaults to {@link StandardRobotsMetaProvider}.
 *     Set to <code>null</code> via
 *     {@link #setRobotsMetaProvider(RobotsMetaProvider)} to disable
 *     support for robots metadata rules.
 *   </li>
 *   <li>
 *     <b>HTML "nofollow":</b> Most HTML-oriented link extractors support
 *     the <code>rel="nofollow"</code> attribute set on HTML links and offer
 *     a way to disable this instruction. E.g.,
 *     {@link HtmlLinkExtractor#setIgnoreNofollow(boolean)}.
 *   </li>
 *   <li>
 *     <b>Sitemap:</b> Sitemaps XML files contain as listing of
 *     website URLs typically worth crawling.  They can be detected by
 *     looking at usual website locations or via robots.txt instructions, or
 *     they can be specified via {@link #setStartReferencesSitemaps(List)}.
 *     Defaults to {@link GenericSitemapResolver}, which
 *     offers support for disabling sitemap detection to rely only
 *     on sitemap start references.
 *     Setting it to <code>null</code> via
 *     {@link #setSitemapResolver(SitemapResolver_OLD) effectively disables
 *     sitemap support altogether, and is thus incompatible with sitemaps
 *     specified as start references.
*    </li>
 *   <li>
 *     <b>Canonical URLs:</b> The crawler will reject URLs that are
 *     non-canonical, as per HTML <code>&lt;meta ...&gt;</code> or
 *     HTTP response instructions.
 *     Defaults to {@link GenericCanonicalLinkDetector}.
 *     Set to <code>null</code> via
 *     {@link #setCanonicalLinkDetector(CanonicalLinkDetector) to disable
 *     support canonical links (increasing the chance of getting duplicates).
 *     </li>
 *   <li>
 *     <b>Fetcher-specific:</b> Fetcher implementations may support additional
 *     web site instructions with corresponding configuration options.
 *     For example, the default HTTP Fetcher ({@link GenericHttpFetcher})
 *     supports the <code>If-Modified-Since</code> for web sites supporting it
 *     (only affects incremental crawls). To turn that off, use
 *     {@link GenericHttpFetcherConfig#setIfModifiedSinceDisabled(boolean)}.
 *     See fetcher documentation for additional options.
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
 * with {@link #setRecrawlableResolver(RecrawlableResolver)}.
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
 * {@link #setMetadataChecksummer(MetadataChecksummer)} and
 * {@link #setDocumentChecksummer(DocumentChecksummer)}.
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
 * <code>null</code>.
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
 *   <start
 *       stayOnDomain="[false|true]"
 *       includeSubdomains="[false|true]"
 *       stayOnPort="[false|true]"
 *       stayOnProtocol="[false|true]"
 *       async="[false|true]"
 *       stayOnSitemap="[false|true]">
 *     <!-- All the following tags are repeatable. -->
 *     <ref>(a URL)</ref>
 *     <refsFile>(local path to a file containing URLs)</refsFile>
 *     <sitemap>(URL to a sitemap XML)</sitemap>
 *     <provider class="(StartURLsProvider implementation)"/>
 *   </start>
 *
 *   <urlNormalizer class="(URLNormalizer implementation)" />
 *
 *   <delay class="(DelayResolver implementation)"/>
 *
 *   <maxDepth>(maximum crawl depth)</maxDepth>
 *   <keepReferencedLinks>[INSCOPE|OUTSCOPE|MAXDEPTH]</keepReferencedLinks>
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#init}
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#directive-meta}
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#directive-doc}
 *
 *   <httpFetchers
 *       maxRetries="(number of times to retry a failed fetch attempt)"
 *       retryDelay="(how many milliseconds to wait between re-attempting)">
 *     <!-- Repeatable -->
 *     <fetcher
 *         class="(HttpFetcher implementation)"/>
 *   </httpFetchers>
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#pipeline-queue}
 *
 *   <robotsTxt class="(RobotsMetaProvider implementation)"/>
 *
 *   <sitemapResolver class="(SitemapResolver_OLD implementation)"/>
 *
 *   <recrawlableResolver class="(RecrawlableResolver implementation)" />
 *
 *   <canonicalLinkDetector class="(CanonicalLinkDetector implementation)"/>
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#checksum-meta}
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#dedup-meta}
 *
 *   <robotsMeta class="(RobotsMetaProvider implementation)" />
 *
 *   <linkExtractors>
 *     <!-- Repeatable -->
 *     <extractor class="(LinkExtractor implementation)" />
 *   </linkExtractors>
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#pipeline-import}
 *
 *   <preImportProcessors>
 *     <!-- Repeatable -->
 *     <processor class="(WebDocumentProcessor implementation)"></processor>
 *   </preImportProcessors>
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#import}
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#checksum-doc}
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#dedup-doc}
 *
 *   <postImportProcessors>
 *     <!-- Repeatable -->
 *     <processor class="(WebDocumentProcessor implementation)"></processor>
 *   </postImportProcessors>
 *
 *   <postImportLinks keep="[false|true]">
 *     <fieldMatcher
 *       {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes} />
 *   </postImportLinks>
 *
 *   {@nx.include com.norconex.crawler.core.crawler.CrawlerConfig#pipeline-committer}
 * </crawler>
 * }
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants

//TODO Given we don't need @schema here to pick up javadoc
// when we have the maven-compiler-plugin setup properly...
// do we need to include all compile maven dependencies we are including for open api?
// maybe need to add to maven-compiler plugin the swagger stuff, like in core?
//@Schema //(description = "Web crawler configuration.")
public class WebCrawlerConfig extends CrawlerConfig {

    /**
     * Flags for storing as metadata a page referenced links.
     * Default will only store linked that are "in-scope".
     * See class documentation for details.
     */
    public enum ReferencedLinkType {
        INSCOPE, OUTSCOPE, MAXDEPTH;
    }

    private final List<String> startReferencesSitemaps = new ArrayList<>();

    private final Set<ReferencedLinkType> keepReferencedLinks =
            new HashSet<>(Arrays.asList(ReferencedLinkType.INSCOPE));

    /**
     * The strategy to use to determine if a URL is in scope.
     * @param urlCrawlScopeStrategy strategy to use
     * @return the strategy
     */
    private URLCrawlScopeStrategy urlCrawlScopeStrategy =
            new URLCrawlScopeStrategy();

    /**
     * The URL normalizer. Defaults to {@link GenericURLNormalizer}.
     * @param urlNormalizer URL normalizer
     * @return URL normalizer
     */
    private WebURLNormalizer urlNormalizer = new GenericURLNormalizer();

    /**
     * The delay resolver dictating the minimum amount of time to wait
     * between web requests. Defaults to {@link GenericDelayResolver}.
     * @param delayResolver delay resolver
     * @return delay resolver
     */
    private DelayResolver delayResolver = new GenericDelayResolver();

    /**
     * The canonical link detector. To disable canonical link detection,
     * use {@link #setIgnoreCanonicalLinks(boolean)}.
     * Defaults to {@link GenericCanonicalLinkDetector}.
     * @param canonicalLinkDetector the canonical link detector
     * @return the canonical link detector
     */
    private CanonicalLinkDetector canonicalLinkDetector =
            new GenericCanonicalLinkDetector();

    private final List<LinkExtractor> linkExtractors =
            new ArrayList<>(Arrays.asList(new HtmlLinkExtractor()));

    private TextMatcher postImportLinks = new TextMatcher();
    /**
     * Whether to keep the Importer-generated field holding URLs to queue
     * for further crawling.
     * @param postImportLinksKeep <code>true</code> if keeping
     * @return <code>true</code> if keeping
     * @see #setPostImportLinks(TextMatcher)
     */
    private boolean postImportLinksKeep;

    /**
     * Whether to limit crawling to entries found in an existing website
     * sitemap (do not go deeper). Only applies if a sitemap is present
     * for a website.
     * This option is similar to specifying a sitemap start URL with a
     * <code>maxDepth</code> of <code>1</code> with the difference that when
     * used with regular start URLs and no sitemap is detected, it will crawl
     * the corresponding website as if this option was set to
     * <code>false</code>.
     * Does not apply if sitemap support has been disabled in your
     * configuration.
     * Note that if <code>async</code> is <code>true</code>, you may get
     * a few false rejection events until for documents not yet encountered
     * in the sitemap.
     */
    private boolean stayOnSitemap;

    /**
     * The provider of robots.txt rules for a site (if applicable).
     * Defaults to {@link StandardRobotsTxtProvider}.
     * Set to <code>null</code> to disable.
     * @param robotsTxtProvider robots.txt provider
     * @return robots.txt provider
     * @see #setIgnoreRobotsTxt(boolean)
     */
    private RobotsTxtProvider robotsTxtProvider =
            new StandardRobotsTxtProvider();

    /**
     * The provider of robots metadata rules for a page (if applicable).
     * Defaults to {@link StandardRobotsMetaProvider}.
     * Set to <code>null</code> to disable.
     * @param robotsMetaProvider robots metadata rules
     * @return robots metadata rules r
     * @see #setIgnoreRobotsMeta(boolean)
     */
    private RobotsMetaProvider robotsMetaProvider =
            new StandardRobotsMetaProvider();

    /**
     * The resolver of web site sitemaps (if applicable).
     * Defaults to {@link GenericSitemapResolver}.
     * Set to <code>null</code> to disable all sitemap support, or
     * see class documentation to disable sitemap detection only.
     * @param sitemapResolver sitemap resolver
     * @return sitemap resolver
     * @see SitemapLocator
     */
    private SitemapResolver sitemapResolver = new GenericSitemapResolver();

    /**
     * The locator of sitemaps (if applicable).
     * Defaults to {@link GenericSitemapLocator}.
     * Set to <code>null</code> to disable locating sitemaps
     * (relying on sitemaps defined as start reference, if any).
     * @param sitemapLocator sitemap locator
     * @return sitemap locator
     * @see SitemapResolver
     */
    private SitemapLocator sitemapLocator = new GenericSitemapLocator();

    /**
     * The resolver that indicates whether a given URL is ready to be
     * crawled by a new crawl session. Usually amounts to checking if enough
     * time has passed between two crawl sessions.
     * Defaults to {@link GenericRecrawlableResolver}.
     * @param robotsMetaProvider recrawlable resolver
     * @return recrawlableResolver recrawlable resolver
     */
    private RecrawlableResolver recrawlableResolver =
            new GenericRecrawlableResolver();

    public WebCrawlerConfig() {
        setMetadataChecksummer(new LastModifiedMetadataChecksummer());
        setFetchers(List.of(new GenericHttpFetcher()));
    }

    //--- Accessors ------------------------------------------------------------

    /**
     * Gets sitemap URLs to be used as starting points for crawling.
     * @return sitemap URLs (never <code>null</code>)
     * @since 2.3.0
     */
    public List<String> getStartReferencesSitemaps() {
        return Collections.unmodifiableList(startReferencesSitemaps);
    }
    /**
     * Sets the sitemap URLs used as starting points for crawling.
     * @param startReferencesSitemaps sitemap URLs
     * @since 2.3.0
     */
    @JsonIgnore
    public void setStartReferencesSitemaps(String... startReferencesSitemaps) {
        setStartReferencesSitemaps(Arrays.asList(startReferencesSitemaps));
    }
    /**
     * Sets the sitemap URLs used as starting points for crawling.
     * @param startReferencesSitemaps sitemap URLs
     * @since 3.0.0
     */
    public void setStartReferencesSitemaps(
            List<String> startReferencesSitemaps) {
        CollectionUtil.setAll(
                this.startReferencesSitemaps, startReferencesSitemaps);
        CollectionUtil.removeBlanks(this.startReferencesSitemaps);
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
    @JsonIgnore
    public void setKeepReferencedLinks(
            ReferencedLinkType... keepReferencedLinks) {
        CollectionUtil.setAll(this.keepReferencedLinks, keepReferencedLinks);
    }

    /**
     * Gets link extractors.
     * @return link extractors
     */
    public List<LinkExtractor> getLinkExtractors() {
        return Collections.unmodifiableList(linkExtractors);
    }
    /**
     * Sets link extractors.
     * @param linkExtractors link extractors
     */
    @JsonIgnore
    public void setLinkExtractors(LinkExtractor... linkExtractors) {
        setLinkExtractors(Arrays.asList(linkExtractors));
    }
    /**
     * Sets link extractors.
     * @param linkExtractors link extractors
     * @since 3.0.0
     */
    public void setLinkExtractors(List<LinkExtractor> linkExtractors) {
        CollectionUtil.setAll(this.linkExtractors, linkExtractors);
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

    @Override
    public void saveToXML(XML xml) {
        super.saveToXML(xml);
        xml.addDelimitedElementList("keepReferencedLinks",
                new ArrayList<>(keepReferencedLinks));

        var startXML = xml.computeElementIfAbsent("start", null)
                .setAttribute("stayOnProtocol",
                        urlCrawlScopeStrategy.isStayOnProtocol())
                .setAttribute("stayOnDomain",
                        urlCrawlScopeStrategy.isStayOnDomain())
                .setAttribute("includeSubdomains",
                        urlCrawlScopeStrategy.isIncludeSubdomains())
                .setAttribute("stayOnPort",
                        urlCrawlScopeStrategy.isStayOnPort())
                .setAttribute("stayOnSitemap",
                        stayOnSitemap);

        startXML.addElementList("sitemap", startReferencesSitemaps);

        xml.addElement("urlNormalizer", urlNormalizer);
        xml.addElement("delay", delayResolver);
        xml.addElement("robotsTxt", robotsTxtProvider);
        xml.addElement("sitemapResolver", sitemapResolver);
        xml.addElement(Fields.sitemapLocator, sitemapLocator);
        xml.addElement("canonicalLinkDetector", canonicalLinkDetector);
        xml.addElement("recrawlableResolver", recrawlableResolver);

        xml.addElement("robotsMeta", robotsMetaProvider);
        xml.addElementList("linkExtractors", "extractor", linkExtractors);

        postImportLinks.saveToXML(
                xml.addElement("postImportLinks")
                        .setAttribute("keep", postImportLinksKeep)
                        .addElement("fieldMatcher"));
    }

    @Override
    public void loadFromXML(XML xml) {
        super.loadFromXML(xml);

        // Simple Settings
        loadSimpleSettings(xml);

        // RobotsTxt provider
        setRobotsTxtProvider(xml.getObjectImpl(
                RobotsTxtProvider.class, "robotsTxt", robotsTxtProvider));

        // Sitemap Resolver
        setSitemapResolver(xml.getObjectImpl(
                SitemapResolver.class,
                Fields.sitemapResolver, sitemapResolver));
        setSitemapLocator(xml.getObjectImpl(
                SitemapLocator.class, Fields.sitemapLocator, sitemapLocator));

        // Canonical Link Detector
        setCanonicalLinkDetector(xml.getObjectImpl(CanonicalLinkDetector.class,
                "canonicalLinkDetector", canonicalLinkDetector));

        // Recrawlable resolver
        setRecrawlableResolver(xml.getObjectImpl(RecrawlableResolver.class,
                "recrawlableResolver", recrawlableResolver));

        // RobotsMeta provider
        setRobotsMetaProvider(xml.getObjectImpl(
                RobotsMetaProvider.class, "robotsMeta", robotsMetaProvider));

        // Link Extractors
        setLinkExtractors(xml.getObjectListImpl(LinkExtractor.class,
                "linkExtractors/extractor", linkExtractors));

        postImportLinks.loadFromXML(xml.getXML("postImportLinks/fieldMatcher"));
        postImportLinksKeep =
                xml.getBoolean("postImportLinks/@keep", postImportLinksKeep);

        // Removed/replaced version 2.x configuration options:
        xml.checkDeprecated("httpClientFactory", "httpFetchers/fetcher", true);
        xml.checkDeprecated("metadataFetcher", "httpFetchers/fetcher", true);
        xml.checkDeprecated("documentFetcher", "httpFetchers/fetcher", true);
        xml.checkDeprecated("redirectURLProvider",
                """
                    'redirectURLProvider' under 'httpFetchers/fetcher' for\s\
                    com.norconex.crawler.web.fetch.impl\
                    .GenericHttpFetcher""", true);
        xml.checkDeprecated("userAgent",
                """
                    'userAgent' under 'httpFetchers/fetcher' for\s\
                    com.norconex.crawler.web.fetch.impl\
                    .GenericHttpFetcher and possibly other fetchers""",
                        true);
    }

    private void loadSimpleSettings(XML xml) {
        xml.checkDeprecated("keepOutOfScopeLinks", "keepReferencedLinks", true);

        setUrlNormalizer(xml.getObjectImpl(
                WebURLNormalizer.class, "urlNormalizer", urlNormalizer));
        setDelayResolver(xml.getObjectImpl(
                DelayResolver.class, "delay", delayResolver));

        setKeepReferencedLinks(new HashSet<>(xml.getDelimitedEnumList(
                "keepReferencedLinks", ReferencedLinkType.class,
                        new ArrayList<>(keepReferencedLinks))));
        urlCrawlScopeStrategy.setStayOnProtocol(xml.getBoolean(
                "start/@stayOnProtocol",
                urlCrawlScopeStrategy.isStayOnProtocol()));
        urlCrawlScopeStrategy.setStayOnDomain(xml.getBoolean(
                "start/@stayOnDomain",
                urlCrawlScopeStrategy.isStayOnDomain()));
        urlCrawlScopeStrategy.setIncludeSubdomains(xml.getBoolean(
                "start/@includeSubdomains",
                urlCrawlScopeStrategy.isIncludeSubdomains()));
        urlCrawlScopeStrategy.setStayOnPort(xml.getBoolean(
                "start/@stayOnPort",
                urlCrawlScopeStrategy.isStayOnPort()));
        setStartReferencesSitemaps(
                xml.getStringList("start/sitemap", startReferencesSitemaps));
        setStayOnSitemap(xml.getBoolean(
                "start/@stayOnSitemap",
                isStayOnSitemap()));
    }
}
