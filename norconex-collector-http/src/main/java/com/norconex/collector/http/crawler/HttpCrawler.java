/* Copyright 2010-2013 Norconex Inc.
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
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.AbstractCrawler;
import com.norconex.collector.core.ref.IReference;
import com.norconex.collector.core.ref.store.IReferenceStore;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doc.pipe.DocumentPipeline;
import com.norconex.collector.http.doc.pipe.DocumentPipelineContext;
import com.norconex.collector.http.ref.HttpDocReference;
import com.norconex.collector.http.ref.HttpDocReferenceState;
import com.norconex.collector.http.ref.pipe.ReferencePipeline;
import com.norconex.collector.http.ref.pipe.ReferencePipelineContext;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.util.PathUtils;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.jef4.status.IJobStatus;
import com.norconex.jef4.status.JobStatusUpdater;
import com.norconex.jef4.suite.JobSuite;

/**
 * The HTTP Crawler.
 * @author Pascal Essiembre
 */
@SuppressWarnings("deprecation")
public class HttpCrawler extends AbstractCrawler {
	
    private static final Logger LOG = LogManager.getLogger(HttpCrawler.class);
	
    private static final String PROP_OK_URL_COUNT = "okUrlCount";
	private static final int MINIMUM_DELAY = 10;
    private static final int FILE_DELETE_COUNT_MAX = 100;
	
	private HttpClient httpClient;
	private ISitemapResolver sitemapResolver;
    private boolean stopped;
    private int okURLsCount;
    private int deletedDownloadCount;
    
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
    
    /**
     * @return the sitemapResolver
     */
    public ISitemapResolver getSitemapResolver() {
        return sitemapResolver;
    }

    @Override
    protected void prepareExecution(
            JobStatusUpdater statusUpdater, JobSuite suite, 
            IReferenceStore refStore, boolean resume) {
        logInitializationInformation();
        initializeHTTPClient();
        
        this.sitemapResolver = getCrawlerConfig().getSitemapResolverFactory()
                .createSitemapResolver(getCrawlerConfig(), resume);
        
        if (resume) {
            okURLsCount = statusUpdater.getProperties().getInt(
                    PROP_OK_URL_COUNT, 0);
        } else {
            String[] startURLs = getCrawlerConfig().getStartURLs();
            for (int i = 0; i < startURLs.length; i++) {
                String startURL = startURLs[i];
                ReferencePipelineContext context = new ReferencePipelineContext(
                        this, refStore, new HttpDocReference(startURL, 0));
                new ReferencePipeline().process(context);
            }
            HttpCrawlerEventFirer.fireCrawlerStarted(this);
        }
        
    }
    
    private void logInitializationInformation() {
        LOG.info(getId() +  ": RobotsTxt support: " 
                + getCrawlerConfig().isIgnoreRobotsTxt());
        LOG.info(getId() +  ": RobotsMeta support: "  
                + getCrawlerConfig().isIgnoreRobotsMeta()); 
        LOG.info(getId() +  ": Sitemap support: " 
                + getCrawlerConfig().isIgnoreSitemap());
    }

    @Override
    protected void cleanupExecution(JobStatusUpdater statusUpdater,
            JobSuite suite, IReferenceStore refStore) {
        closeHttpClient();
    }
    
    @Override
    protected void execute(JobStatusUpdater statusUpdater,
            JobSuite suite, IReferenceStore refStore) {

        
        //TODO move this code to a config validator class?
        //TODO move this code to base class?
        if (StringUtils.isBlank(getCrawlerConfig().getId())) {
            throw new CollectorException("HTTP Crawler must be given "
                    + "a unique identifier (id).");
        }

        
//        StopWatch watch = new StopWatch();
//        watch.start();
//        

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

        deleteDownloadDirectory();
        
//        watch.stop();
//        LOG.info(getId() + ": "
//                + refStore.getProcessedCount() + " URLs processed "
//                + "in " + watch.toString() + " for \"" + getId() + "\".");

        LOG.debug(getId() + ": Removing empty directories");
        FileUtil.deleteEmptyDirs(gelCrawlerDownloadDir());

        if (!stopped) {
            HttpCrawlerEventFirer.fireCrawlerFinished(this);
        }
        LOG.info(getId() + ": Crawler \"" + getId() + "\" " 
                + (stopped ? "stopped." : "completed."));
    }

    private void deleteDownloadDirectory() {
        if (!getCrawlerConfig().isKeepDownloads()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Deleting crawler downloads directory: "
                        + gelCrawlerDownloadDir());
            }
            try {
                FileUtil.deleteFile(gelCrawlerDownloadDir());
            } catch (IOException e) {
                LOG.error(getId() 
                        + ": Could not delete the crawler downloads directory: "
                        + gelCrawlerDownloadDir(), e);
            }
        }
    }

    private void handleOrphans(IReferenceStore refStore,
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
            IReferenceStore refStore, JobStatusUpdater statusUpdater, JobSuite suite) {
        long count = 0;
        Iterator<IReference> it = refStore.getCacheIterator();
        if (it != null) {
            while (it.hasNext()) {
                HttpDocReference reference = (HttpDocReference) it.next();
                ReferencePipelineContext context = new ReferencePipelineContext(
                        this, refStore, reference);
                new ReferencePipeline().process(context);
                count++;
            }
            processURLs(refStore, statusUpdater, suite, false);
        }
        LOG.info(getId() + ": Reprocessed " + count + " orphan URLs...");
    }
    
    private void deleteCacheOrphans(
            IReferenceStore refStore, JobStatusUpdater statusUpdater, JobSuite suite) {
        long count = 0;
        Iterator<IReference> it = refStore.getCacheIterator();
        if (it != null) {
            while (it.hasNext()) {
                refStore.queue(it.next());
                count++;
            }
            processURLs(refStore, statusUpdater, suite, true);
        }
        LOG.info(getId() + ": Deleted " + count + " orphan URLs...");
    }
    
//    /*default*/ HttpCrawlerConfig getCrawlerConfig() {
//        return crawlerConfig;
//    }
//    
    private void processURLs(
    		final IReferenceStore refStore,
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
            final IReferenceStore refStore,
            final JobStatusUpdater statusUpdater, 
            final boolean delete) {
        HttpDocReference queuedURL = (HttpDocReference) refStore.nextQueued();
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
            StopWatch watch = new StopWatch();
            watch.start();
            int preOKCount = okURLsCount;
            processNextQueuedURL(queuedURL, refStore, delete);
            if (preOKCount != okURLsCount) {
                statusUpdater.getProperties().setInt(
                        PROP_OK_URL_COUNT, okURLsCount);
            }
            setProgress(statusUpdater, refStore);
            watch.stop();
            if (LOG.isDebugEnabled()) {
                LOG.debug(getId() + ": " + watch.toString() 
                        + " to process: " + queuedURL.getReference());
            }
        } else {
            int activeCount = refStore.getActiveCount();
            boolean queueEmpty = refStore.isQueueEmpty();
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
            JobStatusUpdater statusUpdater, IReferenceStore db) {
        int queued = db.getQueueSize();
        int processed = db.getProcessedCount();
        int total = queued + processed;
        if (total == 0) {
            statusUpdater.setProgress(0); //TODO was previously set to maximum, why?
        } else {
            statusUpdater.setProgress(BigDecimal.valueOf(processed)
                    .divide(BigDecimal.valueOf(total), RoundingMode.DOWN)
                    .doubleValue());
        }
        statusUpdater.setNote(
                NumberFormat.getIntegerInstance().format(processed)
                + " urls processed out of "
                + NumberFormat.getIntegerInstance().format(total));
    }

    private File createLocalFile(IReference httpDocReference, String extension) {
        return new File(gelCrawlerDownloadDir().getAbsolutePath() 
                + SystemUtils.FILE_SEPARATOR 
                + PathUtils.urlToPath(httpDocReference.getReference())
                + extension);
    }
    
    private void deleteURL(
            IReference reference, File outputFile, HttpDocument doc) {
        LOG.debug(getId() + ": Deleting URL: " + reference.getReference());
        ICommitter committer = getCrawlerConfig().getCommitter();
        ((HttpDocReference) reference).setState(HttpDocReferenceState.DELETED);
        if (committer != null) {
            committer.queueRemove(
                    reference.getReference(), outputFile, doc.getMetadata());
        }
    }
    
    private void processNextQueuedURL(HttpDocReference reference, 
            IReferenceStore refStore, boolean delete) {
        String url = reference.getReference();
//        File outputFile = createLocalFile(reference, ".txt");
//        HttpDocument doc = new HttpDocument(
//                reference.getReference(), createLocalFile(reference, ".raw"));
        HttpDocument doc = new HttpDocument(reference);
        setURLMetadata(doc.getMetadata(), reference);
        
        try {
            if (delete) {
                File outputFile = File.createTempFile(
                        "committer-delete-", ".txt", 
                        getCrawlerConfig().getWorkDir());
                FileUtils.copyInputStreamToFile(
                        doc.getContent().getInputStream(), outputFile);

                
                deleteURL(reference, outputFile, doc);
                return;
            } else if (LOG.isDebugEnabled()) {
                LOG.debug(getId() + ": Processing URL: " + url);
            }
            
            //TODO create pipeline context prototype
            //TODO cache the pipeline object?
            DocumentPipelineContext context = new DocumentPipelineContext(
                    this, refStore, doc/*, robotsTxt*/);
            if (!new DocumentPipeline().process(context)) {
                
//                LOG.error(context.getImporterResponse().getImporterStatus().getDescription());
                
                return;
            }
            
            
            
//            if (!new DocumentProcessor(this, httpClient, refStore, 
//                    outputFile, doc, reference, sitemapResolver).processURL()) {
//                return;
//            }
        } catch (Exception e) {
            //TODO do we really want to catch anything other than 
            // HTTPFetchException?  In case we want special treatment to the 
            // class?
            ((HttpDocReference) reference).setState(HttpDocReferenceState.ERROR);
            LOG.error(getId() + ": Could not process document: " + url
                    + " (" + e.getMessage() + ")", e);
	    } finally {
            finalizeURLProcessing(reference, refStore, url, /*outputFile,*/ doc);
	    }
	}

    private void finalizeURLProcessing(IReference reference,
            IReferenceStore refStore, String url, /*File outputFile,*/
            HttpDocument doc) {
        //--- Flag URL for deletion --------------------------------------------
        try {
            ICommitter committer = getCrawlerConfig().getCommitter();
            if (refStore.isVanished(reference)) {
                ((HttpDocReference) reference).setState(
                        HttpDocReferenceState.DELETED);
                if (committer != null) {
                    
                    File outputFile = File.createTempFile(
                            "committer-delete-", ".txt", 
                            getCrawlerConfig().getWorkDir());
                    FileUtils.copyInputStreamToFile(
                            doc.getContent().getInputStream(), outputFile);
                    
                    committer.queueRemove(url, outputFile, doc.getMetadata());
                }
            }
        } catch (Exception e) {
            LOG.error(getId() + ": Could not flag URL for deletion: " + url
                    + " (" + e.getMessage() + ")", e);
        }
        
        //--- Mark URL as Processed --------------------------------------------
        try {
            if (reference.getState().isOneOf(HttpDocReferenceState.NEW, HttpDocReferenceState.MODIFIED)) {
//            if (reference.getState() == HttpDocReferenceState.OK) {
                okURLsCount++;
            }
            refStore.processed(reference);
            markOriginalURLAsProcessed(reference, refStore, doc);
            if (reference.getState() == null) {
                LOG.warn("URL status is unknown: " + reference.getReference());
                ((HttpDocReference) reference).setState(HttpDocReferenceState.BAD_STATUS);
            }
//            reference.getState().logInfo(reference);
        } catch (Exception e) {
            LOG.error(getId() + ": Could not mark URL as processed: " + url
                    + " (" + e.getMessage() + ")", e);
        }

        try {
            //--- Delete Local File Download -----------------------------------
            if (!getCrawlerConfig().isKeepDownloads()) {
                
                //TODO write content to file if keeping download, else dispose
                
                doc.getContent().getInputStream().dispose();
                
                
//                LOG.debug("Deleting " + doc.getLocalFile());
//                FileUtils.deleteQuietly(doc.getLocalFile());
//                FileUtils.deleteQuietly(outputFile);
                deleteDownloadDirIfReady();
            }
        } catch (Exception e) {
            LOG.error("Could not dispose of resources.", e);
//            LOG.error(getId() + ": Could not delete local file: "
//                    + doc.getLocalFile() + " (" + e.getMessage() + ")", e);
        }
    }

    private void deleteDownloadDirIfReady() {
        deletedDownloadCount++;
        if (deletedDownloadCount % FILE_DELETE_COUNT_MAX == 0) {
            final long someTimeAgo = System.currentTimeMillis() 
                    - (DateUtils.MILLIS_PER_MINUTE * 2);
            File dir = gelCrawlerDownloadDir();
            Date date = new Date(someTimeAgo);
            int dirCount = FileUtil.deleteEmptyDirs(dir, date);
            if (LOG.isDebugEnabled()) {
                LOG.debug(getId() + ": Deleted " + dirCount 
                        + " empty directories under " + dir);
            }
        }
    }

    private void markOriginalURLAsProcessed(IReference reference,
            IReferenceStore refStore, HttpDocument doc) {
        //TODO Fix #17. Put in place a more permanent solution to this fix?
        if (ObjectUtils.notEqual(doc.getReference(), reference.getReference())) {
            IReference originalURL = reference.safeClone();
            ((HttpDocReference) originalURL).setReference(doc.getReference());
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
        return new File(
                getCrawlerConfig().getWorkDir().getAbsolutePath() + "/downloads");
    }
    private File gelCrawlerDownloadDir() {
        return new File(gelBaseDownloadDir() + "/" + getCrawlerConfig().getId());
    }
    
    private boolean isMaxURLs() {
        return getCrawlerConfig().getMaxURLs() > -1 
                && okURLsCount >= getCrawlerConfig().getMaxURLs();
    }
    private void setURLMetadata(HttpMetadata metadata, IReference ref) {
        HttpDocReference url = (HttpDocReference) ref;
        
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
        private final IReferenceStore refStore;
        private final boolean delete;
        private final CountDownLatch latch;

        private ProcessURLsRunnable(JobSuite suite, JobStatusUpdater statusUpdater,
                IReferenceStore refStore, boolean delete,
                CountDownLatch latch) {
            this.suite = suite;
            this.statusUpdater = statusUpdater;
            this.refStore = refStore;
            this.delete = delete;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                while (!isStopped()) {
                    try {
                        if (!processNextURL(refStore, statusUpdater, delete)) {
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



}
