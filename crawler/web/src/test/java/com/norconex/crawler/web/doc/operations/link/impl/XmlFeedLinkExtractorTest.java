/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.link.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.link.Link;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.doc.DocMetadata;

class XmlFeedLinkExtractorTest {

    @Test
    void testAtomLinkExtraction()  throws IOException {
        var baseURL = "http://www.example.com/";
        var baseDir = baseURL + "test/";
        var docURL = baseDir + "XmlFeedLinkExtractorTest.atom";

        var extractor = new XmlFeedLinkExtractor();

        // All these must be found
        String[] expectedURLs = {
                baseURL + "atom",
                baseURL + "atom/page1.html",
                baseURL + "atom/page2.html",
        };

        InputStream is = IOUtils.buffer(getClass().getResourceAsStream(
                "XmlFeedLinkExtractorTest.atom"));

        var ct = ContentTypeDetector.detect(is);

        var links = extractor.extractLinks(
                toCrawlDoc(docURL, ct, is));
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
    void testRSSLinkExtraction()  throws IOException {
        var baseURL = "http://www.example.com/";
        var baseDir = baseURL + "test/";
        var docURL = baseDir + "XmlFeedLinkExtractorTest.rss";

        var extractor = new XmlFeedLinkExtractor();

        // All these must be found
        String[] expectedURLs = {
                baseURL + "rss",
                baseURL + "rss/page1.html",
                baseURL + "rss/page2.html",
        };

        InputStream is = IOUtils.buffer(getClass().getResourceAsStream(
                "XmlFeedLinkExtractorTest.rss"));

        var ct = ContentTypeDetector.detect(is);

        var links = extractor.extractLinks(
                toCrawlDoc(docURL, ct, is));
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
    void testGenericWriteRead() {
        var extractor = new XmlFeedLinkExtractor();
//        extractor.addRestriction(new PropertyMatcher(TextMatcher.basic("ct")));
//        extractor.addRestriction(new PropertyMatcher(TextMatcher.basic("ref")));
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(extractor));
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
        var docRecord = new WebCrawlDocContext(ref);
        docRecord.setContentType(ct);
        var doc = new CrawlDoc(docRecord, CachedInputStream.cache(is));
        doc.getMetadata().set(DocMetadata.CONTENT_TYPE, ct);
        return doc;
    }
}
