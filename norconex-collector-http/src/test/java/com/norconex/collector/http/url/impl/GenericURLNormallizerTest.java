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
package com.norconex.collector.http.url.impl;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.After;
import org.junit.Test;

import com.norconex.collector.http.url.impl.GenericURLNormalizer.Normalization;
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
                n.new Replace("\\.htm$", ".html"),
                n.new Replace("&debug=true"),
                n.new Replace("(http://)(.*//)(www.example.com)", "$1$3"));

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
                n.new Replace("\\.htm", ".html"),
                n.new Replace("&debug=true"));
        System.out.println("Writing/Reading this: " + n);
        ConfigurationUtil.assertWriteRead(n);
    }

}
