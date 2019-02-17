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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ContentTypeDetector;

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

        ContentType ct = new ContentTypeDetector().detect(is);

        Assert.assertTrue("Atom file not accepted.",
                extractor.accepts(docURL, ct));

        Set<Link> links = extractor.extractLinks(is, docURL, ct);
        is.close();

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL,
                    contains(links, expectedURL));
        }

        Assert.assertEquals("Invalid number of links extracted.",
                expectedURLs.length, links.size());
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

        ContentType ct = new ContentTypeDetector().detect(is);

        Assert.assertTrue("RSS file not accepted.",
                extractor.accepts(docURL, ct));


        Set<Link> links = extractor.extractLinks(is, docURL, ct);
        is.close();

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL,
                    contains(links, expectedURL));
        }

        Assert.assertEquals("Invalid number of links extracted.",
                expectedURLs.length, links.size());
    }

    @Test
    public void testGenericWriteRead() throws IOException {
        XMLFeedLinkExtractor extractor = new XMLFeedLinkExtractor();
        extractor.setApplyToContentTypePattern("ct");
        extractor.setApplyToReferencePattern("ref");
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
