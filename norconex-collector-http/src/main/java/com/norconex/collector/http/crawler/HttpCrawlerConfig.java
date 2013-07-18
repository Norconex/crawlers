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
import java.io.Serializable;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.checksum.IHttpDocumentChecksummer;
import com.norconex.collector.http.checksum.IHttpHeadersChecksummer;
import com.norconex.collector.http.checksum.impl.DefaultHttpDocumentChecksummer;
import com.norconex.collector.http.checksum.impl.DefaultHttpHeadersChecksummer;
import com.norconex.collector.http.client.IHttpClientInitializer;
import com.norconex.collector.http.client.impl.DefaultHttpClientInitializer;
import com.norconex.collector.http.db.ICrawlURLDatabaseFactory;
import com.norconex.collector.http.db.impl.DefaultCrawlURLDatabaseFactory;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.delay.impl.DefaultDelayResolver;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.fetch.impl.DefaultDocumentFetcher;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.robot.IRobotsMetaProvider;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.impl.DefaultRobotsMetaProvider;
import com.norconex.collector.http.robot.impl.DefaultRobotsTxtProvider;
import com.norconex.collector.http.sitemap.ISitemapsResolver;
import com.norconex.collector.http.sitemap.impl.DefaultSitemapResolver;
import com.norconex.collector.http.url.IURLExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.impl.DefaultURLExtractor;
import com.norconex.committer.ICommitter;
import com.norconex.importer.ImporterConfig;


public class HttpCrawlerConfig implements Cloneable, Serializable {

    private static final long serialVersionUID = -3350877963428801802L;
    private String id;
    private int maxDepth = -1;
    private File workDir = new File("./work");
    private String[] startURLs;
    private int numThreads = 2;
    private int maxURLs = -1;
    
    private boolean ignoreRobotsTxt;
    private boolean ignoreRobotsMeta;
    private boolean ignoreSitemap;
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
    private IRobotsMetaProvider robotsMetaProvider =
            new DefaultRobotsMetaProvider();
    private ISitemapsResolver sitemapResolver =
            new DefaultSitemapResolver();

    private ICrawlURLDatabaseFactory crawlURLDatabaseFactory =
            new DefaultCrawlURLDatabaseFactory();
    
    private ImporterConfig importerConfig = new ImporterConfig();

    private IHttpDocumentFilter[] documentfilters;
	
    private IURLFilter[] urlFilters;	

    private IHttpHeadersFilter[] httpHeadersFilters;
    private IHttpHeadersChecksummer httpHeadersChecksummer = 
    		new DefaultHttpHeadersChecksummer();
	
    private IHttpDocumentProcessor[] preImportProcessors;
    private IHttpDocumentProcessor[] postImportProcessors;

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
        this.startURLs = ArrayUtils.clone(startURLs);
    }
    public void setMaxDepth(int depth) {
        this.maxDepth = depth;
    }
    public int getMaxDepth() {
        return maxDepth;
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
    public int getMaxURLs() {
        return maxURLs;
    }
    public void setMaxURLs(int maxURLs) {
        this.maxURLs = maxURLs;
    }
    public IHttpDocumentFilter[] getHttpDocumentfilters() {
        return documentfilters;
    }
    public void setHttpDocumentfilters(IHttpDocumentFilter[] documentfilters) {
        this.documentfilters = ArrayUtils.clone(documentfilters);
    }
    public IURLFilter[] getURLFilters() {
        return urlFilters;
    }
    public void setURLFilters(IURLFilter[] urlFilters) {
        this.urlFilters = ArrayUtils.clone(urlFilters);
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
        this.crawlerListeners = ArrayUtils.clone(crawlerListeners);
    }
    public IHttpHeadersFilter[] getHttpHeadersFilters() {
        return httpHeadersFilters;
    }
    public void setHttpHeadersFilters(IHttpHeadersFilter[] httpHeadersFilters) {
        this.httpHeadersFilters = ArrayUtils.clone(httpHeadersFilters);
    }

    public IHttpDocumentProcessor[] getPreImportProcessors() {
        return preImportProcessors;
    }
    public void setPreImportProcessors(
    		IHttpDocumentProcessor[] httpPreProcessors) {
        this.preImportProcessors = ArrayUtils.clone(httpPreProcessors);
    }
    public IHttpDocumentProcessor[] getPostImportProcessors() {
        return postImportProcessors;
    }
    public void setPostImportProcessors(
    		IHttpDocumentProcessor[] httpPostProcessors) {
        this.postImportProcessors = ArrayUtils.clone(httpPostProcessors);
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
	public boolean isIgnoreRobotsMeta() {
        return ignoreRobotsMeta;
    }
    public void setIgnoreRobotsMeta(boolean ignoreRobotsMeta) {
        this.ignoreRobotsMeta = ignoreRobotsMeta;
    }
    public IRobotsMetaProvider getRobotsMetaProvider() {
        return robotsMetaProvider;
    }
    public void setRobotsMetaProvider(IRobotsMetaProvider robotsMetaProvider) {
        this.robotsMetaProvider = robotsMetaProvider;
    }
    public boolean isIgnoreSitemap() {
        return ignoreSitemap;
    }
    public void setIgnoreSitemap(boolean ignoreSitemap) {
        this.ignoreSitemap = ignoreSitemap;
    }
    public ISitemapsResolver getSitemapResolver() {
        return sitemapResolver;
    }
    public void setSitemapResolver(ISitemapsResolver sitemapResolver) {
        this.sitemapResolver = sitemapResolver;
    }
    @Override
    protected Object clone() throws CloneNotSupportedException {
        try {
            return BeanUtils.cloneBean(this);
        } catch (Exception e) {
            throw new HttpCollectorException(e);
        }
    }
}
