package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.Serializable;

import org.apache.commons.beanutils.BeanUtils;

import com.norconex.collector.http.db.ICrawlURLDatabaseFactory;
import com.norconex.collector.http.db.impl.DefaultCrawlURLDatabaseFactory;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IDelayResolver;
import com.norconex.collector.http.handler.IHttpClientInitializer;
import com.norconex.collector.http.handler.IHttpDocumentChecksummer;
import com.norconex.collector.http.handler.IHttpDocumentFetcher;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.handler.IHttpHeadersChecksummer;
import com.norconex.collector.http.handler.IHttpHeadersFetcher;
import com.norconex.collector.http.handler.IRobotsTxtProvider;
import com.norconex.collector.http.handler.IURLExtractor;
import com.norconex.collector.http.handler.IURLNormalizer;
import com.norconex.committer.ICommitter;
import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.handler.impl.DefaultDelayResolver;
import com.norconex.collector.http.handler.impl.DefaultDocumentFetcher;
import com.norconex.collector.http.handler.impl.DefaultHttpClientInitializer;
import com.norconex.collector.http.handler.impl.DefaultHttpDocumentChecksummer;
import com.norconex.collector.http.handler.impl.DefaultHttpHeadersChecksummer;
import com.norconex.collector.http.handler.impl.DefaultRobotsTxtProvider;
import com.norconex.collector.http.handler.impl.DefaultURLExtractor;
import com.norconex.importer.ImporterConfig;


public class HttpCrawlerConfig implements Cloneable, Serializable {

    private static final long serialVersionUID = -3350877963428801802L;
    private String id;
    private int depth = -1;
    private File workDir = new File("./work");
    private String[] startURLs;
    private int numThreads = 2;
    private boolean ignoreRobotsTxt;
    private boolean keepDownloads;
    private boolean deleteOrphans;

    private IURLNormalizer urlNormalizer;

    private IDelayResolver delayResolver = new DefaultDelayResolver();
    
    private IHttpClientInitializer httpClientInitializer =
            new DefaultHttpClientInitializer();

    private IHttpDocumentFetcher httpDocumentFetcher =
            new DefaultDocumentFetcher();

    private IHttpHeadersFetcher httpHeadersFetcher;

    private IURLExtractor urlExtractor = new DefaultURLExtractor();

    private IRobotsTxtProvider robotsTxtProvider =
            new DefaultRobotsTxtProvider();

    //private IRobotsMetaBuilder robotMetaBuilder;

    private ICrawlURLDatabaseFactory crawlURLDatabaseFactory =
            new DefaultCrawlURLDatabaseFactory();
    
    private ImporterConfig importerConfig = new ImporterConfig();

    private IHttpDocumentFilter[] documentfilters;
	
    private IURLFilter[] urlFilters;	

    private IHttpHeadersFilter[] httpHeadersFilters;
    private IHttpHeadersChecksummer httpHeadersChecksummer = 
    		new DefaultHttpHeadersChecksummer();
	
    private IHttpDocumentProcessor[] httpPreProcessors;
    private IHttpDocumentProcessor[] httpPostProcessors;

    private IHttpDocumentChecksummer httpDocumentChecksummer =
    		new DefaultHttpDocumentChecksummer();
	
    private IHttpCrawlerEventListener[] crawlerListeners;
    
    private ICommitter committer;
    
	public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String[] getStartURLs() {
        return startURLs;
    }
    public void setStartURLs(String[] startURLs) {
        this.startURLs = startURLs;
    }
    public void setDepth(int depth) {
        this.depth = depth;
    }
    public int getDepth() {
        return depth;
    }
    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }
    public File getWorkDir() {
        return workDir;
    }
    public int getNumThreads() {
        return numThreads;
    }
    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }
    public IHttpDocumentFilter[] getHttpDocumentfilters() {
        return documentfilters;
    }
    public void setHttpDocumentfilters(IHttpDocumentFilter[] documentfilters) {
        this.documentfilters = documentfilters;
    }
    public IURLFilter[] getURLFilters() {
        return urlFilters;
    }
    public void setURLFilters(IURLFilter[] urlFilters) {
        this.urlFilters = urlFilters;
    }
    public ImporterConfig getImporterConfig() {
        return importerConfig;
    }
    public void setImporterConfig(ImporterConfig importerConfig) {
        this.importerConfig = importerConfig;
    }
    
    public IHttpClientInitializer getHttpClientInitializer() {
        return httpClientInitializer;
    }
    public void setHttpClientInitializer(
            IHttpClientInitializer httpClientInitializer) {
        this.httpClientInitializer = httpClientInitializer;
    }
    public IHttpDocumentFetcher getHttpDocumentFetcher() {
        return httpDocumentFetcher;
    }
    public void setHttpDocumentFetcher(
    		IHttpDocumentFetcher httpDocumentFetcher) {
        this.httpDocumentFetcher = httpDocumentFetcher;
    }
    public IHttpHeadersFetcher getHttpHeadersFetcher() {
        return httpHeadersFetcher;
    }
    public void setHttpHeadersFetcher(IHttpHeadersFetcher httpHeadersFetcher) {
        this.httpHeadersFetcher = httpHeadersFetcher;
    }
    public IURLExtractor getUrlExtractor() {
        return urlExtractor;
    }
    public void setUrlExtractor(IURLExtractor urlExtractor) {
        this.urlExtractor = urlExtractor;
    }
    public IRobotsTxtProvider getRobotsTxtProvider() {
        return robotsTxtProvider;
    }
    public void setRobotsTxtProvider(IRobotsTxtProvider robotsTxtProvider) {
        this.robotsTxtProvider = robotsTxtProvider;
    }
    

    
//    public Object getDocumentStateDetectorStrategy() {return null;}
           // states: new, existing_modified, existing_unmodified, orphan, unreacheable

    public IURLNormalizer getUrlNormalizer() {
        return urlNormalizer;
    }
    public void setUrlNormalizer(IURLNormalizer urlNormalizer) {
        this.urlNormalizer = urlNormalizer;
    }
    public boolean isDeleteOrphans() {
        return deleteOrphans;
    }
    public void setDeleteOrphans(boolean deleteOrphans) {
        this.deleteOrphans = deleteOrphans;
    }
    public IDelayResolver getDelayResolver() {
        return delayResolver;
    }
    public void setDelayResolver(IDelayResolver delayResolver) {
        this.delayResolver = delayResolver;
    }
    public IHttpCrawlerEventListener[] getCrawlerListeners() {
        return crawlerListeners;
    }
    public void setCrawlerListeners(
            IHttpCrawlerEventListener[] crawlerListeners) {
        this.crawlerListeners = crawlerListeners;
    }
    public IHttpHeadersFilter[] getHttpHeadersFilters() {
        return httpHeadersFilters;
    }
    public void setHttpHeadersFilters(IHttpHeadersFilter[] httpHeadersFilters) {
        this.httpHeadersFilters = httpHeadersFilters;
    }

    public IHttpDocumentProcessor[] getHttpPreProcessors() {
        return httpPreProcessors;
    }
    public void setHttpPreProcessors(
    		IHttpDocumentProcessor[] httpPreProcessors) {
        this.httpPreProcessors = httpPreProcessors;
    }
    public IHttpDocumentProcessor[] getHttpPostProcessors() {
        return httpPostProcessors;
    }
    public void setHttpPostProcessors(
    		IHttpDocumentProcessor[] httpPostProcessors) {
        this.httpPostProcessors = httpPostProcessors;
    }
    public boolean isIgnoreRobotsTxt() {
        return ignoreRobotsTxt;
    }
    public void setIgnoreRobotsTxt(boolean ignoreRobotsTxt) {
        this.ignoreRobotsTxt = ignoreRobotsTxt;
    }
    
    public ICommitter getCommitter() {
        return committer;
    }
    public void setCommitter(ICommitter committer) {
        this.committer = committer;
    }
    public boolean isKeepDownloads() {
        return keepDownloads;
    }
    public void setKeepDownloads(boolean keepDownloads) {
        this.keepDownloads = keepDownloads;
    }
    public IHttpHeadersChecksummer getHttpHeadersChecksummer() {
		return httpHeadersChecksummer;
	}
	public void setHttpHeadersChecksummer(
			IHttpHeadersChecksummer httpHeadersChecksummer) {
		this.httpHeadersChecksummer = httpHeadersChecksummer;
	}
	public IHttpDocumentChecksummer getHttpDocumentChecksummer() {
		return httpDocumentChecksummer;
	}
	public void setHttpDocumentChecksummer(
			IHttpDocumentChecksummer httpDocumentChecksummer) {
		this.httpDocumentChecksummer = httpDocumentChecksummer;
	}
    public ICrawlURLDatabaseFactory getCrawlURLDatabaseFactory() {
        return crawlURLDatabaseFactory;
    }
    public void setCrawlURLDatabaseFactory(
            ICrawlURLDatabaseFactory crawlURLDatabaseFactory) {
        this.crawlURLDatabaseFactory = crawlURLDatabaseFactory;
    }
	
	@Override
    protected Object clone() {
        try {
            return (HttpCrawlerConfig) BeanUtils.cloneBean(this);
        } catch (Exception e) {
            throw new HttpCollectorException(e);
        }
//        return (HttpCrawlerConfig) SerializationUtils.clone(this);
    }
	
	
}
