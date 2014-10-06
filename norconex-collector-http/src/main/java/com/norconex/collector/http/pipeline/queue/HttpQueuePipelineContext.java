/**
 * 
 */
package com.norconex.collector.http.pipeline.queue;

import org.apache.http.client.HttpClient;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.pipeline.BasePipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.ISitemapResolver;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpQueuePipelineContext extends BasePipelineContext {

    private final RobotsTxt robotsTxt;

    public HttpQueuePipelineContext(
            HttpCrawler crawler, ICrawlDataStore refStore, 
            HttpCrawlData crawlData) {
        super(crawler, refStore, crawlData);
        HttpCrawlerConfig config = crawler.getCrawlerConfig();
        if (!config.isIgnoreRobotsTxt()) {
            this.robotsTxt = config.getRobotsTxtProvider().getRobotsTxt(
                    getHttpClient(), getCrawlData().getReference(), 
                    config.getUserAgent());
        } else {
            this.robotsTxt = null;
        }
    }

    public HttpClient getHttpClient() {
        return getCrawler().getHttpClient();
    }

    public ISitemapResolver getSitemapResolver() {
        return getCrawler().getSitemapResolver();
    }
    
    public RobotsTxt getRobotsTxt() {
        return robotsTxt;
    }
    
    @Override
    public HttpCrawlerConfig getConfig() {
        return (HttpCrawlerConfig) super.getConfig();
    }
    
    @Override
    public HttpCrawlData getCrawlData() {
        return (HttpCrawlData) super.getCrawlData();
    }
    
    public HttpCrawler getCrawler() {
        return (HttpCrawler) super.getCrawler();
    };
}
