/* Copyright 2010-2015 Norconex Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
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
    public void testGenericLinkExtractor() throws IOException {
        GenericLinkExtractor ex = new GenericLinkExtractor();
        ex.addLinkTag("link", null);
        testLinkExtraction(ex);
    }
    @Test
    public void testTikaLinkExtractor() throws IOException {
        testLinkExtraction(new TikaLinkExtractor());
    }

    @Test
    public void testGenericWriteRead() throws IOException {
        GenericLinkExtractor extractor = new GenericLinkExtractor();
        extractor.setContentTypes(ContentType.HTML, ContentType.XML);
        extractor.setIgnoreNofollow(true);
        extractor.setKeepReferrerData(true);
        extractor.setKeepFragment(true);
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
                baseDir + "titleTarget.html",
                baseURL + "htmlEntities",
                baseURL + "?p1=v1&p2=v2&p3=v3",
        };
        // only GenericLinkExtractor supports these extra URLs:
        if (extractor instanceof GenericLinkExtractor) {
            String[] additionalURLs = {
                    baseURL + "addedTagNoAttribUrlInBody.html",
                    baseURL + "addedTagAttribUrlInBody.html",
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);
        }
        
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "badhref.html",
                baseURL + "nofollow.html",
                baseURL + "/dont/process/scripts/'+variable+'",
                baseURL + "/dont/process/a/'+inscript+'",
                baseDir // empty href
        };

        InputStream is = getClass().getResourceAsStream(
                "LinkExtractorTest.html");

        Set<Link> links = extractor.extractLinks(
                is, docURL, ContentType.HTML);
        IOUtils.closeQuietly(is);

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL, 
                    contains(links, expectedURL));
        }
        for (String unexpectedURL : unexpectedURLs) {
            assertFalse("Found unexpected URL: " + unexpectedURL, 
                    contains(links, unexpectedURL));
        }

        Assert.assertEquals("Invalid number of links extracted.", 
                expectedURLs.length, links.size());
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
