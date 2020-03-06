/* Copyright 2017-2020 Norconex Inc.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.link.Link;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.parser.ParseState;

/**
 * @author Pascal Essiembre
 */
public class XMLFeedLinkExtractorTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            XMLFeedLinkExtractorTest.class);

    @Test
    public void testAtomLinkExtraction()  throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "XMLFeedLinkExtractorTest.atom";

        XMLFeedLinkExtractor extractor = new XMLFeedLinkExtractor();

        // All these must be found
        String[] expectedURLs = {
                baseURL + "atom",
                baseURL + "atom/page1.html",
                baseURL + "atom/page2.html",
        };

        InputStream is = IOUtils.buffer(getClass().getResourceAsStream(
                "XMLFeedLinkExtractorTest.atom"));

        ContentType ct = ContentTypeDetector.detect(is);

//        Assertions.assertTrue(
//                extractor.accepts(docURL, ct),
//                "Atom file not accepted.");

        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(docURL, ct, is), ParseState.PRE);
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

    @Test
    public void testRSSLinkExtraction()  throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "XMLFeedLinkExtractorTest.rss";

        XMLFeedLinkExtractor extractor = new XMLFeedLinkExtractor();

        // All these must be found
        String[] expectedURLs = {
                baseURL + "rss",
                baseURL + "rss/page1.html",
                baseURL + "rss/page2.html",
        };

        InputStream is = IOUtils.buffer(getClass().getResourceAsStream(
                "XMLFeedLinkExtractorTest.rss"));

        ContentType ct = ContentTypeDetector.detect(is);

//        Assertions.assertTrue(
//                extractor.accepts(docURL, ct),
//                "RSS file not accepted.");


        Set<Link> links = extractor.extractLinks(
                toCrawlDoc(docURL, ct, is), ParseState.PRE);
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

    @Test
    public void testGenericWriteRead() {
        XMLFeedLinkExtractor extractor = new XMLFeedLinkExtractor();
        extractor.addRestriction(new PropertyMatcher(TextMatcher.basic("ct")));
        extractor.addRestriction(new PropertyMatcher(TextMatcher.basic("ref")));
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
