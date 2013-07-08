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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.util.PathUtils;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.io.FileUtil;
import com.norconex.jef.AbstractResumableJob;
import com.norconex.jef.IJobContext;
import com.norconex.jef.progress.IJobStatus;
import com.norconex.jef.progress.JobProgress;
import com.norconex.jef.suite.JobSuite;

public class HttpCrawler extends AbstractResumableJob {
	
	private static final Logger LOG = LogManager.getLogger(HttpCrawler.class);
	
	private static final int MINIMUM_DELAY = 10;
	
	private final HttpCrawlerConfig crawlerConfig;
    private DefaultHttpClient httpClient;
    private final IHttpCrawlerEventListener[] listeners;
    private boolean stopped;
    private int okURLsCount;
    
	public HttpCrawler(
	        HttpCrawlerConfig crawlerConfig) {
		super();
		this.crawlerConfig = crawlerConfig;
		IHttpCrawlerEventListener[] ls = crawlerConfig.getCrawlerListeners();
		if (ls == null) {
		    this.listeners = new IHttpCrawlerEventListener[]{};
		} else {
	        this.listeners = crawlerConfig.getCrawlerListeners();
		}
        crawlerConfig.getWorkDir().mkdirs();
	}
	
	@Override
	public IJobContext createJobContext() {
	    return new IJobContext() {            
            private static final long serialVersionUID = 2785476254478043557L;
            @Override
            public long getProgressMinimum() {
                return IJobContext.PROGRESS_ZERO;
            }
            @Override
            public long getProgressMaximum() {
                return IJobContext.PROGRESS_100;
            }
            @Override
            public String getDescription() {
                return "Norconex HTTP Crawler";
            }
        };
	}
	
	
	
	@Override
	public String getId() {
		return crawlerConfig.getId();
	}

    public boolean isStopped() {
        return stopped;
    }
    
    @Override
    protected void resumeExecution(JobProgress progress, JobSuite suite) {
        ICrawlURLDatabase database = 
              crawlerConfig.getCrawlURLDatabaseFactory().createCrawlURLDatabase(
                      crawlerConfig, true);
        initializeHTTPClient();
        okURLsCount = NumberUtils.toInt(progress.getMetadata());
        try {
            execute(database, progress, suite);
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    @Override
    protected void startExecution(JobProgress progress, JobSuite suite) {
        ICrawlURLDatabase database = 
              crawlerConfig.getCrawlURLDatabaseFactory().createCrawlURLDatabase(
                      crawlerConfig, false);
        initializeHTTPClient();
        String[] startURLs = crawlerConfig.getStartURLs();
        for (int i = 0; i < startURLs.length; i++) {
            String startURL = startURLs[i];
            new URLProcessor(this, httpClient, database, 
                    new CrawlURL(startURL, 0)).processURL();
        }
        for (IHttpCrawlerEventListener listener : listeners) {
            listener.crawlerStarted(this);
        }
        try {
            execute(database, progress, suite);
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }
	
    private void execute(
            ICrawlURLDatabase database, JobProgress progress, JobSuite suite) {

        //TODO print initialization information
        LOG.info("RobotsTxt support " + 
                (crawlerConfig.isIgnoreRobotsTxt() ? "disabled." : "enabled"));
        LOG.info("RobotsMeta support " + 
                (crawlerConfig.isIgnoreRobotsMeta() ? "disabled." : "enabled"));

        //TODO consider offering threading here?
        processURLs(database, progress, suite, false);

        // Process what remains in cache
        if (!isMaxURLs()) {
            database.queueCache();
            processURLs(database, progress, suite, crawlerConfig.isDeleteOrphans());
        }

        ICommitter committer = crawlerConfig.getCommitter();
        if (committer != null) {
            LOG.info("Crawler \"" + getId() + "\" " 
                    + (stopped ? "stopping" : "finishing")
                    + ": committing documents.");
            committer.commit();
        }
        
        LOG.debug("Removing empty directories");
        FileUtil.deleteEmptyDirs(gelCrawlerDownloadDir());
        
        if (!stopped) {
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.crawlerFinished(this);
            }
        }
        LOG.info("Crawler \"" + getId() + "\" " 
                + (stopped ? "stopped." : "completed."));
    }
    
    /*default*/ HttpCrawlerConfig getCrawlerConfig() {
        return crawlerConfig;
    }
    
    private void processURLs(
    		final ICrawlURLDatabase database,
    		final JobProgress progress, 
    		final JobSuite suite,
    		final boolean delete) {
        
        int numThreads = crawlerConfig.getNumThreads();
        final CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i + 1;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    LOG.debug("Crawler thread #" + threadIndex + " started.");
                    while (!isStopped()) {
                        try {
                            if (!processNextURL(database, progress, delete)) {
                                break;
                            }
                        } catch (Exception e) {
                            LOG.fatal("An error occured that could compromise "
                                + "the stability of the crawler. Stopping "
                                + "excution to avoid further issues...", e);
                            stop(progress, suite);
                        }
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            pool.shutdown();
        } catch (InterruptedException e) {
             throw new HttpCollectorException(e);
        }
        
        //--- Delete Download Dir ----------------------------------------------
        if (!crawlerConfig.isKeepDownloads()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting downloads directory: "
                        + gelBaseDownloadDir());
            }
            try {
                FileUtil.deleteFile(gelBaseDownloadDir());
            } catch (IOException e) {
                LOG.error("Could not delete the downloads directory: "
                        + gelBaseDownloadDir(), e);
            }
        }
    }
    
    /**
     * @return <code>true</code> if more urls to process
     */
    private boolean processNextURL(
            final ICrawlURLDatabase database,
            final JobProgress progress, 
            final boolean delete) {
        CrawlURL queuedURL = database.next();
        if (queuedURL != null) {
            if (isMaxURLs()) {
                LOG.info("Maximum URLs reached: " + crawlerConfig.getMaxURLs());
                return false;
            }
            StopWatch watch = new StopWatch();
            watch.start();
            int preOKCount = okURLsCount;
            processNextQueuedURL(queuedURL, database, delete);
            if (preOKCount != okURLsCount) {
                progress.setMetadata(Integer.toString(okURLsCount));
            }
            setProgress(progress, database);
            watch.stop();
            if (LOG.isDebugEnabled()) {
                LOG.debug(watch.toString() 
                        + " to process: " + queuedURL.getUrl());
            }
        } else {
            if (database.getActiveCount() == 0 && database.isQueueEmpty()) {
                return false;
            }
            Sleeper.sleepMillis(MINIMUM_DELAY);
        }
        return true;
    }
    
    private void setProgress(JobProgress progress, ICrawlURLDatabase db) {
        int queued = db.getQueueSize();
        int processed = db.getProcessedCount();
        int total = queued + processed;
        if (total == 0) {
            progress.setProgress(progress.getJobContext().getProgressMaximum());
        } else {
            progress.setProgress(BigDecimal.valueOf(processed)
                    .divide(BigDecimal.valueOf(total), RoundingMode.DOWN)
                    .multiply(BigDecimal.valueOf(IJobContext.PROGRESS_100))
                    .intValue());
        }
        progress.setNote(
                NumberFormat.getIntegerInstance().format(processed)
                + " urls processed out of "
                + NumberFormat.getIntegerInstance().format(total));
    }

    private File createLocalFile(CrawlURL crawlURL, String extension) {
        return new File(gelCrawlerDownloadDir().getAbsolutePath() 
                + SystemUtils.FILE_SEPARATOR 
                + PathUtils.urlToPath(crawlURL.getUrl())
                + extension);
    }
    
    private void deleteURL(
            CrawlURL crawlURL, File outputFile, HttpDocument doc) {
        LOG.debug("Deleting URL: " + crawlURL.getUrl());
        ICommitter committer = crawlerConfig.getCommitter();
        crawlURL.setStatus(CrawlStatus.DELETED);
        if (committer != null) {
            committer.queueRemove(
                    crawlURL.getUrl(), outputFile, doc.getMetadata());
        }
    }
    
    private void processNextQueuedURL(
            CrawlURL crawlURL, ICrawlURLDatabase database, boolean delete) {
        String url = crawlURL.getUrl();
        File outputFile = createLocalFile(crawlURL, ".txt");
        HttpDocument doc = new HttpDocument(
                crawlURL.getUrl(), createLocalFile(crawlURL, ".raw"));        
        try {
            if (delete) {
                deleteURL(crawlURL, outputFile, doc);
                return;
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Processing URL: " + url);
            }
            
            if (!new DocumentProcessor(this, httpClient, database, 
                    outputFile, doc, crawlURL).processURL()) {
                return;
            }
        } catch (Exception e) {
            //TODO do we really want to catch anything other than 
            // HTTPFetchException?  In case we want special treatment to the 
            // class?
            crawlURL.setStatus(CrawlStatus.ERROR);
            if (LOG.isDebugEnabled()) {
                LOG.error("Could not process document: " + url
                        + " (" + e.getMessage() + ")", e);
            } else {
                LOG.error("Could not process document: " + url
                        + " (" + e.getMessage() + ")");
            }
	    } finally {
            //--- Flag URL for deletion ----------------------------------------
	        ICommitter committer = crawlerConfig.getCommitter();
            if (database.isVanished(crawlURL)) {
                crawlURL.setStatus(CrawlStatus.DELETED);
                if (committer != null) {
                    committer.queueRemove(url, outputFile, doc.getMetadata());
                }
            }
	    	
            //--- Mark URL as Processed ----------------------------------------
            if (crawlURL.getStatus() == CrawlStatus.OK) {
                okURLsCount++;
            }
            database.processed(crawlURL);
            crawlURL.getStatus().logInfo(crawlURL);
            
            //--- Delete Local File Download -----------------------------------
            if (!crawlerConfig.isKeepDownloads()) {
                LOG.debug("Deleting " + doc.getLocalFile());
                FileUtils.deleteQuietly(doc.getLocalFile());
            }
	    }
	}

	private void initializeHTTPClient() {
        //TODO set HTTPClient user-agent from config before calling init..
	    PoolingClientConnectionManager m = new PoolingClientConnectionManager();
	    if (crawlerConfig.getNumThreads() <= 0) {
	        m.setMaxTotal(Integer.MAX_VALUE);
	    } else {
	        m.setMaxTotal(crawlerConfig.getNumThreads());
	    }
        httpClient = new DefaultHttpClient(m);
        crawlerConfig.getHttpClientInitializer().initializeHTTPClient(
                httpClient);
	}

    @Override
    public void stop(IJobStatus progress, JobSuite suite) {
        stopped = true;
        LOG.info("Stopping the crawler \"" + progress.getJobId() +  "\".");
    }
    private File gelBaseDownloadDir() {
        return new File(
                crawlerConfig.getWorkDir().getAbsolutePath() + "/downloads");
    }
    private File gelCrawlerDownloadDir() {
        return new File(gelBaseDownloadDir() + "/" + crawlerConfig.getId());
    }
    
    private boolean isMaxURLs() {
        return crawlerConfig.getMaxURLs() > -1 
                && okURLsCount >= crawlerConfig.getMaxURLs();
    }
}
