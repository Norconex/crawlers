/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.crawler;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
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
import com.norconex.importer.doc.ImporterDocument;
import com.norconex.importer.response.ImporterResponse;
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
    protected void prepareExecution(
            JobStatusUpdater statusUpdater, JobSuite suite, 
            ICrawlDataStore crawlDataStore, boolean resume) {
        
        logInitializationInformation();
        initializeHTTPClient();

        if (!getCrawlerConfig().isIgnoreSitemap()) {
            this.sitemapResolver = 
                    getCrawlerConfig().getSitemapResolverFactory()
                            .createSitemapResolver(getCrawlerConfig(), resume);
        }

        if (!resume) {
            queueStartURLs(crawlDataStore);
        }
    }
    
    private void queueStartURLs(ICrawlDataStore crawlDataStore) {
        // Queue regular start urls
        String[] startURLs = getCrawlerConfig().getStartURLs();
        if (startURLs != null) {
            for (int i = 0; i < startURLs.length; i++) {
                String startURL = startURLs[i];
                executeQueuePipeline(
                        new HttpCrawlData(startURL, 0), crawlDataStore);
            }
        }
        // Queue start urls define in one or more seed files
        String[] urlsFiles = getCrawlerConfig().getUrlsFiles();
        if (urlsFiles != null) {
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
                    }
                } catch (IOException e) {
                    throw new CollectorException(
                            "Could not process URLs file: " + urlsFile, e);
                } finally {
                    LineIterator.closeQuietly(it);;
                }
            }
        }
    }
    
    private void logInitializationInformation() {
        LOG.info(getId() +  ": RobotsTxt support: " 
                + !getCrawlerConfig().isIgnoreRobotsTxt());
        LOG.info(getId() +  ": RobotsMeta support: "  
                + !getCrawlerConfig().isIgnoreRobotsMeta()); 
        LOG.info(getId() +  ": Sitemap support: " 
                + !getCrawlerConfig().isIgnoreSitemap());
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
        HttpDocument doc = new HttpDocument(document);
        HttpCrawlData httpData = (HttpCrawlData) crawlData;
        HttpMetadata metadata = doc.getMetadata();
        
        metadata.addInt(HttpMetadata.COLLECTOR_DEPTH, httpData.getDepth());
        if (StringUtils.isNotBlank(httpData.getSitemapChangeFreq())) {
            metadata.addString(HttpMetadata.COLLECTOR_SM_CHANGE_FREQ, 
                    httpData.getSitemapChangeFreq());
        }
        if (httpData.getSitemapLastMod() != null) {
            metadata.addLong(HttpMetadata.COLLECTOR_SM_LASTMOD, 
                    httpData.getSitemapLastMod());
        }        
        if (httpData.getSitemapPriority() != null) {
            metadata.addFloat(HttpMetadata.COLLECTOR_SM_PRORITY, 
                    httpData.getSitemapPriority());
        }    
        return doc;
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
        return new HttpCrawlData(
                embeddedReference, ((HttpCrawlData) parentCrawlData).getDepth());
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
        if (StringUtils.isNotBlank(httpData.getOriginalReference()) 
                && ObjectUtils.notEqual(httpData.getOriginalReference(), 
                        httpData.getReference())) {
            HttpCrawlData originalRef = (HttpCrawlData) httpData.clone();
            originalRef.setReference(httpData.getOriginalReference());
            originalRef.setOriginalReference(null);
            crawlDataStore.processed(originalRef);
        }
    }
    
    @Override
    protected void cleanupExecution(JobStatusUpdater statusUpdater,
            JobSuite suite, ICrawlDataStore refStore) {
        closeHttpClient();
    }
    
    private void initializeHTTPClient() {
        httpClient = getCrawlerConfig().getHttpClientFactory().createHTTPClient(
                getCrawlerConfig().getUserAgent());
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
