/* Copyright 2014-2016 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.collector.core.crawler.ICrawler;
import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.crawler.event.ICrawlerEventListener;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.url.impl.GenericLinkExtractor;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.file.IFileVisitor;

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
                HttpMetadata.COLLECTOR_REFERENCED_URLS);
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
//        String url = crawler.getCrawlerConfig().getStartURLs()[0];
        collector.start(false);

        File downloadDir = 
                new File(crawler.getCrawlerConfig().getWorkDir(), "downloads");
        final Mutable<File> downloadedFile = new MutableObject<>();
        FileUtil.visitAllFiles(downloadDir, new IFileVisitor() {
            @Override
            public void visit(File file) {
                if (downloadedFile.getValue() != null) {
                    return;
                }
                if (file.toString().contains("downloads")) {
                    downloadedFile.setValue(file);
                }
            }
        });
        String content = FileUtils.readFileToString(
                downloadedFile.getValue(), CharEncoding.UTF_8);
        Assert.assertTrue("Invalid or missing download file.",
                content.contains("<b>This</b> file <i>must</i> be saved as is, "
                        + "with this <span>formatting</span>"));
    }
    
    @Test
    public void testMaxURLs() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=basic&depth=0");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        crawler.getCrawlerConfig().setMaxDocuments(15);
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
        Assert.assertTrue("Wrong or undetected User-Agent.", 
                IOUtils.toString(doc.getContent(), CharEncoding.UTF_8).contains(
                        "Super Secret Agent"));
    }
    
    @Test
    public void testCanonicalLink() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=canonical");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        final MutableInt canCount = new MutableInt();
        crawler.getCrawlerConfig().setCrawlerListeners(
                new ICrawlerEventListener[] {new ICrawlerEventListener() {
            @Override
            public void crawlerEvent(ICrawler crawler, CrawlerEvent event) {
                if (HttpCrawlerEvent.REJECTED_CANONICAL.equals(
                        event.getEventType())) {
                    canCount.increment();
                }
            }
        }});
        collector.start(false);
        
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        Assert.assertEquals("Wrong number of canonical link rejection.",
                2, canCount.intValue());
    }

    
    @Test
    public void testSpecialURLs() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=specialURLs");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        collector.start(false);
        
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 4);
    }
    
    
    @Test
    public void testScriptTags() throws IOException {
        // Content of <script> tags must be stripped by GenericLinkExtractor
        // but src must be followed.
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=script");
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        GenericLinkExtractor le = new GenericLinkExtractor();
        le.addLinkTag("script", "src");
        crawler.getCrawlerConfig().setLinkExtractors(le);
        collector.start(false);

        List<HttpDocument> docs = getCommitedDocuments(crawler);

        assertListSize("document", docs, 2);

        for (HttpDocument doc : docs) {
            String content = IOUtils.toString(
                    doc.getContent(), CharEncoding.UTF_8);
            if (!doc.getReference().contains("script=true")) {
                // first page
                Assert.assertTrue("First page not crawled properly",
                        content.contains("View the source"));
                Assert.assertTrue("Did not strip inside of <script>",
                        !content.contains("THIS_MUST_BE_STRIPPED"));
            } else {
                // second page
                Assert.assertTrue("Script page not crawled properly",
                        content.contains("This must be crawled"));
            }
        }
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
