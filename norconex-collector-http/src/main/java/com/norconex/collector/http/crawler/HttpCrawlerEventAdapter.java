package com.norconex.collector.http.crawler;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IHttpDocumentFetcher;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.handler.IHttpHeadersFetcher;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.meta.Metadata;

/**
 * Adapter for {@link IHttpCrawlerEventListener}.  None of the method
 * implementations do nothing. Subclasing of this class
 * is favoured over direct implementation of {@link IHttpCrawlerEventListener}.
 * @author Pascal Essiembre
 */
public class HttpCrawlerEventAdapter implements IHttpCrawlerEventListener{
    
    public void crawlerStarted(HttpCrawler crawler) {}
    public void documentRobotsTxtRejected(HttpCrawler crawler,
            String url, IURLFilter filter, RobotsTxt robotsTxt) {}
    public void documentURLRejected(HttpCrawler crawler,String url, IURLFilter filter) {}
    public void documentHeadersFetched(HttpCrawler crawler,
            String url, IHttpHeadersFetcher headersFetcher, Metadata headers) {}
    public void documentHeadersRejected(HttpCrawler crawler,
            String url, IHttpHeadersFilter filter, Metadata headers) {}
    public void documentFetched(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFetcher fetcher) {}
    public void documentURLsExtracted(HttpCrawler crawler, HttpDocument document) {}
    public void documentRejected(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFilter filter) {}
    public void documentPreProcessed(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentProcessor preProcessor) {}
    public void documentImported(HttpCrawler crawler,HttpDocument document) {}
    public void documentPostProcessed(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentProcessor postProcessor) {}
    public void crawlerFinished(HttpCrawler crawler) {}
    public void documentCrawled(HttpCrawler crawler, HttpDocument document) {}
}
