/* Copyright 2010-2017 Norconex Inc.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Iterator;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.google.common.base.Objects;
import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.AbstractCrawler;
import com.norconex.collector.core.crawler.ICrawler;
import com.norconex.collector.core.data.BaseCrawlData;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.pipeline.committer.HttpCommitterPipeline;
import com.norconex.collector.http.pipeline.committer.HttpCommitterPipelineContext;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipeline;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipelineContext;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.importer.doc.ImporterDocument;
import com.norconex.importer.response.ImporterResponse;
import com.norconex.jef4.status.IJobStatus;
import com.norconex.jef4.status.JobStatusUpdater;
import com.norconex.jef4.suite.JobSuite;

/**
 * The HTTP Crawler.
 * @author Pascal Essiembre
 */
public class HttpCrawler extends AbstractCrawler {

    private static final Logger LOG = LogManager.getLogger(HttpCrawler.class);

	private HttpClient httpClient;
	private ISitemapResolver sitemapResolver;

    /**
     * Constructor.
     * @param crawlerConfig HTTP crawler configuration
     */
	public HttpCrawler(HttpCrawlerConfig crawlerConfig) {
		super(crawlerConfig);
	}

    @Override
    public HttpCrawlerConfig getCrawlerConfig() {
        return (HttpCrawlerConfig) super.getCrawlerConfig();
    }

    /**
     * @return the httpClient
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * @return the sitemapResolver
     */
    public ISitemapResolver getSitemapResolver() {
        return sitemapResolver;
    }

    @Override
    public void stop(IJobStatus jobStatus, JobSuite suite) {
        super.stop(jobStatus, suite);
        if (sitemapResolver != null) {
            sitemapResolver.stop();
        }
    }

    @Override
    protected void prepareExecution(
            JobStatusUpdater statusUpdater, JobSuite suite,
            ICrawlDataStore crawlDataStore, boolean resume) {

        logInitializationInformation();
        initializeHTTPClient();
        initializeRedirectionStrategy();

        // We always initialize the sitemap resolver even if ignored
        // because sitemaps can be specified as start URLs.
        if (getCrawlerConfig().getSitemapResolverFactory() != null) {
            this.sitemapResolver =
                    getCrawlerConfig().getSitemapResolverFactory()
                            .createSitemapResolver(getCrawlerConfig(), resume);
        }

        if (!resume) {
            queueStartURLs(crawlDataStore);
        }
    }

    private void queueStartURLs(final ICrawlDataStore crawlDataStore) {
        int urlCount = 0;
        // Sitemaps must be first, since we favor explicit sitemap
        // referencing as oppose to let other methods guess for it.
        urlCount += queueStartURLsSitemaps(crawlDataStore);
        urlCount += queueStartURLsRegular(crawlDataStore);
        urlCount += queueStartURLsSeedFiles(crawlDataStore);
        urlCount += queueStartURLsProviders(crawlDataStore);
        LOG.info(NumberFormat.getNumberInstance().format(urlCount)
                + " start URLs identified.");
    }

    private int queueStartURLsRegular(final ICrawlDataStore crawlDataStore) {
        String[] startURLs = getCrawlerConfig().getStartURLs();
        if (startURLs == null) {
            return 0;
        }

        for (int i = 0; i < startURLs.length; i++) {
            String startURL = startURLs[i];
            if (StringUtils.isNotBlank(startURL)) {
                executeQueuePipeline(
                        new HttpCrawlData(startURL, 0), crawlDataStore);
            } else {
                LOG.debug("Blank start URL encountered, ignoring it.");
            }
        }
        return startURLs.length;
    }
    private int queueStartURLsSeedFiles(final ICrawlDataStore crawlDataStore) {
        String[] urlsFiles = getCrawlerConfig().getStartURLsFiles();
        if (urlsFiles == null) {
            return 0;
        }

        int urlCount = 0;
        for (int i = 0; i < urlsFiles.length; i++) {
            String urlsFile = urlsFiles[i];
            LineIterator it = null;
            try (InputStream is = new FileInputStream(urlsFile)) {
                it = IOUtils.lineIterator(is, StandardCharsets.UTF_8);
                while (it.hasNext()) {
                    String startURL = StringUtils.trimToNull(it.nextLine());
                    if (startURL != null && !startURL.startsWith("#")) {
                        executeQueuePipeline(
                                new HttpCrawlData(startURL, 0), crawlDataStore);
                        urlCount++;
                    }
                }
            } catch (IOException e) {
                throw new CollectorException(
                        "Could not process URLs file: " + urlsFile, e);
            } finally {
                LineIterator.closeQuietly(it);
            }
        }
        return urlCount;
    }
    private int queueStartURLsSitemaps(final ICrawlDataStore crawlDataStore) {
        String[] sitemapURLs = getCrawlerConfig().getStartSitemapURLs();

        // If no sitemap URLs, leave now
        if (sitemapURLs == null) {
            return 0;
        }

        // There are sitemaps, process them. First group them by URL root
        MultiValuedMap<String, String> sitemapsPerRoots =
                new ArrayListValuedHashMap<>();
        for (String sitemapURL : sitemapURLs) {
            String urlRoot = HttpURL.getRoot(sitemapURL);
            sitemapsPerRoots.put(urlRoot, sitemapURL);
        }

        final MutableInt urlCount = new MutableInt();
        SitemapURLAdder urlAdder = new SitemapURLAdder() {
            @Override
            public void add(HttpCrawlData reference) {
                executeQueuePipeline(reference, crawlDataStore);
                urlCount.increment();
            }
        };
        // Process each URL root group separately
        for (String  urlRoot : sitemapsPerRoots.keySet()) {
            String[] locations = sitemapsPerRoots.get(
                    urlRoot).toArray(ArrayUtils.EMPTY_STRING_ARRAY);
            if (sitemapResolver != null) {
                sitemapResolver.resolveSitemaps(
                        httpClient, urlRoot, locations, urlAdder, true);
            } else {
                LOG.error("Sitemap resolver is null. Sitemaps defined as "
                        + "start URLs cannot be resolved.");
            }
        }
        return urlCount.intValue();
    }

    private int queueStartURLsProviders(final ICrawlDataStore crawlDataStore) {
        IStartURLsProvider[] providers =
                getCrawlerConfig().getStartURLsProviders();
        if (providers == null) {
            return 0;
        }
        int count = 0;
        for (IStartURLsProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            Iterator<String> it = provider.provideStartURLs();
            while (it.hasNext()) {
                executeQueuePipeline(
                        new HttpCrawlData(it.next(), 0), crawlDataStore);
                count++;
            }
        }
        return count;
    }

    private void logInitializationInformation() {
        LOG.info(getId() +  ": RobotsTxt support: "
                + !getCrawlerConfig().isIgnoreRobotsTxt());
        LOG.info(getId() +  ": RobotsMeta support: "
                + !getCrawlerConfig().isIgnoreRobotsMeta());
        LOG.info(getId() +  ": Sitemap support: "
                + !getCrawlerConfig().isIgnoreSitemap());
        LOG.info(getId() +  ": Canonical links support: "
                + !getCrawlerConfig().isIgnoreCanonicalLinks());

        String userAgent = getCrawlerConfig().getUserAgent();
        if (StringUtils.isBlank(userAgent)) {
            LOG.info(getId() +  ": User-Agent: <None specified>");
            LOG.debug("It is recommended you identify yourself to web sites "
                    + "by specifying a user agent "
                    + "(https://en.wikipedia.org/wiki/User_agent)");
        } else {
            LOG.info(getId() +  ": User-Agent: " + userAgent);
        }
    }

    @Override
    protected void executeQueuePipeline(
            ICrawlData crawlData, ICrawlDataStore crawlDataStore) {
        HttpCrawlData httpData = (HttpCrawlData) crawlData;
        HttpQueuePipelineContext context = new HttpQueuePipelineContext(
                this, crawlDataStore, httpData);
        new HttpQueuePipeline().execute(context);
    }

    @Override
    protected ImporterDocument wrapDocument(
            ICrawlData crawlData, ImporterDocument document) {
        return new HttpDocument(document);
    }
    @Override
    protected void initCrawlData(ICrawlData crawlData,
            ICrawlData cachedCrawlData, ImporterDocument document) {
        HttpDocument doc = (HttpDocument) document;
        HttpCrawlData httpData = (HttpCrawlData) crawlData;
        HttpCrawlData cachedHttpData = (HttpCrawlData) cachedCrawlData;
        HttpMetadata metadata = doc.getMetadata();

        metadata.addInt(HttpMetadata.COLLECTOR_DEPTH, httpData.getDepth());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_SM_CHANGE_FREQ,
                httpData.getSitemapChangeFreq());
        if (httpData.getSitemapLastMod() != null) {
            metadata.addLong(HttpMetadata.COLLECTOR_SM_LASTMOD,
                    httpData.getSitemapLastMod());
        }
        if (httpData.getSitemapPriority() != null) {
            metadata.addFloat(HttpMetadata.COLLECTOR_SM_PRORITY,
                    httpData.getSitemapPriority());
        }

        // In case the crawl data supplied is from a URL was pulled from cache
        // since the parent was skipped and could not be extracted normally
        // with link information, we attach referrer data here if null
        // (but only if referrer reference is not null, which should never
        // be in this case as it is set by beforeFinalizeDocumentProcessing()
        // below.
        // We do not need to do this for sitemap information since the queue
        // pipeline takes care of (re)adding it.
        //TODO consider having a flag on CrawlData that says where it came
        //from so we know to initialize it properly.  Or... always
        //initialize some new crawl data from cache higher up?
        if (cachedHttpData != null && httpData.getReferrerReference() != null
                && Objects.equal(
                        httpData.getReferrerReference(),
                        cachedHttpData.getReferrerReference())) {
            if (httpData.getReferrerLinkTag() == null) {
                httpData.setReferrerLinkTag(
                        cachedHttpData.getReferrerLinkTag());
            }
            if (httpData.getReferrerLinkText() == null) {
                httpData.setReferrerLinkText(
                        cachedHttpData.getReferrerLinkText());
            }
            if (httpData.getReferrerLinkTitle() == null) {
                httpData.setReferrerLinkTitle(
                        cachedHttpData.getReferrerLinkTitle());
            }
        }

        // Add referrer data to metadata
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_REFERENCE,
                httpData.getReferrerReference());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_LINK_TAG,
                httpData.getReferrerLinkTag());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_LINK_TEXT,
                httpData.getReferrerLinkText());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_LINK_TITLE,
                httpData.getReferrerLinkTitle());

        // Add possible redirect trail
        if (ArrayUtils.isNotEmpty(httpData.getRedirectTrail())) {
            metadata.setString(HttpMetadata.COLLECTOR_REDIRECT_TRAIL,
                    httpData.getRedirectTrail());
        }
    }

    @Override
    protected ImporterResponse executeImporterPipeline(
            ImporterPipelineContext importerContext) {
        HttpImporterPipelineContext httpContext =
                new HttpImporterPipelineContext(importerContext);
        new HttpImporterPipeline(
                getCrawlerConfig().isKeepDownloads(),
                importerContext.isOrphan(),
                getCrawlerConfig().isLinkExtractorQuitAtDepth()
        ).execute(httpContext);
        return httpContext.getImporterResponse();
    }

    @Override
    protected BaseCrawlData createEmbeddedCrawlData(
            String embeddedReference, ICrawlData parentCrawlData) {
        return new HttpCrawlData(embeddedReference,
                ((HttpCrawlData) parentCrawlData).getDepth());
    }

    @Override
    protected void executeCommitterPipeline(ICrawler crawler,
            ImporterDocument doc, ICrawlDataStore crawlDataStore,
            BaseCrawlData crawlData, BaseCrawlData cachedCrawlData) {

        HttpCommitterPipelineContext context = new HttpCommitterPipelineContext(
                (HttpCrawler) crawler, crawlDataStore, (HttpDocument) doc,
                (HttpCrawlData) crawlData, (HttpCrawlData) cachedCrawlData);
        new HttpCommitterPipeline().execute(context);
    }

    @Override
    protected void beforeFinalizeDocumentProcessing(
            BaseCrawlData crawlData, ICrawlDataStore store,
            ImporterDocument doc, ICrawlData cachedData) {

        // If URLs were not yet extracted, it means no links will be followed.
        // In case the referring document was skipped or has a bad status
        // (which can always be temporary), we should queue for processing any
        // referenced links from cache to make sure an attempt will be made to
        // re-crawl these "child" links and they will not be considered orphans.
        // Else, as orphans they could wrongfully be deleted, ignored, or
        // be re-assigned the wrong depth if linked from another, deeper, page.
        // See: https://github.com/Norconex/collector-http/issues/278


        HttpCrawlData httpData = (HttpCrawlData) crawlData;
        HttpCrawlData httpCachedData = (HttpCrawlData) cachedData;

        //TODO improve this #533 hack in v3
        if (httpData.getState().isNewOrModified()
                && ArrayUtils.isNotEmpty(httpData.getRedirectTrail())) {
            HttpImporterPipeline.GOOD_REDIRECTS.add(httpData.getReference());
        }

        // If never crawled before, URLs were extracted already, or cached
        // version has no extracted URLs, abort now.
        if (cachedData == null
                || ArrayUtils.isNotEmpty(httpData.getReferencedUrls())
                || ArrayUtils.isEmpty(httpCachedData.getReferencedUrls())) {
            return;
        }

        // Only continue if the document could not have extracted URLs because
        // it was skipped, or in a temporary invalid state that prevents
        // accessing child links normally.
        CrawlState state = crawlData.getState();
        if (!state.isSkipped()
                && !state.isOneOf(CrawlState.BAD_STATUS, CrawlState.ERROR)) {
            return;
        }

        // OK, let's do this
        if (LOG.isDebugEnabled()) {
            LOG.debug("Queuing referenced URLs of " + crawlData.getReference());
        }

        int childDepth = httpData.getDepth() + 1;
        String[] referencedUrls = httpCachedData.getReferencedUrls();
        for (String url : referencedUrls) {

            HttpCrawlData childData = new HttpCrawlData(url, childDepth);
            childData.setReferrerReference(httpData.getReference());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Queueing skipped document's child: "
                        + childData.getReference());
            }
            executeQueuePipeline(childData, store);
        }
    }

    @Override
    protected void markReferenceVariationsAsProcessed(
            BaseCrawlData crawlData, ICrawlDataStore crawlDataStore) {

        HttpCrawlData httpData = (HttpCrawlData) crawlData;
        // Mark original URL as processed
        String originalRef = httpData.getOriginalReference();
        String finalRef = httpData.getReference();
        if (StringUtils.isNotBlank(originalRef)
                && ObjectUtils.notEqual(originalRef, finalRef)) {
            HttpCrawlData originalData = (HttpCrawlData) httpData.clone();
            originalData.setReference(originalRef);
            originalData.setOriginalReference(null);
            crawlDataStore.processed(originalData);
        }
    }

    @Override
    protected void cleanupExecution(JobStatusUpdater statusUpdater,
            JobSuite suite, ICrawlDataStore refStore) {
        try {
            if (sitemapResolver != null) {
                sitemapResolver.stop();
            }
        } catch (Exception e) {
            LOG.error("Could not stop sitemap store.");
        }
        closeHttpClient();
    }

    private void metadataAddString(
            HttpMetadata metadata, String key, String value) {
        if (value != null) {
            metadata.addString(key, value);
        }
    }

    private void initializeHTTPClient() {
        httpClient = getCrawlerConfig().getHttpClientFactory().createHTTPClient(
                getCrawlerConfig().getUserAgent());
	}

    // Wraps redirection strategy to consider URLs as new documents to
    // queue for processing.
    private void initializeRedirectionStrategy() {
        try {
            Object chain = FieldUtils.readField(httpClient, "execChain", true);
            Object redir = FieldUtils.readField(
                    chain, "redirectStrategy", true);
            if (redir instanceof RedirectStrategy) {
                RedirectStrategy originalStrategy = (RedirectStrategy) redir;
                RedirectStrategyWrapper strategyWrapper =
                        new RedirectStrategyWrapper(originalStrategy,
                                getCrawlerConfig().getRedirectURLProvider());
                FieldUtils.writeField(
                        chain, "redirectStrategy", strategyWrapper, true);
            } else {
                LOG.warn("Could not wrap RedirectStrategy to properly handle"
                        + "redirects.");
            }
        } catch (Exception e) {
            LOG.warn("\"maxConnectionInactiveTime\" could not be set since "
                    + "internal connection manager does not support it.");
        }
    }

    private void closeHttpClient() {
        if (httpClient instanceof CloseableHttpClient) {
            try {
                ((CloseableHttpClient) httpClient).close();
            } catch (IOException e) {
                LOG.error(getId() +  " Cannot close HttpClient.", e);
            }
        }
    }
}
