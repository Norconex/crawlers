/**
 * 
 */
package com.norconex.collector.http.data.pipe;

import org.apache.http.client.HttpClient;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.ISitemapResolver;

/**
 * @author Pascal Essiembre
 *
 */
public class CrawlDataPipelineContext {

    private final HttpCrawler crawler;
    private final ICrawlDataStore crawlDataStore;
    private final HttpCrawlData docCrawl;
    private final RobotsTxt robotsTxt;

    public CrawlDataPipelineContext(
            HttpCrawler crawler, ICrawlDataStore refStore, 
            HttpCrawlData reference) {
        this.crawler = crawler;
        this.crawlDataStore = refStore;
        this.docCrawl = reference;
        if (!getConfig().isIgnoreRobotsTxt()) {
            this.robotsTxt = getConfig().getRobotsTxtProvider().getRobotsTxt(
                    getHttpClient(), getDocCrawl().getReference(), 
                    getConfig().getUserAgent());
        } else {
            this.robotsTxt = null;
        }
    }

    public HttpCrawler getCrawler() {
        return crawler;
    }

    public HttpCrawlerConfig getConfig() {
        return crawler.getCrawlerConfig();
    }
    
    public HttpCrawlData getDocCrawl() {
        return docCrawl;
    }

    public HttpClient getHttpClient() {
        return crawler.getHttpClient();
    }

    public ICrawlDataStore getDocCrawlStore() {
        return crawlDataStore;
    }

    public ISitemapResolver getSitemapResolver() {
        return crawler.getSitemapResolver();
    }
    
    public RobotsTxt getRobotsTxt() {
        return robotsTxt;
    }
}
