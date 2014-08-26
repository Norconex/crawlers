/**
 * 
 */
package com.norconex.collector.http.ref.pipe;

import org.apache.http.client.HttpClient;

import com.norconex.collector.core.ref.store.IReferenceStore;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.ref.HttpDocReference;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.ISitemapResolver;

/**
 * @author Pascal Essiembre
 *
 */
public class ReferencePipelineContext {

    private final HttpCrawler crawler;
    private final IReferenceStore refStore;
    private final HttpDocReference reference;
    private final RobotsTxt robotsTxt;

//    private RobotsMeta robotsMeta;
    
    public ReferencePipelineContext(
            HttpCrawler crawler, IReferenceStore refStore, 
            HttpDocReference reference /*, RobotsTxt robotsTxt*/) {
        this.crawler = crawler;
        this.refStore = refStore;
        this.reference = reference;
        this.robotsTxt = getConfig().getRobotsTxtProvider().getRobotsTxt(
                getHttpClient(), getReference().getReference(), 
                getConfig().getUserAgent());
    }

    public HttpCrawler getCrawler() {
        return crawler;
    }

    public HttpCrawlerConfig getConfig() {
        return crawler.getCrawlerConfig();
    }
    
    public HttpDocReference getReference() {
        return reference;
    }

    public HttpClient getHttpClient() {
        return crawler.getHttpClient();
    }

    public IReferenceStore getReferenceStore() {
        return refStore;
    }

    public ISitemapResolver getSitemapResolver() {
        return crawler.getSitemapResolver();
    }
    
    public RobotsTxt getRobotsTxt() {
        return robotsTxt;
    }

//    public RobotsMeta getRobotsMeta() {
//        return robotsMeta;
//    }
//    /**
//     * @param robotsMeta the robotsMeta to set
//     */
//    public void setRobotsMeta(RobotsMeta robotsMeta) {
//        this.robotsMeta = robotsMeta;
//    }
}
