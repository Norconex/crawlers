/* Copyright 2010-2013 Norconex Inc.
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
package com.norconex.collector.http.handler.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;

import org.junit.Test;

import com.norconex.importer.ContentType;

/**
 * @author Pascal Essiembre
 */
public class DefaultURLExtractorTest {

    @Test
    public void testURLExtraction() throws IOException {
        
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "DefaultURLExtractorTest.html";
        String[] expectedURLs = {
                baseURL + "meta-redirect.html",
                baseURL + "startWithDoubleslash.html",
                docURL + "?startWith=questionmark",
                docURL, // <-- "#startWithHashMark" (hash is stripped)
                baseURL + "startWithSlash.html",
                baseDir + "relativeToLastSegment.html",
                "http://www.sample.com/blah.html"
        };
        
        Reader docReader = new InputStreamReader(getClass().getResourceAsStream(
                "DefaultURLExtractorTest.html"));
        DefaultURLExtractor extractor = new DefaultURLExtractor();

        Set<String> urls = extractor.extractURLs(
                docReader, docURL, ContentType.HTML);

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find extracted URL: " + expectedURL, 
                    urls.contains(expectedURL));
        }
    }
}
