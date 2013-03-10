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
 * <p>Allows implementers to react to any crawler-specific events.</p>
 * <p><b>CAUTION:</b> Implementors should not implement this interface directly.
 * They are strongly advised to subclass the
 * {@link HttpCrawlerEventAdapter} class instead for forward compatibility.</p>
 * <p>Keep in mind that if defined as part of crawler defaults, 
 * a single instance of this listener will be shared amongst crawlers
 * (unless overwritten).</p>
 * @author Pascal Essiembre
 */
public interface IHttpCrawlerEventListener {
    
	//TODO add two new methods for headers and document checksum 
	//(i.e. modified vs not-modified.   Or rely on rejected flag?
	//TODO add documentDeleted
	
    void crawlerStarted(HttpCrawler crawler);
    void documentRobotsTxtRejected(HttpCrawler crawler,
            String url, IURLFilter filter, RobotsTxt robotsTxt);
    void documentURLRejected(
            HttpCrawler crawler, String url, IURLFilter filter);
    void documentHeadersFetched(HttpCrawler crawler,
            String url, IHttpHeadersFetcher headersFetcher, Metadata headers);
    void documentHeadersRejected(HttpCrawler crawler,
            String url, IHttpHeadersFilter filter, Metadata headers);
    void documentFetched(HttpCrawler crawler, 
            HttpDocument document, IHttpDocumentFetcher fetcher);
    void documentURLsExtracted(HttpCrawler crawler,HttpDocument document);
    void documentRejected(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFilter filter);
    void documentPreProcessed(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentProcessor preProcessor);
    void documentImported(HttpCrawler crawler,HttpDocument document);
    void documentPostProcessed(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentProcessor postProcessor);
    void documentCrawled(HttpCrawler crawler, HttpDocument document);
    void crawlerFinished(HttpCrawler crawler);
}
