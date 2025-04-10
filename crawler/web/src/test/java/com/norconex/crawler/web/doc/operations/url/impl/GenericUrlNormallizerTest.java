/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.url.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizerConfig.Normalization;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizerConfig.NormalizationReplace;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class GenericUrlNormallizerTest {

    private String s;
    private String t;

    @AfterEach
    void tearDown() throws Exception {
        s = null;
        t = null;
    }

    @Test
    void testAddDomainTrailingSlash() {
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setNormalizations(
                List.of(
                        Normalization.ADD_DOMAIN_TRAILING_SLASH));
        s = "http://example.com";
        t = "http://example.com/";
        assertEquals(t, n.normalizeURL(s));
    }

    // Test for https://github.com/Norconex/collector-http/issues/2904
    @Test
    void testUppercaseProtocol() {
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setNormalizations(
                List.of(
                        Normalization.ENCODE_NON_URI_CHARACTERS));
        s = "HTTP://example.com/";
        t = "HTTP://example.com/";
        assertEquals(t, n.normalizeURL(s));
    }

    // Test for https://github.com/Norconex/collector-http/issues/290
    @Test
    void testRemoveTrailingSlashWithOnlyHostname() {
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setNormalizations(
                List.of(
                        Normalization.REMOVE_TRAILING_SLASH));
        s = "http://bot.nerus.com/";
        t = "http://bot.nerus.com";
        assertEquals(t, n.normalizeURL(s));
    }

    @Test
    void testReplacements() {
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setReplacements(
                List.of(
                        new NormalizationReplace("\\.htm$", ".html"),
                        new NormalizationReplace("&debug=true"),
                        new NormalizationReplace(
                                "(http://)(.*//)(www.example.com)",
                                "$1$3")));

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
    void testGithubIssue160() {
        // Github issue #160
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setNormalizations(
                List.of(
                        Normalization.LOWERCASE_SCHEME_HOST,
                        Normalization.UPPERCASE_ESCAPESEQUENCE,
                        Normalization.DECODE_UNRESERVED_CHARACTERS,
                        Normalization.REMOVE_DEFAULT_PORT,
                        Normalization.REMOVE_FRAGMENT,
                        Normalization.REMOVE_DOT_SEGMENTS,
                        Normalization.ADD_DIRECTORY_TRAILING_SLASH,
                        Normalization.REMOVE_DUPLICATE_SLASHES,
                        Normalization.REMOVE_SESSION_IDS,
                        Normalization.UPPERCASE_ESCAPESEQUENCE));

        s = "http://www.etools.ch/sitemap_index.xml";
        t = "http://www.etools.ch/sitemap_index.xml";
        assertEquals(t, n.normalizeURL(s));
    }

    @Test
    void testGithubIssue29() {
        // Github issue #29
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setNormalizations(
                List.of(
                        Normalization.LOWERCASE_SCHEME_HOST,
                        Normalization.UPPERCASE_ESCAPESEQUENCE,
                        Normalization.DECODE_UNRESERVED_CHARACTERS,
                        Normalization.REMOVE_DEFAULT_PORT));
        n.getConfiguration().setReplacements(
                List.of(
                        new NormalizationReplace("&view=print", "&view=html")));

        s = "http://www.somehost.com/hook/";
        t = "http://www.somehost.com/hook/";
        assertEquals(t, n.normalizeURL(s));
    }

    @Test
    void testWriteRead() {
        var n = new GenericUrlNormalizer();
        n.getConfiguration().setNormalizations(
                List.of(
                        Normalization.LOWERCASE_SCHEME_HOST,
                        Normalization.ADD_DIRECTORY_TRAILING_SLASH,
                        Normalization.DECODE_UNRESERVED_CHARACTERS,
                        Normalization.REMOVE_DOT_SEGMENTS,
                        Normalization.REMOVE_DUPLICATE_SLASHES,
                        Normalization.REMOVE_SESSION_IDS));
        n.getConfiguration().setReplacements(
                List.of(
                        new NormalizationReplace("\\.htm", ".html"),
                        new NormalizationReplace("&debug=true")));
        LOG.debug("Writing/Reading this: {}", n);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(n));
    }

    @Test
    void testLoadFromXML() throws IOException {
        GenericUrlNormalizer n;
        var xml1 = "<urlNormalizer><normalizations></normalizations>"
                + "</urlNormalizer>";

        // an empty <normalizations> tag means have none
        n = new GenericUrlNormalizer();
        try (Reader r = new StringReader(xml1)) {
            BeanMapper.DEFAULT.read(n, r, Format.XML);
        }
        assertEquals(0, n.getConfiguration().getNormalizations().size());

        // no <normalizations> tag means use defaults
        n = new GenericUrlNormalizer();
        var xml2 = "<urlNormalizer></urlNormalizer>";
        try (Reader r = new StringReader(xml2)) {
            BeanMapper.DEFAULT.read(n, r, Format.XML);
        }
        assertEquals(6, n.getConfiguration().getNormalizations().size());

        // normal... just a few
        n = new GenericUrlNormalizer();
        var xml3 = """
                <urlNormalizer>
                  <normalizations>
                    <normalization>removeSessionIds</normalization>
                    <normalization>lowerCaseSchemeHost</normalization>
                  </normalizations>
                </urlNormalizer>""";
        try (Reader r = new StringReader(xml3)) {
            BeanMapper.DEFAULT.read(n, r, Format.XML);
        }
        assertEquals(2, n.getConfiguration().getNormalizations().size());
    }
}
