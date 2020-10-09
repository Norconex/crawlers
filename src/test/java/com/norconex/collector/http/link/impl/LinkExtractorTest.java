/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.link.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.TestUtil;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.collector.http.link.Link;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;


/**
 * Tests multiple {@link ILinkExtractor} implementations.
 * @author Pascal Essiembre
 */
public class LinkExtractorTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            LinkExtractorTest.class);

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "{index} {1}")
    @MethodSource(value= {
            "linkExtractorProvider"
    })
    @interface LinkExtractorsTest {}

    static Stream<Arguments> linkExtractorProvider() {
        return Stream.of(
                TestUtil.args(() -> {
                    HtmlLinkExtractor hle = new HtmlLinkExtractor();
                    hle.addLinkTag("link", null);
                    return hle;
                }),
                TestUtil.args(new DOMLinkExtractor()),
                TestUtil.args(new TikaLinkExtractor())
        );
    }

    //--- Common tests ---------------------------------------------------------

    @LinkExtractorsTest
    public void testLinkExtraction(ILinkExtractor extractor, String testName)
            throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "LinkExtractorTest.html";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "meta-redirect.html",
                baseURL + "startWithDoubleslash.html",
                docURL + "?startWith=questionmark",
                docURL + "#startWithHashMark",
                baseURL + "startWithSlash.html",
                baseDir + "relativeToLastSegment.html",
                "http://www.sample.com/blah.html",
                baseURL + "onTwoLines.html",
                baseURL + "imageSlash.gif",
                baseURL + "imageNoSlash.gif",
                baseDir + "titleTarget.html",
                baseURL + "htmlEntities",
                baseURL + "?p1=v1&p2=v2&p3=v3",
                baseURL + "contains two spaces.html",
        };
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "badhref.html",
                baseURL + "nofollow.html",
                baseURL + "/dont/process/scripts/'+variable+'",
                baseURL + "/dont/process/a/'+inscript+'",
                baseURL + "comment.html",
                baseDir, // empty href
        };

        // Only HtmlLinkExtractor:
        if (extractor instanceof HtmlLinkExtractor) {
            String[] additionalURLs = {
                    baseURL + "addedTagNoAttribUrlInBody.html",
                    baseURL + "addedTagAttribUrlInBody.html",
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);

            String[] fewerURLs = {
                    "tel:123",
                    "mailto:blah@blah.com"
            };
            unexpectedURLs = ArrayUtils.addAll(unexpectedURLs, fewerURLs);
        }
        // Only TikaLinkExtractor:
        if (extractor instanceof TikaLinkExtractor) {
            String[] additionalURLs = {
                    "tel:123",
                    "mailto:blah@blah.com"
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);
        }

        InputStream is = getClass().getResourceAsStream(
                "LinkExtractorTest.html");

        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(docURL, ContentType.HTML, is));
        is.close();

        for (String expectedURL : expectedURLs) {
            assertTrue(
                    contains(links, expectedURL),
                "Could not find expected URL: " + expectedURL);
        }
        for (String unexpectedURL : unexpectedURLs) {
            assertFalse(
                    contains(links, unexpectedURL),
                "Found unexpected URL: " + unexpectedURL);
        }

        Assertions.assertEquals(
                expectedURLs.length, links.size(),
                "Invalid number of links extracted.");
    }

    //--- BASE HREF Tests ------------------------------------------------------

    @LinkExtractorsTest
    public void testBaseHrefLinkExtraction(
            ILinkExtractor extractor, String testName) throws IOException {
        String docURL = "http://www.example.com/test/absolute/"
                + "LinkBaseHrefTest.html";
        String host = "http://www.sample.com";
        String baseURL = host + "/blah/";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "a/b/c.html",
                host + "/d/e/f.html",
                "http://www.sample.com/g/h/i.html",
                "http://www.anotherhost.com/k/l/m.html",
        };

        InputStream is =
                getClass().getResourceAsStream("LinkBaseHrefTest.html");
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(docURL, ContentType.HTML, is));
        is.close();
        for (String expectedURL : expectedURLs) {
            assertTrue(
                    contains(links, expectedURL),
                "Could not find expected URL: " + expectedURL);
        }
        Assertions.assertEquals(
                expectedURLs.length, links.size(),
                "Invalid number of links extracted.");
    }


    @LinkExtractorsTest
    public void testRelativeBaseHrefLinkExtraction(
            ILinkExtractor extractor, String testName) throws IOException {

        if (extractor instanceof TikaLinkExtractor) {
            // TikaLinkExtractor does not support all the same test cases
            // as the generic one. Consider updating it or deprecating it.
            return;
        }

        String docURL = "http://www.example.com/test/relative/"
                + "LinkRelativeBaseHrefTest.html";

        // All these must be found
        String[] expectedURLs = {
                "http://www.example.com/test/relative/blah.html?param=value",
                "http://www.example.com/d/e/f.html",
                "http://www.example.com/test/relative/path^blah.html",
                "http://www.anotherhost.com/k/l/m.html",
        };

        InputStream is =
                getClass().getResourceAsStream("LinkRelativeBaseHrefTest.html");
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(docURL, ContentType.HTML, is));
        is.close();
        for (String expectedURL : expectedURLs) {
            assertTrue(
                    contains(links, expectedURL),
                "Could not find expected URL: " + expectedURL);
        }
        Assertions.assertEquals(
                expectedURLs.length, links.size(),
                "Invalid number of links extracted.");
    }

    //--- Referrer Data Tests --------------------------------------------------

    @LinkExtractorsTest
    public void testLinkKeepReferrer(
            ILinkExtractor extractor, String testName) throws IOException {
        // All these must be found
        Set<Link> expectedLinks = new HashSet<>(Arrays.asList(
                keepReferrerLink("1-notitle-notext.html", null, null),
                keepReferrerLink("2-notitle-yestext.html", "2 Yes Text", null),
                keepReferrerLink(
                        "3-yestitle-yestext.html", "3 Yes Text", "3 Yes Title"),
                keepReferrerLink("4-yestitle-notext.html", null, "4 Yes Title"),
                // Link 5 should not be there (no href).
                keepReferrerLink("6-yestitle-yestexthtml.html",
                        "[6]Yes Text", "6 Yes Title")
        ));

        InputStream is =
                getClass().getResourceAsStream("LinkKeepReferrerTest.html");
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc("http://www.site.com/parent.html",
                        ContentType.HTML, is));
        is.close();

        TestUtil.assertSameEntries(expectedLinks, links);
    }


    private Link keepReferrerLink(
            String relURL, String text, String title) {
        Link link = new Link("http://www.site.com/" + relURL);
        link.setReferrer("http://www.site.com/parent.html");
        link.getMetadata().set("tag", "a");
        link.getMetadata().set("attr", "href");
        if (text != null) {
            link.getMetadata().set("text", text);
        }
        if (title != null) {
            link.getMetadata().set("attr.title", title);
        }
        return link;
    }

    //--- Extract/NoExtract Tests ----------------------------------------------
    @Test
    public void testExtractBetween() throws IOException {
        String baseURL = "http://www.example.com/";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "include1.html",
                baseURL + "include2.html",
                baseURL + "include3.html",
                baseURL + "include4.html",
                baseURL + "include5.html",
        };
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "exclude1.html",
                baseURL + "exclude2.html",
                baseURL + "exclude3.html",
                baseURL + "exclude4.html",
                baseURL + "exclude5.html",
                baseURL + "exclude6.html",
                baseURL + "exclude7.html",
        };

        // Only HtmlLinkExtractor:
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        extractor.addExtractBetween("<include1>", "</include1>\\s+", true);
        extractor.addExtractBetween("<Include2>", "</Include2>\\s+", false);
        extractor.addNoExtractBetween("<exclude1>", "</exclude1>\\s+", true);
        extractor.addNoExtractBetween("<Exclude2>", "</Exclude2>\\s+", false);

        Set<Link> links;
        try (InputStream is = getClass().getResourceAsStream(
                "LinkExtractBetweenTest.html")) {
            links = extractor.extractLinks(
                    toCrawlDoc(baseURL + "LinkExtractBetweenTest.html",
                            ContentType.HTML, is));
        }

        for (String expectedURL : expectedURLs) {
            assertTrue(
                    contains(links, expectedURL),
                "Could not find expected URL: " + expectedURL);
        }
        for (String unexpectedURL : unexpectedURLs) {
            assertFalse(
                    contains(links, unexpectedURL),
                "Found unexpected URL: " + unexpectedURL);
        }

        Assertions.assertEquals(
                expectedURLs.length, links.size(),
                "Invalid number of links extracted.");
    }

    @Test
    public void testExtractSelector() throws IOException {
        String baseURL = "http://www.example.com/";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "include1.html",
                baseURL + "include2.html",
                baseURL + "include3.html",
                baseURL + "include4.html",
                baseURL + "include5.html",
        };
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "exclude1.html",
                baseURL + "exclude2.html",
                baseURL + "exclude3.html",
                baseURL + "exclude4.html",
                baseURL + "exclude5.html",
                baseURL + "exclude6.html",
                baseURL + "exclude7.html",
        };

        // Only HtmlLinkExtractor:
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        extractor.addExtractSelectors("include1");
        extractor.addExtractSelectors("include2");
        extractor.addNoExtractSelectors("exclude1");
        extractor.addNoExtractSelectors("exclude2");

        Set<Link> links;
        try (InputStream is = getClass().getResourceAsStream(
                "LinkExtractBetweenTest.html")) {
            links = extractor.extractLinks(
                    toCrawlDoc(baseURL + "LinkExtractBetweenTest.html",
                            ContentType.HTML, is));
        }

        for (String expectedURL : expectedURLs) {
            Assertions.assertTrue(contains(links, expectedURL),
                    "Could not find expected URL: " + expectedURL);
        }
        for (String unexpectedURL : unexpectedURLs) {
            Assertions.assertFalse(contains(links, unexpectedURL),
                    "Found unexpected URL: " + unexpectedURL);
        }

        Assertions.assertEquals(expectedURLs.length, links.size(),
                "Invalid number of links extracted.");
    }

    //--- Other Tests ----------------------------------------------------------
    @Test
    public void testHtmlWriteRead() {
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        extractor.setIgnoreNofollow(true);
        extractor.addLinkTag("food", "chocolate");
        extractor.addLinkTag("friend", "Thor");
        extractor.addExtractBetween("start1", "end1", true);
        extractor.addExtractBetween("start2", "end2", false);
        extractor.addNoExtractBetween("nostart1", "noend1", true);
        extractor.addNoExtractBetween("nostart2", "noend2", false);
        LOG.debug("Writing/Reading this: {}", extractor);
        XML.assertWriteRead(extractor, "extractor");
    }

    @Test
    public void testHtmlEquivRefreshIssue210()
            throws IOException {
        String html = "<html><head><meta http-equiv=\"refresh\" "
                + "content=\"0; URL=en/91/index.html\">"
                + "</head><body></body></html>";
        String docURL = "http://db-artmag.com/index_en.html";
        ILinkExtractor extractor = new HtmlLinkExtractor();
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(docURL, ContentType.HTML,
                        new ByteArrayInputStream(html.getBytes())));

        Assertions.assertEquals( 1, links.size(),
                "Invalid number of links extracted.");
        Assertions.assertEquals(
                "http://db-artmag.com/en/91/index.html",
                links.iterator().next().getUrl());
    }

    @Test
    public void testTikaWriteRead() {
        TikaLinkExtractor extractor = new TikaLinkExtractor();
        extractor.setIgnoreNofollow(true);
        LOG.debug("Writing/Reading this: {}", extractor);
        XML.assertWriteRead(extractor, "extractor");
    }


    @Test
    public void testIssue188() throws IOException {
        String ref = "http://www.site.com/en/articles/articles.html"
                + "?param1=value1&param2=value2";
        String url = "http://www.site.com/en/articles/detail/article-x.html";
        String html = "<html><body>"
                + "<a href=\"/en/articles/detail/article-x.html\">test link</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(ref, ContentType.HTML, input));
        input.close();
        Assertions.assertTrue(contains(links, url),
                "URL not extracted: " + url);
    }

    @Test
    public void testIssue236() throws IOException {
        String url = "javascript:__doPostBack('MoreInfoList1$Pager','2')";
        String html = "<html><body>"
                + "<a href=\"" + url + "\">JavaScript link</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        extractor.setSchemes("javascript");
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc("N/A", ContentType.HTML, input));
        input.close();
        Assertions.assertTrue(contains(links, url),
                "URL not extracted: " + url);
    }

    // Related to https://github.com/Norconex/collector-http/pull/312
    @Test
    public void testHtmlBadlyFormedURL() throws IOException {
        String ref = "http://www.example.com/index.html";
        String url = "http://www.example.com/invalid^path^.html";
        String html = "<html><body>"
                + "<a href=\"/invalid^path^.html\">test link</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(ref, ContentType.HTML, input));
        input.close();
        Assertions.assertTrue(contains(links, url),
                "URL not extracted: " + url);
    }

    //Test for: https://github.com/Norconex/collector-http/issues/423
    @Test
    public void testUnquottedURL() throws IOException {
        String ref = "http://www.example.com/index.html";
        String url1 = "http://www.example.com/unquoted_url1.html";
        String url2 = "http://www.example.com/unquoted_url2.html";
        String html = "<html><body>"
                + "<a href=unquoted_url1.html>test link 1</a>"
                + "<a href=unquoted_url2.html title=\"blah\">test link 2</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(ref, ContentType.HTML, input));
        input.close();
        assertTrue(
                contains(links, url1),
                "Could not find expected URL: " + url1);
        assertTrue(
                contains(links, url2),
                "Could not find expected URL: " + url2);
    }
    @Test
    public void testBadQuotingURL() throws IOException {
        String ref = "http://www.example.com/index.html";
        String url1 = "http://www.example.com/bad\"quote1.html";
        String url2 = "http://www.example.com/bad'quote2.html";
        String url3 = "http://www.example.com/bad\"quote3.html";
        String html = "<html><body>"
                + "<a href=bad\"quote1.html>test link 1</a>"
                + "<a href=\"bad'quote2.html\">test link 1</a>"
                + "<a href='bad\"quote3.html'>test link 1</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(ref, ContentType.HTML, input));
        input.close();
        assertTrue(
                contains(links, url1),
                "Could not find expected URL: " + url1);
        assertTrue(
                contains(links, url2),
                "Could not find expected URL: " + url2);
        assertTrue(
                contains(links, url3),
                "Could not find expected URL: " + url3);
    }

    @Test
    public void testDOMWriteRead() {
        DOMLinkExtractor extractor = new DOMLinkExtractor();
        extractor.setIgnoreNofollow(true);
        extractor.setCharset("charset");
        extractor.setParser("xml");
        extractor.setSchemes("http", "test");
        LOG.debug("Writing/Reading this: {}", extractor);
        XML.assertWriteRead(extractor, "extractor");
    }

    private boolean contains(Set<Link> links, String url) {
        for (Link link : links) {
            if (url.equals(link.getUrl())) {
                return true;
            }
        }
        return false;
    }

    private CrawlDoc toCrawlDoc(String ref, ContentType ct, InputStream is) {
        HttpDocInfo docInfo = new HttpDocInfo(ref);
        docInfo.setContentType(ct);
        CrawlDoc doc = new CrawlDoc(docInfo, CachedInputStream.cache(is));
        doc.getMetadata().set(DocMetadata.CONTENT_TYPE, ct);
        return doc;
    }
}
