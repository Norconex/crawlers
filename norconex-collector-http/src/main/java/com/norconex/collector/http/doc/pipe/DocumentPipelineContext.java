/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.apache.commons.lang3.CharEncoding;
import org.apache.http.client.HttpClient;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.doccrawl.store.IDocCrawlStore;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doccrawl.HttpDocCrawl;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.importer.doc.Content;
import com.norconex.importer.response.ImporterResponse;

/**
 * @author Pascal Essiembre
 *
 */
public class DocumentPipelineContext {

    private final HttpCrawler crawler;
    private final HttpDocument doc;
    private final IDocCrawlStore docCrawlStore;
    private final HttpDocCrawl docCrawl;
    private RobotsMeta robotsMeta;
    private ImporterResponse importerResponse;
    
    public DocumentPipelineContext(
            HttpCrawler crawler, IDocCrawlStore docCrawlStore, 
            HttpDocument doc, HttpDocCrawl docCrawl /*, RobotsTxt robotsTxt*/) {
        this.crawler = crawler;
        this.docCrawlStore = docCrawlStore;
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
    public HttpDocCrawl getDocCrawl() {
        return docCrawl;
    }
    
    public HttpClient getHttpClient() {
        return crawler.getHttpClient();
    }

    public HttpDocument getDocument() {
        return doc;
    }

    public IHttpHeadersFetcher getHttpHeadersFetcher() {
        return getConfig().getHttpHeadersFetcher();
    }

    public IDocCrawlStore getReferenceStore() {
        return docCrawlStore;
    }

    public ISitemapResolver getSitemapResolver() {
        return crawler.getSitemapResolver();
    }
    
    public HttpMetadata getMetadata() {
        return doc.getMetadata();
    }

    public RobotsMeta getRobotsMeta() {
        return robotsMeta;
    }
    /**
     * @param robotsMeta the robotsMeta to set
     */
    public void setRobotsMeta(RobotsMeta robotsMeta) {
        this.robotsMeta = robotsMeta;
    }

    public boolean isHttpHeadFetchEnabled() {
        return getConfig().getHttpHeadersFetcher() != null;
    }
    
    public ImporterResponse getImporterResponse() {
        return importerResponse;
    }

    public void setImporterResponse(ImporterResponse importerResponse) {
        this.importerResponse = importerResponse;
    }

    public Content getContent() {
        return getDocument().getContent();
    }

    public Reader getContentReader() {
        try {
            return new InputStreamReader(
                    getDocument().getContent().getInputStream(), 
                    CharEncoding.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new CollectorException(e);
        }
    }

}
