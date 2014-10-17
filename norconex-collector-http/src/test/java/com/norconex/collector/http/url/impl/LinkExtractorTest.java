/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.url.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.file.ContentType;


/**
 * Tests multiple {@link ILinkExtractor} implementations.
 * @author Pascal Essiembre
 */
public class LinkExtractorTest {

    @Test
    public void testHtmlLinkExtractor() throws IOException {
        testLinkExtraction(new HtmlLinkExtractor());
    }
    @Test
    public void testTikaLinkExtractor() throws IOException {
        testLinkExtraction(new TikaLinkExtractor());
    }

    @Test
    public void testHtmlWriteRead() throws IOException {
        HtmlLinkExtractor extractor = new HtmlLinkExtractor();
        extractor.setContentTypes(ContentType.HTML, ContentType.XML);
        extractor.setIgnoreNofollow(true);
        extractor.setKeepReferrerData(true);
        extractor.addLinkTag("food", "chocolate");
        extractor.addLinkTag("friend", "Thor");
        System.out.println("Writing/Reading this: " + extractor);
        ConfigurationUtil.assertWriteRead(extractor);
    }

    
    @Test
    public void testTikaWriteRead() throws IOException {
        TikaLinkExtractor extractor = new TikaLinkExtractor();
        extractor.setContentTypes(ContentType.HTML, ContentType.XML);
        extractor.setIgnoreNofollow(true);
        extractor.setKeepReferrerData(true);
        System.out.println("Writing/Reading this: " + extractor);
        ConfigurationUtil.assertWriteRead(extractor);
    }
    
    private void testLinkExtraction(ILinkExtractor extractor) 
            throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "LinkExtractorTest.html";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "meta-redirect.html",
                baseURL + "startWithDoubleslash.html",
                docURL + "?startWith=questionmark",
                docURL, // <-- "#startWithHashMark" (hash is stripped)
                baseURL + "startWithSlash.html",
                baseDir + "relativeToLastSegment.html",
                "http://www.sample.com/blah.html",
                baseURL + "onTwoLines.html",
                baseURL + "imageSlash.gif",
                baseURL + "imageNoSlash.gif",
        };
        
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "badhref.html",
                baseURL + "nofollow.html",
        };

        InputStream is = getClass().getResourceAsStream(
                "LinkExtractorTest.html");

        Set<Link> links = extractor.extractLinks(
                is, docURL, ContentType.HTML);

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL, 
                    contains(links, expectedURL));
        }
        for (String unexpectedURL : unexpectedURLs) {
            assertFalse("Found unexpected URL: " + unexpectedURL, 
                    contains(links, unexpectedURL));
        }
        IOUtils.closeQuietly(is);
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
