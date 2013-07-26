package com.norconex.collector.http.crawler;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.map.Properties;

/**
 * Convenience class for firing crawler events.  The listeners defined
 * in the {@link HttpCrawler} configuration are the fired events targets.
 * @author Pascal Essiembre
 */
public final class HttpCrawlerEventFirer {

    private static final Logger LOG = 
            LogManager.getLogger(HttpCrawlerEventFirer.class);
    
    private HttpCrawlerEventFirer() {
        super();
    }

    public static void fireCrawlerStarted(HttpCrawler crawler) {
        debug("Crawler Started. Crawler=%s", crawler.getId());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.crawlerStarted(crawler);
        }
    }

    public static void fireDocumentRobotsTxtRejected(HttpCrawler crawler,
            String url, IURLFilter filter, RobotsTxt robotsTxt) {
        debug("Document rejected by robots.txt. URL=%s Filter=%s", url, filter);
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentRobotsTxtRejected(crawler, url, filter, robotsTxt);
        }
    }
    public static void fireDocumentRobotsMetaRejected(
            HttpCrawler crawler, String url, RobotsMeta robotsMeta) {
        debug("Document rejected by robots meta noindex rule. "
                + "URL=%s RobotsMeta=%s", url, robotsMeta);
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentRobotsMetaRejected(crawler, url, robotsMeta);
        }
    }
    
    public static void fireDocumentURLRejected(
            HttpCrawler crawler, String url, IURLFilter filter) {
        debug("Document rejected by URL filter. URL=%s Filter=%s", url, filter);
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentURLRejected(crawler, url, filter);
        }
    }

    public static void fireDocumentHeadersFetched(HttpCrawler crawler,
            String url, IHttpHeadersFetcher headersFetcher, 
            Properties headers) {
        debug("Document headers fetched. URL=%s", url);
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentHeadersFetched(
                    crawler, url, headersFetcher, headers);
        }
    }

    public static void fireDocumentHeadersRejected(HttpCrawler crawler,
            String url, IHttpHeadersFilter filter, Properties headers) {
        debug("Document rejected by headers filter. URL=%s Filter=%s",
                url, filter);
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentHeadersRejected(crawler, url, filter, headers);
        }
    }

    public static void fireDocumentFetched(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFetcher fetcher) {
        debug("Document fetched. URL=%s File=%s Content-Type=%s", 
                document.getUrl(), document.getLocalFile(), 
                document.getMetadata().getContentType());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentFetched(crawler, document, fetcher);
        }
    }

    public static void fireDocumentURLsExtracted(
            HttpCrawler crawler, HttpDocument document) {
        debug("Document URLs extracted. URL=%s", document.getUrl());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentURLsExtracted(crawler, document);
        }
    }

    public static void fireDocumentRejected(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFilter filter) {
        debug("Document rejected by document filter. URL=%s Filter=%s",
                document.getUrl(), filter);
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentRejected(crawler, document, filter);
        }
    }

    public static void fireDocumentPreProcessed(
            HttpCrawler crawler, HttpDocument document,
            IHttpDocumentProcessor preProcessor) {
        debug("Document pre-processed. URL=%s", document.getUrl());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentPreProcessed(crawler, document, preProcessor);
        }
    }

    public static void fireDocumentImported(
            HttpCrawler crawler,HttpDocument document) {
        debug("Document imported. URL=%s", document.getUrl());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentImported(crawler, document);
        }
    }
    
    public static void fireDocumentImportRejected(
            HttpCrawler crawler, HttpDocument document) {
        debug("Document rejected by Importer. URL=%s", document.getUrl());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentImportRejected(crawler, document);
        }
    }

    public static void fireDocumentPostProcessed(HttpCrawler crawler, 
            HttpDocument document, IHttpDocumentProcessor postProcessor) {
        debug("Document post-processed. URL=%s", document.getUrl());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentPostProcessed(crawler, document, postProcessor);
        }
    }

    public static void fireCrawlerFinished(HttpCrawler crawler) {
        debug("Crawler finished. Crawler=%s", crawler.getId());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.crawlerFinished(crawler);
        }
    }

    public static void fireDocumentCrawled(
            HttpCrawler crawler, HttpDocument document) {
        debug("Document crawled. URL=%s", document.getUrl());
        for (IHttpCrawlerEventListener listener : getListeners(crawler)) {
            listener.documentCrawled(crawler, document);
        }
    }
    
    private static IHttpCrawlerEventListener[] getListeners(
            HttpCrawler crawler) {
        IHttpCrawlerEventListener[] listeners = 
                crawler.getCrawlerConfig().getCrawlerListeners();
        if (listeners == null) {
            listeners = new IHttpCrawlerEventListener[] {};
        }
        return listeners;
    }
    
    private static void debug(String message, Object... values) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("EVENT: " + message, values));
        }
    }
}
