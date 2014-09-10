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
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.norconex.collector.http.url.IURLExtractor;
import com.norconex.commons.lang.file.ContentType;


/**
 * Tests multiple {@link IURLExtractor} implementations.
 * @author Pascal Essiembre
 */
public class URLExtractorTest {

    @Test
    public void testDefaultURLExtractor() throws IOException {
        testURLExtraction(new DefaultURLExtractor());
    }
    @Test
    public void testTikaURLExtractor() throws IOException {
        testURLExtraction(new TikaURLExtractor());
    }

    private void testURLExtraction(IURLExtractor extractor) throws IOException {
        
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "URLExtractorTest.html";

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
        };
        
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "badhref.html",
        };

        Reader docReader = new InputStreamReader(getClass().getResourceAsStream(
                "URLExtractorTest.html"));

        Set<String> urls = extractor.extractURLs(
                docReader, docURL, ContentType.HTML);

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL, 
                    urls.contains(expectedURL));
        }
        for (String unexpectedURL : unexpectedURLs) {
            assertFalse("Found unexpected URL: " + unexpectedURL, 
                    urls.contains(unexpectedURL));
        }
        IOUtils.closeQuietly(docReader);
    }
}
