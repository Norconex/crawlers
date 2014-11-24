/* Copyright 2010-2014 Norconex Inc.
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.After;
import org.junit.Test;

import com.norconex.collector.http.url.impl.GenericURLNormalizer.Normalization;
import com.norconex.collector.http.url.impl.GenericURLNormalizer.Replace;
import com.norconex.commons.lang.config.ConfigurationUtil;

public class GenericURLNormallizerTest {

    private String s;
    private String t;
    
    @After
    public void tearDown() throws Exception {
        s = null;
        t = null;
    }
    
    @Test
	public void testReplacements() {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setReplaces(
                new Replace("\\.htm$", ".html"),
                new Replace("&debug=true"),
                new Replace("(http://)(.*//)(www.example.com)", "$1$3"));

        s = "http://www.example.com//www.example.com/page1.html";
        t = "http://www.example.com/page1.html";
        assertEquals(t, n.normalizeURL(s));

        s = "http://www.example.com/page1.htm";
        t = "http://www.example.com/page1.html";
        assertEquals(t, n.normalizeURL(s));

        s = "http://www.example.com/record?id=1&debug=true&view=print";
        t = "http://www.example.com/record?id=1&view=print";
        assertEquals(t, n.normalizeURL(s));
	}
	
    @Test
    public void testGithubIssue29() throws IOException {
        // Github issue #29
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.lowerCaseSchemeHost,
                Normalization.upperCaseEscapeSequence,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDefaultPort);
        n.setReplaces(
                new Replace("&view=print", "&view=html"));
        
        s = "http://www.somehost.com/hook/";
        t = "http://www.somehost.com/hook/";
        assertEquals(t, n.normalizeURL(s));
    }
    
    @Test
    public void testWriteRead() throws IOException {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.lowerCaseSchemeHost,
                Normalization.addTrailingSlash,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDotSegments,
                Normalization.removeDuplicateSlashes,
                Normalization.removeSessionIds);
        n.setReplaces(
                new Replace("\\.htm", ".html"),
                new Replace("&debug=true"));
        System.out.println("Writing/Reading this: " + n);
        ConfigurationUtil.assertWriteRead(n);
    }

}
