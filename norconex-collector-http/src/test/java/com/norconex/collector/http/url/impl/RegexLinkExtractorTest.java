/* Copyright 2017-2019 Norconex Inc.
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
package com.norconex.collector.http.url.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.XML;

/**
 * @author Pascal Essiembre
 */
public class RegexLinkExtractorTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            RegexLinkExtractorTest.class);

    @Test
    public void testLinkExtraction()  throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "RegexLinkExtractorTest.html";

        RegexLinkExtractor extractor = new RegexLinkExtractor();
        extractor.addPattern("\\[\\s*(.*?)\\s*\\]", "$1");
        extractor.addPattern("<link>\\s*(.*?)\\s*</link>", "$1");
        extractor.addPattern("<a href=\"javascript:;\"[^>]*?id=\"p_(\\d+)\">",
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
        InputStream is = getClass().getResourceAsStream(
                "RegexLinkExtractorTest.txt");

        Set<Link> links = extractor.extractLinks(
                is, docURL, ContentType.TEXT);
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
    public void testJSLinkFromXML()  throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "RegexLinkExtractorTest.html";

        RegexLinkExtractor extractor = new RegexLinkExtractor();
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                getClass().getSimpleName() + ".cfg.xml"))) {
            extractor.loadFromXML(new XML(r));
        }
        // All these must be found
        String[] expectedURLs = {
                baseURL + "page?id=12345",
                baseURL + "page?id=67890",
        };

        Set<Link> links;
        try (InputStream is = getClass().getResourceAsStream(
                "RegexLinkExtractorTest.txt")) {
            links = extractor.extractLinks(is, docURL, ContentType.TEXT);
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
    public void testGenericWriteRead() throws IOException {
        RegexLinkExtractor extractor = new RegexLinkExtractor();
        extractor.addPattern("\\[(.*?)\\]", "$1");
        extractor.addPattern("<link>.*?</link>", "$1");
        extractor.setApplyToContentTypePattern("ct");
        extractor.setApplyToReferencePattern("ref");
        extractor.setCharset("charset");
        extractor.setMaxURLLength(12345);
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
}
