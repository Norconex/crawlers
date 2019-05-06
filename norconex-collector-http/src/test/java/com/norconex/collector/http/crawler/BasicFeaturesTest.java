/* Copyright 2014-2019 Norconex Inc.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.url.impl.GenericLinkExtractor;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.doc.ImporterMetadata;

/**
 * @author Pascal Essiembre
 *
 */
public class BasicFeaturesTest extends AbstractHttpTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(BasicFeaturesTest.class);

    /**
     * Constructor.
     */
    public BasicFeaturesTest() {
    }

    @Test
    public void testRedirect() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=redirect");
        getCrawlerConfig(collector, 0).setMaxDepth(0);
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        HttpDocument doc = docs.get(0);
        String ref = doc.getReference();

        List<String> urls =
                doc.getMetadata().getStrings(HttpMetadata.COLLECTOR_URL);
        LOG.debug("URLs:" + urls);
        assertListSize("URL", urls, 1);

        Assertions.assertTrue(
                ref.contains("/test/redirected/page.html?case=redirect"),
                "Invalid redirection URL: " + ref);

        List<String> inPageUrls = doc.getMetadata().getStrings(
                HttpMetadata.COLLECTOR_REFERENCED_URLS);
        assertListSize("referenced URLs", inPageUrls, 2);

        Assertions.assertTrue(
                inPageUrls.get(0).matches(".*/test/redirected/page[12].html"),
                "Invalid relative URL: " + inPageUrls.get(0));
        Assertions.assertTrue(
                inPageUrls.get(1).matches(".*/test/redirected/page[12].html"),
                "Invalid relative URL: " + inPageUrls.get(1));
    }

    @Test
    public void testMultiRedirects() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=multiRedirects");
        getCrawlerConfig(collector, 0).setMaxDepth(0);
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        HttpDocument doc = docs.get(0);
        String ref = doc.getReference();

        List<String> trail = doc.getMetadata().getStrings(
                HttpMetadata.COLLECTOR_REDIRECT_TRAIL);
        LOG.debug("Redirect source URLs:" + trail);
        assertListSize("URL", trail, 5);

        // Test the trail order:
        Assertions.assertFalse(trail.get(0).contains("count"));
        Assertions.assertTrue(trail.get(1).contains("count=1"));
        Assertions.assertTrue(trail.get(2).contains("count=2"));
        Assertions.assertTrue(trail.get(3).contains("count=3"));
        Assertions.assertTrue(trail.get(4).contains("count=4"));

        // Test final URL:
        Assertions.assertTrue( ref.contains("count=5"),
                "Invalid redirection URL: " + ref);
    }

    @Test
    public void testCanonicalRedirectLoop() throws IOException {

        HttpCollector collector = null;
        HttpCrawler crawler = null;
        List<HttpDocument> docs = null;
        HttpDocument doc = null;
        String content = null;

        //--- Starting with canonical ---
        collector = newHttpCollector1Crawler(
                "/test?case=canonRedirLoop&type=canonical");

        collector.start(false);

        crawler = (HttpCrawler) collector.getCrawlers().get(0);
        docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        doc = docs.get(0);
        try (CachedInputStream is = doc.getInputStream()) {
            content = IOUtils.toString(is, StandardCharsets.UTF_8);
            is.dispose();
        }
        assertTrue(
                content.contains("Canonical-redirect circular reference"),
                "Wrong content");
        assertTrue(
                doc.getReference().contains("&type=canonical"),
                "Wrong reference");

LOG.warn("FINAL REF: " + doc.getReference());
LOG.warn("FINAL TRAIL:" + doc.getMetadata().getStrings(
        HttpMetadata.COLLECTOR_REDIRECT_TRAIL));

        //-- Starting with redirect ---
        collector = newHttpCollector1Crawler(
                "/test?case=canonRedirLoop&type=redirect");
        collector.start(false);

        crawler = (HttpCrawler) collector.getCrawlers().get(0);
        docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        doc = docs.get(0);
        try (CachedInputStream is = doc.getInputStream()) {
            content = IOUtils.toString(is, StandardCharsets.UTF_8);
            is.dispose();
        }
        assertTrue(
                content.contains("Canonical-redirect circular reference"),
                "Wrong content");
        assertTrue(
                doc.getReference().contains("&type=canonical"),
                "Wrong reference");

        LOG.warn("FINAL REF: " + doc.getReference());
        LOG.warn("FINAL TRAIL:" + doc.getMetadata().getStrings(
        HttpMetadata.COLLECTOR_REDIRECT_TRAIL));
    }

    @Test
    public void testBasicFeatures() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=basic&depth=0");
        getCrawlerConfig(collector, 0).setMaxDepth(10);
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
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
        getCrawlerConfig(collector, 0).setMaxDepth(0);
        getCrawlerConfig(collector, 0).setKeepDownloads(true);
//        String url = crawler.getCrawlerConfig().getStartURLs()[0];
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        File downloadDir = crawler.getDownloadDir().toFile();

        final Mutable<File> downloadedFile = new MutableObject<>();
        FileUtil.visitAllFiles(downloadDir, file -> {
            if (downloadedFile.getValue() != null) {
                return;
            }
            if (file.toString().contains("downloads")) {
                downloadedFile.setValue(file);
            }
        });
        String content = FileUtils.readFileToString(
                downloadedFile.getValue(), StandardCharsets.UTF_8);
        Assertions.assertTrue(
                content.contains("<b>This</b> file <i>must</i> be saved as is, "
                        + "with this <span>formatting</span>"),
                "Invalid or missing download file.");
    }

    @Test
    public void testMaxURLs() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=basic&depth=0");
        getCrawlerConfig(collector, 0).setMaxDocuments(15);
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("URLs", docs, 15);
    }

    @Test
    public void testUserAgent() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=userAgent");
        getCrawlerConfig(collector, 0).setMaxDepth(0);
        ((GenericHttpFetcher) getCrawlerConfig(collector, 0).getHttpFetchers()
                .get(0)).getConfig().setUserAgent("Super Secret Agent");

//        crawler.getCrawlerConfig().setUserAgent("Super Secret Agent");
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        HttpDocument doc = docs.get(0);
        try (CachedInputStream is = doc.getInputStream()) {
            Assertions.assertTrue(IOUtils.toString(is,
                    StandardCharsets.UTF_8).contains("Super Secret Agent"),
                    "Wrong or undetected User-Agent.");
            is.dispose();
        }
    }

    @Test
    public void testCanonicalLink() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=canonical");
        final MutableInt canCount = new MutableInt();

        collector.getEventManager().addListener(e -> {
            if (e.is(HttpCrawlerEvent.REJECTED_NONCANONICAL)) {
                canCount.increment();
            }
        });

        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);

        Assertions.assertEquals(
                2, canCount.intValue(),
                "Wrong number of canonical link rejection.");
    }


    @Test
    public void testSpecialURLs() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=specialURLs");
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 4);
    }


    @Test
    public void testScriptTags() throws IOException {
        // Content of <script> tags must be stripped by GenericLinkExtractor
        // but src must be followed.
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=script");

        GenericLinkExtractor le = new GenericLinkExtractor();
        le.addLinkTag("script", "src");
        getCrawlerConfig(collector, 0).setLinkExtractors(le);
//        crawler.getCrawlerConfig().setLinkExtractors(le);
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);

        assertListSize("document", docs, 2);

        for (HttpDocument doc : docs) {
            try (CachedInputStream is = doc.getInputStream()) {
                String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                is.dispose();
                if (!doc.getReference().contains("script=true")) {
                    // first page
                    Assertions.assertTrue(
                            content.contains("View the source"),
                    "First page not crawled properly");
                    Assertions.assertTrue(
                            !content.contains("THIS_MUST_BE_STRIPPED"),
                    "Did not strip inside of <script>");
                } else {
                    // second page
                    Assertions.assertTrue(
                            content.contains("This must be crawled"),
                    "Script page not crawled properly");
                }
            }
        }
    }

    // related to https://github.com/Norconex/collector-http/issues/313
    @Test
    public void testZeroLength() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=zeroLength");
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);
    }

    // Next two related to https://github.com/Norconex/importer/issues/41
    @Test
    public void testContentTypeCharsetDefault() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=contentTypeCharset");
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);
        HttpDocument doc = docs.get(0);

        Assertions.assertEquals(
                doc.getMetadata().getString(ImporterMetadata.DOC_CONTENT_TYPE),
                "application/javascript");
        Assertions.assertEquals("Big5", doc.getMetadata().getString(
                ImporterMetadata.DOC_CONTENT_ENCODING));
    }
    @Test
    public void testContentTypeCharsetDetect() throws IOException {
        HttpCollector collector = newHttpCollector1Crawler(
                "/test?case=contentTypeCharset");
        GenericHttpFetcher fetcher = (GenericHttpFetcher)
                getCrawlerConfig(collector, 0).getHttpFetchers().get(0);
        fetcher.getConfig().setDetectContentType(true);
        fetcher.getConfig().setDetectCharset(true);
        collector.start(false);

        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers().get(0);
        List<HttpDocument> docs = getCommitedDocuments(crawler);
        assertListSize("document", docs, 1);
        HttpDocument doc = docs.get(0);

        Assertions.assertEquals("text/html",
                doc.getMetadata().getString(ImporterMetadata.DOC_CONTENT_TYPE));
        Assertions.assertEquals(StandardCharsets.UTF_8.toString(),
                doc.getMetadata().getString(
                        ImporterMetadata.DOC_CONTENT_ENCODING));
    }


    private void testDepth(List<HttpDocument> docs) {
        // 0-depth + 10 others == 11 expected files
        Assertions.assertEquals( 11, docs.size(),
                "Did not crawl the right depth.");
    }
    private void testValidMetadata(HttpDocument doc) {
        HttpMetadata meta = doc.getMetadata();

        //Test single value
        assertOneValue(meta,
                HttpMetadata.HTTP_CONTENT_TYPE,
                HttpMetadata.COLLECTOR_CONTENT_TYPE,
                HttpMetadata.COLLECTOR_CONTENT_ENCODING);

        //Test actual values
        Assertions.assertTrue(
                "text/html; charset=UTF-8".equalsIgnoreCase(
                        meta.getString(HttpMetadata.HTTP_CONTENT_TYPE)),
                "Bad HTTP content-type");
        Assertions.assertTrue(
                "text/html".equalsIgnoreCase(
                        meta.getString(HttpMetadata.COLLECTOR_CONTENT_TYPE)),
                "Bad Collection content-type.");
        Assertions.assertTrue(
                StandardCharsets.UTF_8.toString().equalsIgnoreCase(
                    meta.getString(HttpMetadata.COLLECTOR_CONTENT_ENCODING)),
                "Bad char-encoding.");
    }
    private void assertListSize(String listName, List<?> list, int size) {
        Assertions.assertEquals( size, list.size(),

                "Wrong " + listName + " list size.");
    }
    private void assertOneValue(HttpMetadata meta, String... fields) {
        for (String field : fields) {
            Assertions.assertEquals(
                    1, meta.getStrings(field).size(),
                field + " does not contain strickly 1 value.");
        }
    }

}
