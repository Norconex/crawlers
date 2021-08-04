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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlDocInfo;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.pipeline.committer.HttpCommitterPipeline;
import com.norconex.collector.http.pipeline.committer.HttpCommitterPipelineContext;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipeline;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipelineContext;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.importer.response.ImporterResponse;

/**
 * The HTTP Crawler.
 * @author Pascal Essiembre
 */
public class HttpCrawler extends Crawler {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCrawler.class);

	private ISitemapResolver sitemapResolver;
	private HttpFetchClient fetchClient;
	private boolean initialQueueLoaded = true;

    /**
     * Constructor.
     * @param crawlerConfig HTTP crawler configuration
     * @param collector http collector this crawler belongs to
     */
	public HttpCrawler(
	        HttpCrawlerConfig crawlerConfig, HttpCollector collector) {
		super(crawlerConfig, collector);
	}

    @Override
    public HttpCrawlerConfig getCrawlerConfig() {
        return (HttpCrawlerConfig) super.getCrawlerConfig();
    }

    public HttpFetchClient getHttpFetchClient() {
        return fetchClient;
    }

    /**
     * @return the sitemapResolver
     */
    public ISitemapResolver getSitemapResolver() {
        return sitemapResolver;
    }

    @Override
    protected boolean isQueueInitialized() {
        return initialQueueLoaded;
    }

    @Override
    protected void beforeCrawlerExecution(boolean resume) {

        HttpCrawlerConfig cfg = getCrawlerConfig();

        logInitializationInformation();
        fetchClient = new HttpFetchClient(
                getStreamFactory(), cfg.getHttpFetchers(),
                cfg.getHttpFetchersMaxRetries(),
                cfg.getHttpFetchersRetryDelay());

        // We always initialize the sitemap resolver even if ignored
        // because sitemaps can be specified as start URLs.
        this.sitemapResolver = cfg.getSitemapResolver();

        if (!resume) {
            this.initialQueueLoaded = false;
            if (cfg.isStartURLsAsync()) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                    try {
                        LOG.info("Reading start URLs asynchronously.");
                        Thread.currentThread().setName(getId());
                        queueStartURLs();
                    } finally {
                        initialQueueLoaded = true;
                        try {
                            executor.shutdown();
                            executor.awaitTermination(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            LOG.error("Reading of start URLs interrupted.", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            } else {
                queueStartURLs();
                this.initialQueueLoaded = true;
            }
        } else {
            this.initialQueueLoaded = true;
        }
    }
    @Override
    protected void afterCrawlerExecution() {
        //NOOP
    }


    private void queueStartURLs() {
        int urlCount = 0;
        // Sitemaps must be first, since we favor explicit sitemap
        // referencing as oppose to let other methods guess for it.
        urlCount += queueStartURLsSitemaps();
        urlCount += queueStartURLsRegular();
        urlCount += queueStartURLsSeedFiles();
        urlCount += queueStartURLsProviders();
        if (LOG.isInfoEnabled()) {
            LOG.info("{} start URLs identified.",
                    NumberFormat.getNumberInstance().format(urlCount));
        }
    }

    private int queueStartURLsRegular() {
        List<String> startURLs = getCrawlerConfig().getStartURLs();
        for (String startURL : startURLs) {
            if (StringUtils.isNotBlank(startURL)) {
                executeQueuePipeline(
                        new HttpDocInfo(startURL, 0));
            } else {
                LOG.debug("Blank start URL encountered, ignoring it.");
            }
        }
        return startURLs.size();
    }
    private int queueStartURLsSeedFiles() {
        List<Path> urlsFiles = getCrawlerConfig().getStartURLsFiles();

        int urlCount = 0;
        for (Path urlsFile : urlsFiles) {
            try (LineIterator it = IOUtils.lineIterator(
                    Files.newInputStream(urlsFile), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    String startURL = StringUtils.trimToNull(it.nextLine());
                    if (startURL != null && !startURL.startsWith("#")) {
                        executeQueuePipeline(
                                new HttpDocInfo(startURL, 0));
                        urlCount++;
                    }
                }
            } catch (IOException e) {
                throw new CollectorException(
                        "Could not process URLs file: " + urlsFile, e);
            }
        }
        return urlCount;
    }
    private int queueStartURLsSitemaps() {
        List<String> sitemapURLs = getCrawlerConfig().getStartSitemapURLs();

        // There are sitemaps, process them. First group them by URL root
        MultiValuedMap<String, String> sitemapsPerRoots =
                new ArrayListValuedHashMap<>();
        for (String sitemapURL : sitemapURLs) {
            String urlRoot = HttpURL.getRoot(sitemapURL);
            sitemapsPerRoots.put(urlRoot, sitemapURL);
        }

        final MutableInt urlCount = new MutableInt();
        Consumer<HttpDocInfo> urlConsumer = (ref) -> {
                executeQueuePipeline(ref);
                urlCount.increment();
        };
        // Process each URL root group separately
        for (String  urlRoot : sitemapsPerRoots.keySet()) {
            List<String> locations =
                    (List<String>) sitemapsPerRoots.get(urlRoot);
            if (sitemapResolver != null) {
                sitemapResolver.resolveSitemaps(
                        fetchClient, urlRoot, locations, urlConsumer, true);
            } else {
                LOG.error("Sitemap resolver is null. Sitemaps defined as "
                        + "start URLs cannot be resolved.");
            }
        }
        return urlCount.intValue();
    }

    private int queueStartURLsProviders() {
        List<IStartURLsProvider> providers =
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
                executeQueuePipeline(new HttpDocInfo(it.next(), 0));
                count++;
            }
        }
        return count;
    }

    private void logInitializationInformation() {
        LOG.info("RobotsTxt support: {}",
                !getCrawlerConfig().isIgnoreRobotsTxt());
        LOG.info("RobotsMeta support: {}",
                !getCrawlerConfig().isIgnoreRobotsMeta());
        LOG.info("Sitemap support: {}",
                !getCrawlerConfig().isIgnoreSitemap());
        LOG.info("Canonical links support: {}",
                !getCrawlerConfig().isIgnoreCanonicalLinks());
    }

    @Override
    protected void executeQueuePipeline(CrawlDocInfo crawlRef) {
        HttpDocInfo httpData = (HttpDocInfo) crawlRef;
        HttpQueuePipelineContext context = new HttpQueuePipelineContext(
                this, httpData);
        new HttpQueuePipeline().execute(context);
    }

    @Override
    protected Class<? extends CrawlDocInfo> getCrawlReferenceType() {
        return HttpDocInfo.class;
    }

    @Override
    protected void initCrawlDoc(CrawlDoc doc) {

        HttpDocInfo docInfo = (HttpDocInfo) doc.getDocInfo();
        HttpDocInfo cachedDocInfo = (HttpDocInfo) doc.getCachedDocInfo();
        Properties metadata = doc.getMetadata();

        metadata.add(HttpDocMetadata.DEPTH, docInfo.getDepth());
        metadataAddString(metadata, HttpDocMetadata.SM_CHANGE_FREQ,
                docInfo.getSitemapChangeFreq());
        if (docInfo.getSitemapLastMod() != null) {
            metadata.add(HttpDocMetadata.SM_LASTMOD,
                    docInfo.getSitemapLastMod());
        }
        if (docInfo.getSitemapPriority() != null) {
            metadata.add(HttpDocMetadata.SM_PRORITY,
                    docInfo.getSitemapPriority());
        }

        // In case the crawl data supplied is from a URL that was pulled
        // from cache because the parent was skipped and could not be
        // extracted normally with link information, we attach referrer
        // data here if null
        // (but only if referrer reference is not null, which should never
        // be in this case as it is set by beforeFinalizeDocumentProcessing()
        // below.
        // We do not need to do this for sitemap information since the queue
        // pipeline takes care of (re)adding it.
        if (cachedDocInfo != null && docInfo.getReferrerReference() != null
                && Objects.equal(
                        docInfo.getReferrerReference(),
                        cachedDocInfo.getReferrerReference())) {
            if (docInfo.getReferrerLinkMetadata() == null) {
                docInfo.setReferrerLinkMetadata(
                        cachedDocInfo.getReferrerLinkMetadata());
            }
        }

        // Add referrer data to metadata
        metadataAddString(metadata, HttpDocMetadata.REFERRER_REFERENCE,
                docInfo.getReferrerReference());
        if (docInfo.getReferrerLinkMetadata() != null) {
            Properties linkMeta = new Properties();
            linkMeta.fromString(docInfo.getReferrerLinkMetadata());
            for (Entry<String, List<String>> en : linkMeta.entrySet()) {
                String key = HttpDocMetadata.REFERRER_LINK_PREFIX + en.getKey();
                for (String value : en.getValue()) {
                    if (value != null) {
                        metadata.add(key, value);
                    }
                }
            }
        }

        // Add possible redirect trail
        if (!docInfo.getRedirectTrail().isEmpty()) {
            metadata.setList(HttpDocMetadata.REDIRECT_TRAIL,
                    docInfo.getRedirectTrail());
        }
    }

    @Override
    protected ImporterResponse executeImporterPipeline(
            ImporterPipelineContext importerContext) {

//TODO see if a HttpImporterPipelineContext can be created instead,
// OR, have a Map on AbstractPipelineContext for extra elements and
// delete HttpImporterPipelineContext.
        HttpImporterPipelineContext httpContext =
                new HttpImporterPipelineContext(importerContext);


        new HttpImporterPipeline(
                getCrawlerConfig().isKeepDownloads(),
                importerContext.getDocument().isOrphan()).execute(httpContext);
        return httpContext.getImporterResponse();
    }

    @Override
    protected CrawlDocInfo createChildDocInfo(
            String embeddedReference, CrawlDocInfo parentCrawlData) {
        return new HttpDocInfo(embeddedReference,
                ((HttpDocInfo) parentCrawlData).getDepth());
    }

    @Override
    protected void executeCommitterPipeline(Crawler crawler, CrawlDoc doc) {
        new HttpCommitterPipeline().execute(new HttpCommitterPipelineContext(
                (HttpCrawler) crawler, doc));
    }

    @Override
    protected void beforeFinalizeDocumentProcessing(CrawlDoc doc) {

        // If URLs were not yet extracted, it means no links will be followed.
        // In case the referring document was skipped or has a bad status
        // (which can always be temporary), we should queue for processing any
        // referenced links from cache to make sure an attempt will be made to
        // re-crawl these "child" links and they will not be considered orphans.
        // Else, as orphans they could wrongfully be deleted, ignored, or
        // be re-assigned the wrong depth if linked from another, deeper, page.
        // See: https://github.com/Norconex/collector-http/issues/278


        HttpDocInfo httpData = (HttpDocInfo) doc.getDocInfo();
        HttpDocInfo httpCachedData = (HttpDocInfo) doc.getCachedDocInfo();

        // If never crawled before, URLs were extracted already, or cached
        // version has no extracted, URLs, abort now.
        if (httpCachedData == null
                || !httpData.getReferencedUrls().isEmpty()
                || httpCachedData.getReferencedUrls().isEmpty()) {
            return;
        }

        // Only continue if the document could not have extracted URLs because
        // it was skipped, or in a temporary invalid state that prevents
        // accessing child links normally.
        CrawlState state = httpData.getState();
        if (!state.isSkipped() && !state.isOneOf(
                CrawlState.BAD_STATUS, CrawlState.ERROR)) {
            return;
        }

        // OK, let's do this
        if (LOG.isDebugEnabled()) {
            LOG.debug("Queueing referenced URLs of {}",
                    httpData.getReference());
        }

        int childDepth = httpData.getDepth() + 1;
        List<String> referencedUrls = httpCachedData.getReferencedUrls();
        for (String url : referencedUrls) {

            HttpDocInfo childData = new HttpDocInfo(url, childDepth);
            childData.setReferrerReference(httpData.getReference());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Queueing skipped document's child: {}",
                        childData.getReference());
            }
            executeQueuePipeline(childData);
        }
    }

    @Override
    protected void markReferenceVariationsAsProcessed(
            CrawlDocInfo crawlRef) {

        HttpDocInfo httpData = (HttpDocInfo) crawlRef;
        // Mark original URL as processed
        String originalRef = httpData.getOriginalReference();
        String finalRef = httpData.getReference();
        if (StringUtils.isNotBlank(originalRef)
                && ObjectUtils.notEqual(originalRef, finalRef)) {

            HttpDocInfo originalData = new HttpDocInfo(httpData);
            originalData.setReference(originalRef);
            originalData.setOriginalReference(null);
            getDocInfoService().processed(originalData);
        }
    }

    private void metadataAddString(
            Properties metadata, String key, String value) {
        if (value != null) {
            metadata.add(key, value);
        }
    }
}
