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

import java.io.FileInputStream;
import java.io.IOException;
import java.text.NumberFormat;

import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.AbstractCrawler;
import com.norconex.collector.core.crawler.ICrawler;
import com.norconex.collector.core.data.BaseCrawlData;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.pipeline.committer.HttpCommitterPipeline;
import com.norconex.collector.http.pipeline.committer.HttpCommitterPipelineContext;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipeline;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipelineContext;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
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
        initializeRedirectionStrategy(crawlDataStore);

        if (!getCrawlerConfig().isIgnoreSitemap()) {
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
            executeQueuePipeline(
                    new HttpCrawlData(startURL, 0), crawlDataStore);
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
            try {
                it = IOUtils.lineIterator(
                        new FileInputStream(urlsFile), CharEncoding.UTF_8);
                while (it.hasNext()) {
                    String startURL = it.nextLine();
                    executeQueuePipeline(new HttpCrawlData(
                            startURL, 0), crawlDataStore);
                    urlCount++;
                }
            } catch (IOException e) {
                throw new CollectorException(
                        "Could not process URLs file: " + urlsFile, e);
            } finally {
                LineIterator.closeQuietly(it);;
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
        MultiValueMap<String, String> sitemapsPerRoots = new MultiValueMap<>();
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
            String[] locations = sitemapsPerRoots.getCollection(
                    urlRoot).toArray(ArrayUtils.EMPTY_STRING_ARRAY);
            sitemapResolver.resolveSitemaps(
                    httpClient, urlRoot, locations, urlAdder, true);
        }
        return urlCount.intValue();
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
    protected void applyCrawlData(
            ICrawlData crawlData, ImporterDocument document) {
        HttpDocument doc = (HttpDocument) document;
        HttpCrawlData httpData = (HttpCrawlData) crawlData;
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
        // Referrer data
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_REFERENCE, 
                httpData.getReferrerReference());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_LINK_TAG, 
                httpData.getReferrerLinkTag());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_LINK_TEXT, 
                httpData.getReferrerLinkText());
        metadataAddString(metadata, HttpMetadata.COLLECTOR_REFERRER_LINK_TITLE, 
                httpData.getReferrerLinkTitle());
    }
    
    @Override
    protected ImporterResponse executeImporterPipeline(
            ICrawler crawler, ImporterDocument doc, 
            ICrawlDataStore crawlDataStore, BaseCrawlData crawlData) {
        //TODO create pipeline context prototype
        //TODO cache the pipeline object?
        HttpImporterPipelineContext context = new HttpImporterPipelineContext(
                (HttpCrawler) crawler, crawlDataStore, 
                (HttpCrawlData) crawlData, (HttpDocument) doc);
        new HttpImporterPipeline(
                getCrawlerConfig().isKeepDownloads()).execute(context);
        return context.getImporterResponse();
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
            BaseCrawlData crawlData) {

        HttpCommitterPipelineContext context = new HttpCommitterPipelineContext(
                (HttpCrawler) crawler, crawlDataStore, (HttpDocument) doc, 
                (HttpCrawlData) crawlData);
        new HttpCommitterPipeline().execute(context);
    }
    
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
    // queue for processing, if they meet the "stayOnSite" requirements and 
    // the regex filters
    private void initializeRedirectionStrategy(ICrawlDataStore crawlDataStore) {
        try {
            Object chain = FieldUtils.readField(httpClient, "execChain", true);
            Object redir = FieldUtils.readField(
                    chain, "redirectStrategy", true);
            if (redir instanceof RedirectStrategy) {
                RedirectStrategy originalStrategy = (RedirectStrategy) redir; 
                HttpCrawlerRedirectStrategy strategyWrapper = 
                        new HttpCrawlerRedirectStrategy(originalStrategy);
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
