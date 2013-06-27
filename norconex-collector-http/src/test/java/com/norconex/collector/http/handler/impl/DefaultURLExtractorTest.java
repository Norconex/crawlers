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
        Reader docReader = new InputStreamReader(getClass().getResourceAsStream(
                "DefaultURLExtractorTest.html"));
        DefaultURLExtractor x = new DefaultURLExtractor();

        String docURL = "http://www.example.com/DefaultURLExtractorTest.html";
        Set<String> urls = x.extractURLs(docReader, docURL, ContentType.HTML);
        for (String url : urls) {
            // Double leading slashes
            if (url.contains("/test-001/")) {
                assertEquals("http://www.example.com/test-001/test.html", url);
            }
        }
    }
}
