/* Copyright 2010-2020 Norconex Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.url.impl.GenericURLNormalizer.Normalization;
import com.norconex.collector.http.url.impl.GenericURLNormalizer.Replace;
import com.norconex.commons.lang.xml.XML;
public class GenericURLNormallizerTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericURLNormallizerTest.class);

    private String s;
    private String t;

    @AfterEach
    public void tearDown() throws Exception {
        s = null;
        t = null;
    }

    @Test
    public void testAddDomainTrailingSlash() {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.addDomainTrailingSlash
                );
        s = "http://example.com";
        t = "http://example.com/";
        assertEquals(t, n.normalizeURL(s));
    }

    // Test for https://github.com/Norconex/collector-http/issues/2904
    @Test
    public void testUppercaseProtocol() {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.encodeNonURICharacters
                );
        s = "HTTP://example.com/";
        t = "HTTP://example.com/";
        assertEquals(t, n.normalizeURL(s));
    }

    // Test for https://github.com/Norconex/collector-http/issues/290
    @Test
    public void testRemoveTrailingSlashWithOnlyHostname() {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.removeTrailingSlash
                );
        s = "http://bot.nerus.com/";
        t = "http://bot.nerus.com";
        assertEquals(t, n.normalizeURL(s));
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
    public void testGithubIssue160() {
        // Github issue #160
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.lowerCaseSchemeHost,
                Normalization.upperCaseEscapeSequence,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDefaultPort,
                Normalization.removeFragment,
                Normalization.removeDotSegments,
                Normalization.addDirectoryTrailingSlash,
                Normalization.removeDuplicateSlashes,
                Normalization.removeSessionIds,
                Normalization.upperCaseEscapeSequence
                );

        s = "http://www.etools.ch/sitemap_index.xml";
        t = "http://www.etools.ch/sitemap_index.xml";
        assertEquals(t, n.normalizeURL(s));
    }

    @Test
    public void testGithubIssue29() {
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
    public void testWriteRead() {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.lowerCaseSchemeHost,
                Normalization.addDirectoryTrailingSlash,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDotSegments,
                Normalization.removeDuplicateSlashes,
                Normalization.removeSessionIds);
        n.setReplaces(
                new Replace("\\.htm", ".html"),
                new Replace("&debug=true"));
        LOG.debug("Writing/Reading this: {}", n);
        XML.assertWriteRead(n, "urlNormalizer");
    }

    @Test
    public void testEmptyNormalizations() throws IOException {
        GenericURLNormalizer n = null;
        String xml = null;

        // an empty <normalizations> tag means have none
        n = new GenericURLNormalizer();
        xml = "<urlNormalizer><normalizations></normalizations>"
                + "</urlNormalizer>";
        try (Reader r = new StringReader(xml)) {
            new XML(r).populate(n);
        }
        assertEquals(0, n.getNormalizations().size());

        // no <normalizations> tag means use defaults
        n = new GenericURLNormalizer();
        xml = "<urlNormalizer></urlNormalizer>";
        try (Reader r = new StringReader(xml)) {
            new XML(r).populate(n);
        }
        assertEquals(6, n.getNormalizations().size());

        // normal... just a few
        n = new GenericURLNormalizer();
        xml = "<urlNormalizer><normalizations>"
                + "lowerCaseSchemeHost, removeSessionIds</normalizations>"
                + "</urlNormalizer>";
        try (Reader r = new StringReader(xml)) {
            new XML(r).populate(n);
        }
        assertEquals(2, n.getNormalizations().size());
    }
}