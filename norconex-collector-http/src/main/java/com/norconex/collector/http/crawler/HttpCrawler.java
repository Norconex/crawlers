package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.db.CrawlURL;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IHttpDocumentChecksummer;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.handler.IHttpHeadersChecksummer;
import com.norconex.collector.http.handler.IHttpHeadersFetcher;
import com.norconex.collector.http.handler.IURLExtractor;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.util.PathUtils;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.io.FileUtil;
import com.norconex.commons.lang.meta.Metadata;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.jef.AbstractResumableJob;
import com.norconex.jef.IJobContext;
import com.norconex.jef.progress.IJobStatus;
import com.norconex.jef.progress.JobProgress;
import com.norconex.jef.suite.JobSuite;

public class HttpCrawler extends AbstractResumableJob {
	
	private static final Logger LOG = LogManager.getLogger(HttpCrawler.class);
	
	
    private final MultiThreadedHttpConnectionManager connectionManager = 
            new MultiThreadedHttpConnectionManager();
	private final HttpCrawlerConfig httpConfig;
    private final ImporterConfig importConfig;
    private HttpClient httpClient;
    private final IHttpCrawlerEventListener[] listeners;
    private boolean stopped;
	//TODO have config being overwritable... JEF CCOnfig does that...
    
	public HttpCrawler(
	        HttpCrawlerConfig httpConfig) {
		super();
		this.httpConfig = httpConfig;
		this.importConfig = httpConfig.getImporterConfig();
		IHttpCrawlerEventListener[] ls = httpConfig.getCrawlerListeners();
		if (ls == null) {
		    this.listeners = new IHttpCrawlerEventListener[]{};
		} else {
	        this.listeners = httpConfig.getCrawlerListeners();
		}
        httpConfig.getWorkDir().mkdirs();
	}
	
	@Override
	public IJobContext createJobContext() {
	    return new IJobContext() {            
            private static final long serialVersionUID = 2785476254478043557L;
            @Override
            public long getProgressMinimum() {
                return 0;
            }
            @Override
            public long getProgressMaximum() {
                return 100;
            }
            @Override
            public String getDescription() {
                return "Norconex HTTP Crawler";
            }
        };
	}
	
	@Override
	public String getId() {
		return httpConfig.getId();
	}

    public boolean isStopped() {
        return stopped;
    }
    
    @Override
    protected void resumeExecution(JobProgress progress, JobSuite suite) {
        ICrawlURLDatabase database = 
                httpConfig.getCrawlURLDatabaseFactory().createCrawlURLDatabase(
                        httpConfig, true);
        execute(database, progress, suite);
    }

    @Override
    protected void startExecution(JobProgress progress, JobSuite suite) {
        ICrawlURLDatabase database = 
                httpConfig.getCrawlURLDatabaseFactory().createCrawlURLDatabase(
                        httpConfig, false);
        String[] startURLs = httpConfig.getStartURLs();
        for (int i = 0; i < startURLs.length; i++) {
            String startURL = startURLs[i];
            database.queue(startURL, 0);
        }
        for (IHttpCrawlerEventListener listener : listeners) {
            listener.crawlerStarted(this);
        }
        execute(database, progress, suite);
    }
	
    private void execute(
            ICrawlURLDatabase database, JobProgress progress, JobSuite suite) {

        //TODO print initialization information
        LOG.info("RobotsTxt support " + 
                (httpConfig.isIgnoreRobotsTxt() ? "disabled." : "enabled"));
        
        initializeHTTPClient();
        

        //TODO consider offering threading here?
        processURLs(database, progress, suite);

        database.queueCache();
        processURLs(database, progress, suite);
        ICommitter committer = httpConfig.getCommitter();
        if (committer != null) {
            LOG.info("Crawler \"" + getId() + "\" " 
                    + (stopped ? "stopping" : "finishing")
                    + ": committing documents.");
            committer.commit();
        }
        
        connectionManager.closeIdleConnections(10);
        connectionManager.deleteClosedConnections();

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
    
    
    private void processURLs(
    		final ICrawlURLDatabase database,
    		final JobProgress progress, 
    		final JobSuite suite) {
        
        int numThreads = httpConfig.getNumThreads();
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
                            CrawlURL queuedURL = database.next();
                            if (queuedURL != null) {
                                StopWatch watch = new StopWatch();
                                watch.start();
                                processNextQueuedURL(queuedURL, database);
                                setProgress(progress, database);
                                watch.stop();
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug(watch.toString() + " to process: " 
                                            + queuedURL.getUrl());
                                }
                            } else {
                                if (database.getActiveCount() == 0
                                        && database.isQueueEmpty()) {
                                    break;
                                }
                                Sleeper.sleepMillis(10);
                            }
                        } catch (Exception e) {
                            LOG.fatal("An error occured that could compromise "
                                    + "the stability of the crawler component. "
                                    + "Stopping excution to avoid further "
                                    + "issues...", e);
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
        if (!httpConfig.isKeepDownloads()) {
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
    

    private void setProgress(JobProgress progress, ICrawlURLDatabase db) {
        int queued = db.getQueueSize();
        int processed = db.getProcessedCount();
        int total = queued + processed;
        if (total == 0) {
            progress.setProgress(progress.getJobContext().getProgressMaximum());
        } else {
            int percent = (int) Math.floor(
                    ((double) processed / (double) total) * (double) 100);
            progress.setProgress(percent);
        }
        progress.setNote(
                NumberFormat.getIntegerInstance().format(processed)
                + " urls processed out of "
                + NumberFormat.getIntegerInstance().format(total));
    }

    private void processNextQueuedURL(
            CrawlURL crawlURL, ICrawlURLDatabase database) {
        String url = crawlURL.getUrl();
        int urlDepth = crawlURL.getDepth();

        String baseFilename = gelCrawlerDownloadDir().getAbsolutePath() 
                + SystemUtils.FILE_SEPARATOR +  PathUtils.urlToPath(url);
        File rawFile = new File(baseFilename + ".raw"); 
        File outputFile = new File(baseFilename + ".txt");
        HttpDocument doc = new HttpDocument(url, rawFile);
        
        try {
	        if (!isUrlDepthValid(url, urlDepth)) {
	            crawlURL.setStatus(CrawlStatus.TOO_DEEP);
	            return;
	        }
	        if (LOG.isDebugEnabled()) {
	            LOG.debug("Processing URL: " + url);
	        }

            //--- URL Filters --------------------------------------------------
            if (isURLRejected(url, httpConfig.getURLFilters(), null)) {
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return;
            }

            //--- Robots.txt Filters -------------------------------------------
            RobotsTxt robotsTxt = null;
            if (!httpConfig.isIgnoreRobotsTxt()) {
                robotsTxt = httpConfig.getRobotsTxtProvider().getRobotsTxt(
                        httpClient, url);
                if (isURLRejected(url, robotsTxt.getFilters(), robotsTxt)) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return;
                }
            }

            //--- Wait for delay to expire -------------------------------------
            httpConfig.getDelayResolver().delay(robotsTxt, url);
            
            //--- HTTP Headers Fetcher and Filters -----------------------------
            IHttpHeadersFetcher hdFetcher = httpConfig.getHttpHeadersFetcher();
            if (hdFetcher != null) {
                Metadata metadata = hdFetcher.fetchHTTPHeaders(httpClient, url);
                if (metadata == null) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return;
                }
                doc.getMetadata().addProperty(metadata.getProperties());
                enhanceHTTPHeaders(doc.getMetadata());
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentHeadersFetched(
                            this, url, hdFetcher, doc.getMetadata());
                }
                
                //--- HTTP Headers Filters -------------------------------------
                if (isHeadersRejected(url, doc.getMetadata(), 
                        httpConfig.getHttpHeadersFilters())) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return;
                }
                
                //--- HTTP Headers Checksum ------------------------------------
                //TODO only if an INCREMENTAL run... else skip.
                if (isHeadersChecksumRejected(
                        database, crawlURL, doc.getMetadata())) {
                    crawlURL.setStatus(CrawlStatus.UNMODIFIED);
                    return;
                }
            }
            
            //--- Document Fetcher ---------------------------------------------            
            crawlURL.setStatus(fetchDocument(doc));
	        if (crawlURL.getStatus() != CrawlStatus.OK) {
	            return;
	        }

            //--- URL Extractor ------------------------------------------------            
	        extractUrls(doc);

            //--- Store Next URLs to process -----------------------------------
            storeURLs(doc, database, urlDepth);


            //--- Apply Headers filters if not already -------------------------
            if (hdFetcher == null) {
                enhanceHTTPHeaders(doc.getMetadata());
                //--- HTTP Headers Filters -------------------------------------
                if (isHeadersRejected(url, doc.getMetadata(), 
                        httpConfig.getHttpHeadersFilters())) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return;
                }
                
                //--- HTTP Headers Checksum ------------------------------------
                //TODO only if an INCREMENTAL run... else skip.
                if (isHeadersChecksumRejected(
                        database, crawlURL, doc.getMetadata())) {
                    crawlURL.setStatus(CrawlStatus.UNMODIFIED);
                    return;
                }
            }
            
            //--- Document Filters ---------------------------------------------
            if (isDocumentRejected(doc, httpConfig.getHttpDocumentfilters())) {
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return;
            }

            //--- Document Pre-Processing --------------------------------------
            if (httpConfig.getHttpPreProcessors() != null) {
                for (IHttpDocumentProcessor preProc :
                        httpConfig.getHttpPreProcessors()) {
                    preProc.processDocument(httpClient, doc);
                    for (IHttpCrawlerEventListener listener : listeners) {
                        listener.documentPreProcessed(this, doc, preProc);
                    }
                }
            }
            
            //--- IMPORT Module ------------------------------------------------
            if (!importDocument(doc, outputFile)) {
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return;
            }
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentImported(this, doc);
            }
            
            //--- HTTP Document Checksum ---------------------------------------
            //TODO only if an INCREMENTAL run... else skip.
            if (isDocumentChecksumRejected(database, crawlURL, doc)) {
                crawlURL.setStatus(CrawlStatus.UNMODIFIED);
                return;
            }
            
            //--- Document Post-Processing -------------------------------------
            if (httpConfig.getHttpPostProcessors() != null) {
                for (IHttpDocumentProcessor postProc :
                        httpConfig.getHttpPostProcessors()) {
                    postProc.processDocument(httpClient, doc);
                    for (IHttpCrawlerEventListener listener : listeners) {
                        listener.documentPostProcessed(this, doc, postProc);
                    }
                }            
            }
            
	        //TODO Tranform output here to format of choice? 
	        // Rather call an open interface for that.. providing default
	        // one, but people could skip writing to file altogether???

            //--- Document Commit ----------------------------------------------
	        ICommitter committer = httpConfig.getCommitter();
            if (committer != null) {
                committer.queueAdd(url, outputFile, doc.getMetadata());
            }

            
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentCrawled(this, doc);
            }
            
	        //Sleep
//            sleep(robotsTxt, timerStart, url);
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
	        ICommitter committer = httpConfig.getCommitter();
            if (database.isVanished(crawlURL)) {
                crawlURL.setStatus(CrawlStatus.DELETED);
                if (committer != null) {
                    committer.queueRemove(url, outputFile, doc.getMetadata());
                }
            }
	    	
            //--- Mark URL as Processed ----------------------------------------
            database.processed(crawlURL);
            if (LOG.isInfoEnabled()) {
                LOG.info(StringUtils.leftPad(
                        crawlURL.getStatus().toString(), 10) + " > " + url);
            }
            
            //--- Delete Local File Download -----------------------------------
            if (!httpConfig.isKeepDownloads()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Deleting" + doc.getLocalFile());
                }
                FileUtils.deleteQuietly(doc.getLocalFile());
            }
	    }
	}

    private void storeURLs(
            HttpDocument doc, ICrawlURLDatabase database, int urlDepth) {
        Collection<String> urls = doc.getMetadata().getDocumentUrls();
        for (String urlToProcess : urls) {
            if (database.isActive(urlToProcess)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Already being processed: " + urlToProcess);
                }
            } else if (database.isQueued(urlToProcess)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Already queued: " + urlToProcess);
                }
            } else if (database.isProcessed(urlToProcess)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Already processed: " + urlToProcess);
                }
            } else {
                database.queue(urlToProcess, urlDepth + 1);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Queued for processing: " + urlToProcess);
                }
            }
        }
    }
        
    private boolean isURLRejected(
            String url, IURLFilter[] filters, RobotsTxt robotsTxt) {
        if (filters == null) {
            return false;
        }
        String type = "";
        if (robotsTxt != null) {
            type = " (robots.txt)";
        }
        for (IURLFilter filter : filters) {
            if (filter.acceptURL(url)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document URL" + type + ". URL=" + url
                            + " Filter=" + filter);
                }
            } else {
                for (IHttpCrawlerEventListener listener : listeners) {
                    if (robotsTxt != null) {
                        listener.documentRobotsTxtRejected(
                                this, url, filter, robotsTxt);
                    } else {
                        listener.documentURLRejected(this, url, filter);
                    }
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("REJECTED document URL" + type + ". URL=" 
                            + url + " Filter=" + filter);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isHeadersRejected(
            String url, Metadata headers, IHttpHeadersFilter[] filters) {
        if (filters == null) {
            return false;
        }
        for (IHttpHeadersFilter filter : filters) {
            if (filter.acceptHeaders(url, headers)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document http headers. URL=" + url
                            + " Filter=" + filter);
                }
            } else {
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentHeadersRejected(
                            this, url, filter, headers);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("REJECTED document http headers.  URL=" 
                            + url + " Filter=" + filter);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isHeadersChecksumRejected(
            ICrawlURLDatabase database, CrawlURL crawlURL, Metadata headers) {
    	IHttpHeadersChecksummer check = httpConfig.getHttpHeadersChecksummer();
        if (check == null) {
            return false;
        }
        String newHeadChecksum = check.createChecksum(headers);
        crawlURL.setHeadChecksum(newHeadChecksum);
        String oldHeadChecksum = null;
        CrawlURL cachedURL = database.getCached(crawlURL.getUrl());
        if (cachedURL != null) {
        	oldHeadChecksum = cachedURL.getHeadChecksum();
        } else {
            LOG.debug("ACCEPTED document headers checkum (new): URL="
            		+ crawlURL.getUrl());
        	return false;
        }
        if (StringUtils.isNotBlank(newHeadChecksum) 
        		&& ObjectUtils.equals(newHeadChecksum, oldHeadChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document headers checkum (unmodified): URL="
                		+ crawlURL.getUrl());
            }
            return true;
        }
        LOG.debug("ACCEPTED document headers checkum (modified): URL=" 
                + crawlURL.getUrl());
        return false;
    }

    private boolean isDocumentChecksumRejected(
            ICrawlURLDatabase database, CrawlURL crawlURL, 
            HttpDocument document) {
    	IHttpDocumentChecksummer check = 
    			httpConfig.getHttpDocumentChecksummer();
        if (check == null) {
            return false;
        }
        String newDocChecksum = check.createChecksum(document);
        crawlURL.setDocChecksum(newDocChecksum);
        String oldDocChecksum = null;
        CrawlURL cachedURL = database.getCached(crawlURL.getUrl());
        if (cachedURL != null) {
        	oldDocChecksum = cachedURL.getDocChecksum();
        } else {
            LOG.debug("ACCEPTED document checkum (new): URL=" 
                    + crawlURL.getUrl());
        	return false;
        }
        if (StringUtils.isNotBlank(newDocChecksum) 
        		&& ObjectUtils.equals(newDocChecksum, oldDocChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document checkum (unmodified): URL=" 
                        + crawlURL.getUrl());
            }
            return true;
        }
        LOG.debug("ACCEPTED document checkum (modified): URL=" 
                + crawlURL.getUrl());
        return false;
    }

    
    private boolean isDocumentRejected(
            HttpDocument document, IHttpDocumentFilter[] filters) {
        if (filters == null) {
            return false;
        }
        for (IHttpDocumentFilter filter : filters) {
            if (filter.acceptDocument(document)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document. URL=" + document.getUrl()
                            + " Filter=" + filter);
                }
            } else {
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentRejected(this, document, filter);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("REJECTED document.  URL=" + document.getUrl()
                            + " Filter=" + filter);
                }
                return true;
            }
        }
        return false;
    }
    
	private boolean isUrlDepthValid(String url, int urlDepth) {
	    if (httpConfig.getDepth() != -1 && urlDepth > httpConfig.getDepth()) {
	        if (LOG.isDebugEnabled()) {
	            LOG.debug("URL too deep to process (" + urlDepth + "): " + url);
	        }
	        return false;
	    }
	    return true;
	}
	
	private void initializeHTTPClient() {
        //TODO set HTTPClient user-agent from config before calling init..

        httpClient = new HttpClient(connectionManager);
        httpConfig.getHttpClientInitializer().initializeHTTPClient(httpClient);
	}

    private void enhanceHTTPHeaders(Metadata metadata) {
        String contentType = metadata.getPropertyValue("Content-Type");
        if (contentType != null) {
            String mimeType = contentType.replaceFirst("(.*?)(;.*)", "$1");
            String charset = contentType.replaceFirst("(.*?)(; )(.*)", "$3");
            charset = charset.replaceFirst("(charset=)(.*)", "$2");
            metadata.addPropertyValue(
                    HttpMetadata.DOC_MIMETYPE, mimeType);
            metadata.addPropertyValue(
                    HttpMetadata.DOC_CHARSET, charset);
        }
    }
    
    private CrawlStatus fetchDocument(HttpDocument doc) {
        //TODO for now we assume the document is downloadable.
		// download as file
        CrawlStatus status = httpConfig.getHttpDocumentFetcher().fetchDocument(
                httpClient, doc);
        if (status == CrawlStatus.OK) {
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentFetched(
                        this, doc, httpConfig.getHttpDocumentFetcher());
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("SAVED DOC: " + doc.getLocalFile().toURI()
                      + " [mime type: " + doc.getMetadata().getContentType() + "]");
          //TODO find better way: http://www.rgagnon.com/javadetails/java-0487.html
          //new MimetypesFileTypeMap().getContentType(documentFile) + "]");
            }
        }
        return status;
    }

    /**
     * Convert binary/formatted text to normalised text.
     * @param doc
     * @throws IOException
     */
    private boolean importDocument(
            HttpDocument doc, File output) throws IOException {
        Importer importer = new Importer(importConfig);
        FileUtil.createDirsForFile(output);
        if (importer.importDocument(
                doc.getLocalFile(),
                doc.getMetadata().getContentType(),
                output,
                doc.getMetadata(),
                doc.getUrl())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("ACCEPTED document import. URL=" + doc.getUrl());
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("REJECTED document import.  URL=" + doc.getUrl());
        }
        return false;
    }

//    private void commitDocument(HttpDocument doc) throws IOException {
//        //TODO this is just for now:
//        doc.getMetadata().writeToFile(new File(
//                doc.getLocalFile().getAbsolutePath() + "-meta.txt"));
//    }
    
    /**
     * Extract URLs before sending to importer (because the importer may
     * strip some "valid" urls in producing content-centric material.
     * Plus, any additional urls could be added to Metadata and they will
     * be considered.
     * @param doc
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void extractUrls(HttpDocument doc) throws IOException,
            IOException {
        FileReader reader = new FileReader(doc.getLocalFile());
		IURLExtractor urlExtractor = 
		        httpConfig.getUrlExtractor();
		Set<String> urls = urlExtractor.extractURLs(
		        reader, doc.getUrl(), doc.getMetadata().getContentType());
		reader.close();
        if (urls != null) {
            doc.getMetadata().addPropertyValue(
                    HttpMetadata.REFERNCED_URLS, urls.toArray(
                    		ArrayUtils.EMPTY_STRING_ARRAY));
        }
        for (IHttpCrawlerEventListener listener : listeners) {
            listener.documentURLsExtracted(this, doc);
        }
    }

    @Override
    public void stop(IJobStatus progress, JobSuite suite) {
        stopped = true;
        LOG.info("Stopping the crawler \"" + progress.getJobId() +  "\".");
    }
    private File gelBaseDownloadDir() {
        return new File(
                httpConfig.getWorkDir().getAbsolutePath() + "/downloads");
    }
    private File gelCrawlerDownloadDir() {
        return new File(gelBaseDownloadDir() + "/" + httpConfig.getId());
    }
}
