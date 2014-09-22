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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.AbstractCrawler;
import com.norconex.collector.core.crawler.event.CrawlerEventManager;
import com.norconex.collector.core.crawler.event.DocCrawlEvent;
import com.norconex.collector.core.doccrawl.IDocCrawl;
import com.norconex.collector.core.doccrawl.store.IDocCrawlStore;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doc.pipe.DocumentPipeline;
import com.norconex.collector.http.doc.pipe.DocumentPipelineContext;
import com.norconex.collector.http.doc.pipe.PostImportPipeline;
import com.norconex.collector.http.doc.pipe.PostImportPipelineContext;
import com.norconex.collector.http.doccrawl.HttpDocCrawl;
import com.norconex.collector.http.doccrawl.HttpDocCrawlState;
import com.norconex.collector.http.doccrawl.pipe.DocCrawlPipeline;
import com.norconex.collector.http.doccrawl.pipe.DocCrawlPipelineContext;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.Importer;
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
	
    private static final String PROP_OK_URL_COUNT = "okUrlCount";
	private static final int MINIMUM_DELAY = 10;
	
	private HttpClient httpClient;
	private ISitemapResolver sitemapResolver;
    private boolean stopped;
    private int okURLsCount;
    private double lastPercent;
    private CrawlerEventManager crawlerEventManager;
    private Importer importer;
    private CachedStreamFactory streamFactory;
    
    /**
     * Constructor.
     * @param crawlerConfig HTTP crawler configuration
     */
	public HttpCrawler(HttpCrawlerConfig crawlerConfig) {
		super(crawlerConfig);
        crawlerConfig.getWorkDir().mkdirs();
	}
	
	@Override
	public String getId() {
		return getCrawlerConfig().getId();
	}

	/**
	 * Whether the job was stopped.
	 * @return <code>true</code> if stopped
	 */
    public boolean isStopped() {
        return stopped;
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
    
    
    public Importer getImporter() {
        return importer;
    }
    
    public CachedStreamFactory getStreamFactory() {
        return streamFactory;
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
            IDocCrawlStore refStore, boolean resume) {
        
        importer = new Importer(getCrawlerConfig().getImporterConfig());
        streamFactory = importer.getStreamFactory();
                
        logInitializationInformation();
        initializeHTTPClient();
        
        if (!getCrawlerConfig().isIgnoreSitemap()) {
            this.sitemapResolver = 
                    getCrawlerConfig().getSitemapResolverFactory()
                            .createSitemapResolver(getCrawlerConfig(), resume);
        }
        
        this.crawlerEventManager = new CrawlerEventManager(
                this, getCrawlerConfig().getCrawlerListeners());
        
        if (resume) {
            okURLsCount = statusUpdater.getProperties().getInt(
                    PROP_OK_URL_COUNT, 0);
        } else {
            String[] startURLs = getCrawlerConfig().getStartURLs();
            for (int i = 0; i < startURLs.length; i++) {
                String startURL = startURLs[i];
                DocCrawlPipelineContext context = new DocCrawlPipelineContext(
                        this, refStore, new HttpDocCrawl(startURL, 0));
                new DocCrawlPipeline().execute(context);
            }
            this.crawlerEventManager.crawlerStarted();
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
    protected void cleanupExecution(JobStatusUpdater statusUpdater,
            JobSuite suite, IDocCrawlStore refStore) {
        closeHttpClient();
    }
    
    @Override
    protected void execute(JobStatusUpdater statusUpdater,
            JobSuite suite, IDocCrawlStore refStore) {

        
        //TODO move this code to a config validator class?
        //TODO move this code to base class?
        if (StringUtils.isBlank(getCrawlerConfig().getId())) {
            throw new CollectorException("HTTP Crawler must be given "
                    + "a unique identifier (id).");
        }

        
        //--- Process start/queued URLS ----------------------------------------
        LOG.info(getId() + ": Crawling URLs...");
        processURLs(refStore, statusUpdater, suite, false);

        if (!isStopped()) {
            handleOrphans(refStore, statusUpdater, suite);
        }
        
        ICommitter committer = getCrawlerConfig().getCommitter();
        if (committer != null) {
            LOG.info(getId() + ": Crawler \"" + getId() + "\" " 
                    + (stopped ? "stopping" : "finishing")
                    + ": committing documents.");
            committer.commit();
        }

        LOG.info(getId() + ": "
                + refStore.getProcessedCount() 
                + " URLs processed for \"" + getId() + "\".");

        LOG.debug(getId() + ": Removing empty directories");
        FileUtil.deleteEmptyDirs(gelCrawlerDownloadDir());

        if (!stopped) {
            this.crawlerEventManager.crawlerFinished();
        }
        LOG.info(getId() + ": Crawler \"" + getId() + "\" " 
                + (stopped ? "stopped." : "completed."));
    }

    private void handleOrphans(IDocCrawlStore refStore,
            JobStatusUpdater statusUpdater, JobSuite suite) {
        if (getCrawlerConfig().isDeleteOrphans()) {
            LOG.info(getId() + ": Deleting orphan URLs (if any)...");
            deleteCacheOrphans(refStore, statusUpdater, suite);
        } else {
            if (!isMaxURLs()) {
                LOG.info(getId() + ": Re-processing orphan URLs (if any)...");
                reprocessCacheOrphans(refStore, statusUpdater, suite);
            }
            // In case any item remains after we are done re-processing:
            LOG.info(getId() + ": Deleting remaining orphan URLs (if any)...");
            deleteCacheOrphans(refStore, statusUpdater, suite);
        }
    }
    
    private void reprocessCacheOrphans(
            IDocCrawlStore refStore, JobStatusUpdater statusUpdater, JobSuite suite) {
        long count = 0;
        Iterator<IDocCrawl> it = refStore.getCacheIterator();
        if (it != null) {
            while (it.hasNext()) {
                HttpDocCrawl reference = (HttpDocCrawl) it.next();
                DocCrawlPipelineContext context = new DocCrawlPipelineContext(
                        this, refStore, reference);
                new DocCrawlPipeline().execute(context);
                count++;
            }
            processURLs(refStore, statusUpdater, suite, false);
        }
        LOG.info(getId() + ": Reprocessed " + count + " orphan URLs...");
    }
    
    private void deleteCacheOrphans(
            IDocCrawlStore refStore, JobStatusUpdater statusUpdater, JobSuite suite) {
        long count = 0;
        Iterator<IDocCrawl> it = refStore.getCacheIterator();
        if (it != null && it.hasNext()) {
            while (it.hasNext()) {
                refStore.queue(it.next());
                count++;
            }
            processURLs(refStore, statusUpdater, suite, true);
        }
        LOG.info(getId() + ": Deleted " + count + " orphan URLs...");
    }
    
    private void processURLs(
    		final IDocCrawlStore refStore,
    		final JobStatusUpdater statusUpdater, 
    		final JobSuite suite,
    		final boolean delete) {
        
        int numThreads = getCrawlerConfig().getNumThreads();
        final CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i + 1;
            LOG.debug(getId() 
                    + ": Crawler thread #" + threadIndex + " started.");
            pool.execute(new ProcessURLsRunnable(
                    suite, statusUpdater, refStore, delete, latch));
        }

        try {
            latch.await();
            pool.shutdown();
        } catch (InterruptedException e) {
             throw new CollectorException(e);
        }
    }
    
    /**
     * @return <code>true</code> if more urls to process
     */
    private boolean processNextURL(
            final IDocCrawlStore docCrawlStore,
            final JobStatusUpdater statusUpdater, 
            final boolean delete) {
        HttpDocCrawl queuedURL = (HttpDocCrawl) docCrawlStore.nextQueued();
        if (LOG.isDebugEnabled()) {
            LOG.debug(getId() 
                    + " Processing next URL from Queue: " + queuedURL);
        }
        if (queuedURL != null) {
            if (isMaxURLs()) {
                LOG.info(getId() + ": Maximum URLs reached: " 
                        + getCrawlerConfig().getMaxURLs());
                return false;
            }
            StopWatch watch = null;
            if (LOG.isDebugEnabled()) {
                watch = new StopWatch();
                watch.start();
            }
            int preOKCount = okURLsCount;
            processNextQueuedURL(queuedURL, docCrawlStore, delete);
            if (preOKCount != okURLsCount) {
                statusUpdater.getProperties().setInt(
                        PROP_OK_URL_COUNT, okURLsCount);
            }
            setProgress(statusUpdater, docCrawlStore);
            if (LOG.isDebugEnabled()) {
                watch.stop();
                LOG.debug(getId() + ": " + watch.toString() 
                        + " to process: " + queuedURL.getReference());
            }
        } else {
            int activeCount = docCrawlStore.getActiveCount();
            boolean queueEmpty = docCrawlStore.isQueueEmpty();
            if (LOG.isDebugEnabled()) {
                LOG.debug(getId() 
                        + " URLs currently being processed: " + activeCount);
                LOG.debug(getId() 
                        + " Is URL queue empty? " + queueEmpty);
            }
            if (activeCount == 0 && queueEmpty) {
                return false;
            }
            Sleeper.sleepMillis(MINIMUM_DELAY);
        }
        return true;
    }
    
    private void setProgress(
            JobStatusUpdater statusUpdater, IDocCrawlStore db) {
        int queued = db.getQueueSize();
        int processed = db.getProcessedCount();
        int total = queued + processed;

        double progress = 0;
        
        if (total != 0) {
            progress = BigDecimal.valueOf(processed)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.DOWN)
                    .doubleValue();
        }
        statusUpdater.setProgress(progress);

        statusUpdater.setNote(
                NumberFormat.getIntegerInstance().format(processed)
                + " urls processed out of "
                + NumberFormat.getIntegerInstance().format(total));
        

        if (LOG.isInfoEnabled()) {
            int percent = BigDecimal.valueOf(progress).movePointLeft(-2)
                    .intValue();
            if (lastPercent != percent) {
                LOG.info(BigDecimal.valueOf(progress).movePointLeft(-2)
                        .intValue() + "% completed (" 
                        + processed + " processed/" + total + " total)");
            }
            lastPercent = percent;
        }
    }

    private void deleteURL(
            IDocCrawl docCrawl, /*File outputFile,*/ HttpDocument doc) {
        LOG.debug(getId() + ": Deleting URL: " + docCrawl.getReference());
        ICommitter committer = getCrawlerConfig().getCommitter();
        ((HttpDocCrawl) docCrawl).setState(HttpDocCrawlState.DELETED);
        if (committer != null) {
            committer.remove(docCrawl.getReference(), doc.getMetadata());
        }
    }
    
    private void processNextQueuedURL(HttpDocCrawl docCrawl, 
            IDocCrawlStore docCrawlStore, boolean delete) {
        String url = docCrawl.getReference();
        HttpDocument doc = new HttpDocument(
                docCrawl.getReference(), streamFactory.newInputStream());
        setURLMetadata(doc.getMetadata(), docCrawl);
        
        boolean fullyProcessed = false;
        
        try {
            if (delete) {
                deleteURL(docCrawl, doc);
                return;
            } else if (LOG.isDebugEnabled()) {
                LOG.debug(getId() + ": Processing URL: " + url);
            }
            
            //TODO create pipeline context prototype
            //TODO cache the pipeline object?
            DocumentPipelineContext context = new DocumentPipelineContext(
                    this, docCrawlStore, doc, docCrawl, importer);

//            if (new DocumentPipeline().execute(context)) {
            new DocumentPipeline().execute(context);
            ImporterResponse response = context.getImporterResponse();
            
            
            
            if (response != null) {
                processImportResponse(response, docCrawlStore, docCrawl);
                fullyProcessed = true;
            } else {
                fireDocCrawlEvent(new DocCrawlEvent(
                      DocCrawlEvent.REJECTED_IMPORT, docCrawl, response));
            }
//            } else {
//                LOG.debug("Document process pipeline ended before "
//                        + "completion for : " + url);
//            }
            
            
            
//            if (!new DocumentProcessor(this, httpClient, refStore, 
//                    outputFile, doc, reference, sitemapResolver).processURL()) {
//                return;
//            }
        } catch (Exception e) {
            //TODO do we really want to catch anything other than 
            // HTTPFetchException?  In case we want special treatment to the 
            // class?
            ((HttpDocCrawl) docCrawl).setState(HttpDocCrawlState.ERROR);
            LOG.error(getId() + ": Could not process document: " + url
                    + " (" + e.getMessage() + ")", e);
	    } finally {
	        if (!fullyProcessed) {
	            finalizeURLProcessing(docCrawl, docCrawlStore, /*url, outputFile,*/ doc);
	        }
	    }
	}
    
    private void processImportResponse(
            ImporterResponse response, 
            IDocCrawlStore docCrawlStore,
            HttpDocCrawl docCrawl) {
        HttpDocument doc = null;
        try {
            if (response.isSuccess()) {
                fireDocCrawlEvent(new DocCrawlEvent(
                        DocCrawlEvent.DOCUMENT_IMPORTED, docCrawl, response));
                doc = new HttpDocument(response.getDocument());
                PostImportPipelineContext context = 
                        new PostImportPipelineContext(
                                this, docCrawlStore, doc, docCrawl);
                new PostImportPipeline().execute(context);
            } else {
                fireDocCrawlEvent(new DocCrawlEvent(
                        DocCrawlEvent.REJECTED_IMPORT, docCrawl, response));
                LOG.debug("Importing unsuccessful for \"" 
                        + docCrawl.getReference() + "\": "
                        + response.getImporterStatus().getDescription());
            }
            ImporterResponse[] children = response.getNestedResponses();
            for (ImporterResponse child : children) {
                HttpDocCrawl childDocCrawl = new HttpDocCrawl(
                        child.getReference(), docCrawl.getDepth());
                processImportResponse(child, docCrawlStore, childDocCrawl);
            }
        } finally {
            finalizeURLProcessing(docCrawl, docCrawlStore, doc);;
        }
    }

    private void finalizeURLProcessing(HttpDocCrawl docCrawl,
            IDocCrawlStore refStore,/* String url, File outputFile,*/
            HttpDocument doc) {
        //--- Flag URL for deletion --------------------------------------------
        try {
            ICommitter committer = getCrawlerConfig().getCommitter();
            if (refStore.isVanished(docCrawl)) {
                docCrawl.setState(
                        HttpDocCrawlState.DELETED);
                if (committer != null) {
                    committer.remove(
                            docCrawl.getReference(), doc.getMetadata());
                }
            }
        } catch (Exception e) {
            LOG.error(getId() + ": Could not flag URL for deletion: "
                    + docCrawl.getReference()
                    + " (" + e.getMessage() + ")", e);
        }
        
        //--- Mark URL as Processed --------------------------------------------
        try {
            if (docCrawl.getState().isCommittable()) {
                okURLsCount++;
            }
            refStore.processed(docCrawl);
            markOriginalURLAsProcessed(docCrawl, refStore);
            if (docCrawl.getState() == null) {
                LOG.warn("URL status is unknown: " + docCrawl.getReference());
                docCrawl.setState(HttpDocCrawlState.BAD_STATUS);
            }
        } catch (Exception e) {
            LOG.error(getId() + ": Could not mark URL as processed: " 
                    + docCrawl.getReference()
                    + " (" + e.getMessage() + ")", e);
        }

        try {
            if (doc != null) {
                doc.getContent().dispose();
            }
        } catch (Exception e) {
            LOG.error("Could not dispose of resources.", e);
        }
    }

    private void markOriginalURLAsProcessed(
            HttpDocCrawl docCrawl, IDocCrawlStore refStore) {
        if (StringUtils.isNotBlank(docCrawl.getOriginalReference()) 
                && ObjectUtils.notEqual(docCrawl.getOriginalReference(), 
                        docCrawl.getReference())) {
            HttpDocCrawl originalURL = (HttpDocCrawl) docCrawl.safeClone();
            originalURL.setReference(docCrawl.getOriginalReference());
            originalURL.setOriginalReference(null);
            refStore.processed(originalURL);
        }
    }

    private void initializeHTTPClient() {
        httpClient = getCrawlerConfig().getHttpClientFactory().createHTTPClient(
                getCrawlerConfig().getUserAgent());
	}

    @Override
    public void stop(IJobStatus jobStatus, JobSuite suite) {
        stopped = true;
        LOG.info("Stopping the crawler \"" + jobStatus.getJobId() +  "\".");
    }
    private File gelBaseDownloadDir() {
        return new File(getCrawlerConfig().getWorkDir().getAbsolutePath() 
                + "/downloads");
    }
    private File gelCrawlerDownloadDir() {
        return new File(gelBaseDownloadDir() 
                + "/" + getCrawlerConfig().getId());
    }
    
    private boolean isMaxURLs() {
        return getCrawlerConfig().getMaxURLs() > -1 
                && okURLsCount >= getCrawlerConfig().getMaxURLs();
    }
    private void setURLMetadata(HttpMetadata metadata, IDocCrawl ref) {
        HttpDocCrawl url = (HttpDocCrawl) ref;
        
        metadata.addInt(HttpMetadata.COLLECTOR_DEPTH, url.getDepth());
        if (StringUtils.isNotBlank(url.getSitemapChangeFreq())) {
            metadata.addString(HttpMetadata.COLLECTOR_SM_CHANGE_FREQ, 
                    url.getSitemapChangeFreq());
        }
        if (url.getSitemapLastMod() != null) {
            metadata.addLong(HttpMetadata.COLLECTOR_SM_LASTMOD, 
                    url.getSitemapLastMod());
        }        
        if (url.getSitemapPriority() != null) {
            metadata.addFloat(HttpMetadata.COLLECTOR_SM_PRORITY, 
                    url.getSitemapPriority());
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
    
    private final class ProcessURLsRunnable implements Runnable {
        private final JobSuite suite;
        private final JobStatusUpdater statusUpdater;
        private final IDocCrawlStore crawlStore;
        private final boolean delete;
        private final CountDownLatch latch;

        private ProcessURLsRunnable(JobSuite suite, 
                JobStatusUpdater statusUpdater,
                IDocCrawlStore refStore, boolean delete,
                CountDownLatch latch) {
            this.suite = suite;
            this.statusUpdater = statusUpdater;
            this.crawlStore = refStore;
            this.delete = delete;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                while (!isStopped()) {
                    try {
                        if (!processNextURL(
                                crawlStore, statusUpdater, delete)) {
                            break;
                        }
                    } catch (Exception e) {
                        LOG.fatal(getId() + ": "
                            + "An error occured that could compromise "
                            + "the stability of the crawler. Stopping "
                            + "excution to avoid further issues...", e);
                        stop(suite.getJobStatus(suite.getRootJob()), suite);
                    }
                }
            } catch (Exception e) {
                LOG.error(getId() + ": Problem in thread execution.", e);
            } finally {
                latch.countDown();
            }
        }
    }


    public void fireDocCrawlEvent(DocCrawlEvent event) {
        crawlerEventManager.crawlerDocumentEvent(event);
    }
}
