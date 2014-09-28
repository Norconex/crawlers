/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import org.apache.http.client.HttpClient;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;

/**
 * @author Pascal Essiembre
 *
 */
public class PostImportPipelineContext {

    private final HttpCrawler crawler;
    private final HttpDocument doc;
    private final ICrawlDataStore crawlDataStore;
    private final HttpCrawlData docCrawl;
//    private RobotsMeta robotsMeta;
//    private ImporterResponse importerResponse;
    
    public PostImportPipelineContext(
            HttpCrawler crawler, ICrawlDataStore crawlDataStore,
            HttpDocument doc, HttpCrawlData docCrawl) {
        this.crawler = crawler;
        this.crawlDataStore = crawlDataStore;
        this.doc = doc;
        this.docCrawl = docCrawl;
    }

    public HttpCrawler getCrawler() {
        return crawler;
    }

    public HttpCrawlerConfig getConfig() {
        return crawler.getCrawlerConfig();
    }
    
    /**
     * @return the docCrawl
     */
    public HttpCrawlData getDocCrawl() {
        return docCrawl;
    }
    
    public HttpClient getHttpClient() {
        return crawler.getHttpClient();
    }

    public HttpDocument getDocument() {
        return doc;
    }
    
    /**
     * @return the crawlDataStore
     */
    public ICrawlDataStore getDocCrawlStore() {
        return crawlDataStore;
    }
    
//
//    public IHttpHeadersFetcher getHttpHeadersFetcher() {
//        return getConfig().getHttpHeadersFetcher();
//    }
//
//    public ICrawlDataStore getReferenceStore() {
//        return crawlDataStore;
//    }
//
//    public ISitemapResolver getSitemapResolver() {
//        return crawler.getSitemapResolver();
//    }
//    
//    public HttpMetadata getMetadata() {
//        return doc.getMetadata();
//    }
//
//    public RobotsMeta getRobotsMeta() {
//        return robotsMeta;
//    }
//    /**
//     * @param robotsMeta the robotsMeta to set
//     */
//    public void setRobotsMeta(RobotsMeta robotsMeta) {
//        this.robotsMeta = robotsMeta;
//    }
//
//    public boolean isHttpHeadFetchEnabled() {
//        return getConfig().getHttpHeadersFetcher() != null;
//    }
//    
//    public ImporterResponse getImporterResponse() {
//        return importerResponse;
//    }
//
//    public void setImporterResponse(ImporterResponse importerResponse) {
//        this.importerResponse = importerResponse;
//    }
//
//    public Content getContent() {
//        return getDocument().getContent();
//    }
//
//    public Reader getContentReader() {
//        try {
//            return new InputStreamReader(
//                    getDocument().getContent().getInputStream(), 
//                    CharEncoding.UTF_8);
//        } catch (UnsupportedEncodingException e) {
//            throw new CollectorException(e);
//        }
//    }

}
