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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.norconex.importer.ContentType;

/**
 * @author Pascal Essiembre
 */
public class DefaultURLExtractorTest {

    private static final String BASEURL = "http://www.example.com/";
    
    @Test
    public void testURLExtraction() throws IOException {
        Reader docReader = new InputStreamReader(getClass().getResourceAsStream(
                "DefaultURLExtractorTest.html"));
        DefaultURLExtractor x = new DefaultURLExtractor();
        String docURL = BASEURL + "DefaultURLExtractorTest.html";

        Set<String> urls = x.extractURLs(docReader, docURL, ContentType.HTML);

        testURL(urls, "test-001/doubleslash.html");
        testURL(urls, "test-002/meta-redirect.html");
        
    }

    private void testURL(Set<String> allUrls, String expectedURL) {
        String fullExpectedURL = BASEURL + expectedURL;
        String testString = StringUtils.substringBefore(expectedURL, "/");
        for (String url : allUrls) {
            if (url.contains(testString)) {
                System.out.println("Extracted URL: " + url);
                assertEquals(fullExpectedURL, url);
                return;
            }
        }
        fail("This URL could not be extracted: " + expectedURL);
    }

}
