/**
 * 
 */
package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.util.PathUtils;

/**
 * @author Pascal Essiembre
 *
 */
public class BasicFeaturesTest extends AbstractHttpTest {

    /**
     * Constructor.
     */
    public BasicFeaturesTest() {
    }

    @Test
    public void testRedirect() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=redirect");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxDepth(0);
        collector.start(false);

        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        HttpDocument doc = docs.get(0);
        String ref = doc.getReference();

        List<String> urls = 
                doc.getMetadata().getStrings(HttpMetadata.COLLECTOR_URL);
        System.out.println("URLs:" + urls);
        assertListSize("URL", urls, 1);

        Assert.assertTrue("Invalid redirection URL: " + ref,
                ref.contains("/test/redirected/page.html?case=redirect"));

        List<String> inPageUrls = doc.getMetadata().getStrings(
                HttpMetadata.COLLECTOR_REFERNCED_URLS);
        assertListSize("referenced URLs", inPageUrls, 2);

        Assert.assertTrue("Invalid relative URL: " + inPageUrls.get(0),
                inPageUrls.get(0).matches(".*/test/redirected/page[12].html"));
        Assert.assertTrue("Invalid relative URL: " + inPageUrls.get(1),
                inPageUrls.get(1).matches(".*/test/redirected/page[12].html"));
    }
    
    @Test
    public void testBasicFeatures() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=basic&depth=0");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxDepth(10);
        collector.start(false);

        List<HttpDocument> docs = getCommitedDocuments(crawler);
        testDepth(docs);
        for (HttpDocument httpDocument : docs) {
            testValidMetadata(httpDocument);
        }
    }

    @Test
    public void testKeepDownload() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test/a$dir/blah?case=keepDownloads");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxDepth(0);
        crawler.getCrawlerConfig().setKeepDownloads(true);
        String url = crawler.getCrawlerConfig().getStartURLs()[0];
        collector.start(false);

        File downloadDir = 
                new File(crawler.getCrawlerConfig().getWorkDir(), "downloads");
        File downloadedFile = new File(downloadDir, PathUtils.urlToPath(url));
        
        String content = FileUtils.readFileToString(downloadedFile);
        Assert.assertTrue("Invalid or missing download file.",
                content.contains("<b>This</b> file <i>must</i> be saved as is, "
                        + "with this <span>formatting</span>"));
    }
    
    @Test
    public void testMaxURLs() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=basic&depth=0");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxURLs(15);
        collector.start(false);

        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("URLs", docs, 15);
    }
    
    @Test
    public void testUserAgent() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=userAgent");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxDepth(0);
        crawler.getCrawlerConfig().setUserAgent("Super Secret Agent");
        collector.start(false);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        HttpDocument doc = docs.get(0);
        Assert.assertTrue("Wrong or undetected User-Agent.", IOUtils.toString(
                doc.getContent()).contains("Super Secret Agent"));
    }
    
    private void testDepth(List<HttpDocument> docs) {
        // 0-depth + 10 others == 11 expected files
        Assert.assertEquals("Did not crawl the right depth.", 11, docs.size()); 
    }
    private void testValidMetadata(HttpDocument doc) {
        HttpMetadata meta = doc.getMetadata();

        //Test single value
        assertOneValue(meta, 
                HttpMetadata.HTTP_CONTENT_TYPE,
                HttpMetadata.COLLECTOR_CONTENT_TYPE,
                HttpMetadata.COLLECTOR_CONTENT_ENCODING);
        
        //Test actual values
        Assert.assertEquals("Bad HTTP content-type", "text/html; charset=UTF-8", 
                meta.getString(HttpMetadata.HTTP_CONTENT_TYPE));
        Assert.assertEquals("Bad Collection content-type.", "text/html", 
                meta.getString(HttpMetadata.COLLECTOR_CONTENT_TYPE));
        Assert.assertEquals("Bad char-encoding.", CharEncoding.UTF_8, 
                meta.getString(HttpMetadata.COLLECTOR_CONTENT_ENCODING));
    }
    private void assertListSize(String listName, List<?> list, int size) {
        Assert.assertEquals(
                "Wrong " + listName + " list size.", size, list.size());
    }
    private void assertOneValue(HttpMetadata meta, String... fields) {
        for (String field : fields) {
            Assert.assertEquals(field + " does not contain strickly 1 value.",
                    1, meta.getStrings(field).size());
        }
    }
}
