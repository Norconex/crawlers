/**
 * 
 */
package com.norconex.collector.http.pipeline.committer;

import org.apache.http.client.HttpClient;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.pipeline.DocumentPipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpCommitterPipelineContext extends DocumentPipelineContext {

    public HttpCommitterPipelineContext(
            HttpCrawler crawler, ICrawlDataStore crawlDataStore,
            HttpDocument doc, HttpCrawlData crawlData) {
        super(crawler, crawlDataStore, crawlData, doc);
    }

    public HttpCrawler getCrawler() {
        return (HttpCrawler) super.getCrawler();
    }

    public HttpCrawlerConfig getConfig() {
        return getCrawler().getCrawlerConfig();
    }
    
    public HttpCrawlData getCrawlData() {
        return (HttpCrawlData) super.getCrawlData();
    }
    
    public HttpClient getHttpClient() {
        return getCrawler().getHttpClient();
    }

    public HttpDocument getDocument() {
        return (HttpDocument) super.getDocument();
    }
}
