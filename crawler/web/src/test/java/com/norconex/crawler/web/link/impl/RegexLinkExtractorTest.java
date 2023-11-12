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
package com.norconex.crawler.web.link.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.link.Link;
import com.norconex.importer.doc.DocMetadata;

@Disabled
class RegexLinkExtractorTest {

    //TODO add a post import test for PDF with links.
    //TODO add a post import test for grabbing a URL value from a field.

    @Test
    void testLinkExtraction()  throws IOException {
        var baseURL = "http://www.example.com/";
        var baseDir = baseURL + "test/";
        var docURL = baseDir + "RegexLinkExtractorTest.html";

        var extractor = new RegexLinkExtractor();
        extractor.getConfiguration()
            .addPattern("\\[\\s*(.*?)\\s*\\]", "$1")
            .addPattern("<link>\\s*(.*?)\\s*</link>", "$1")
            .addPattern("<a href=\"javascript:;\"[^>]*?id=\"p_(\\d+)\">",
                    "/page?id=$1");

        // All these must be found
        String[] expectedURLs = {
                baseURL + "page1.html",
                baseURL + "page2.html",
                baseURL + "page3.html",
                baseURL + "page4.html",
                baseDir + "page5.html",
                baseURL + "page?id=12345",
                baseURL + "page?id=67890",
        };
        var is = getClass().getResourceAsStream(
                "RegexLinkExtractorTest.txt");

        var links = extractor.extractLinks(
                toCrawlDoc(docURL, ContentType.TEXT, is));
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
    void testJSLinkFromXML()  throws IOException {
        var baseURL = "http://www.example.com/";
        var baseDir = baseURL + "test/";
        var docURL = baseDir + "RegexLinkExtractorTest.html";

        var extractor = new RegexLinkExtractor();
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                getClass().getSimpleName() + ".cfg.xml"))) {
            new XML(r).populate(extractor);
        }
        // All these must be found
        String[] expectedURLs = {
                baseURL + "page?id=12345",
                baseURL + "page?id=67890",
        };

        Set<Link> links;
        try (var is = getClass().getResourceAsStream(
                "RegexLinkExtractorTest.txt")) {
            links = extractor.extractLinks(
                    toCrawlDoc(docURL, ContentType.TEXT, is));
        }

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
        var extractor = new RegexLinkExtractor();
        extractor.getConfiguration()
            .addPattern("\\[(.*?)\\]", "$1")
            .addPattern("<link>.*?</link>", "$1")
            .setCharset(UTF_8)
            .setMaxURLLength(12345);
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
        var docRecord = new WebDocRecord(ref);
        docRecord.setContentType(ct);
        var doc = new CrawlDoc(docRecord, CachedInputStream.cache(is));
        doc.getMetadata().set(DocMetadata.CONTENT_TYPE, ct);
        return doc;
    }
}
