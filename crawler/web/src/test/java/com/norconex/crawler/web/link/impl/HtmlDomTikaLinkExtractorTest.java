/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.link.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.crawler.web.WebStubber;
import com.norconex.crawler.web.link.Link;
import com.norconex.crawler.web.link.LinkExtractor;

/**
 * Tests {@link LinkExtractor} implementations that focuses on HTML or
 * HTML-like web pages.
 */
@Disabled
class HtmlDomTikaLinkExtractorTest {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "{0}")
    @MethodSource("linkExtractorProvider")
    @interface LinkExtractorsTest {}
    static Stream<LinkExtractor> linkExtractorProvider() {
        var hle = new HtmlLinkExtractor();
        hle.getConfiguration().addLinkTag("link", null);
        return Stream.of(
            hle,
            new DomLinkExtractor(),
            new TikaLinkExtractor()
        );
    }

    //--- Common tests ---------------------------------------------------------
    @LinkExtractorsTest
    void testLinkExtraction(LinkExtractor extractor)
            throws IOException {
        var baseURL = "http://www.example.com/";
        var baseDir = baseURL + "test/";
        var docURL = baseDir + "LinkExtractorTest.html";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "meta-redirect.html",
                baseURL + "startWithDoubleslash.html",
                docURL + "?startWith=questionmark",
                docURL + "#startWithHashMark",
                baseURL + "startWithSlash.html",
                baseDir + "relativeToLastSegment.html",
                "http://www.sample.com/blah.html",
                baseURL + "onTwoLines.html",
                baseURL + "imageSlash.gif",
                baseURL + "imageNoSlash.gif",
                baseDir + "titleTarget.html",
                baseURL + "htmlEntities",
                baseURL + "?p1=v1&p2=v2&p3=v3",
                baseURL + "contains two spaces.html",
        };
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "badhref.html",
                baseURL + "nofollow.html",
                baseURL + "/dont/process/scripts/'+variable+'",
                baseURL + "/dont/process/a/'+inscript+'",
                baseURL + "comment.html",
                baseDir, // empty href
        };

        // Only HtmlLinkExtractor:
        if (extractor instanceof HtmlLinkExtractor) {
            String[] additionalURLs = {
                    baseURL + "addedTagNoAttribUrlInBody.html",
                    baseURL + "addedTagAttribUrlInBody.html",
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);

            String[] fewerURLs = {
                    "tel:123",
                    "mailto:blah@blah.com"
            };
            unexpectedURLs = ArrayUtils.addAll(unexpectedURLs, fewerURLs);
        }
        // Only TikaLinkExtractor:
        if (extractor instanceof TikaLinkExtractor) {
            String[] additionalURLs = {
                    "tel:123",
                    "mailto:blah@blah.com"
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);
        }

        var is = getClass().getResourceAsStream(
                "LinkExtractorTest.html");

        var links = extractor.extractLinks(
                WebStubber.crawlDoc(docURL, ContentType.HTML, is));
        is.close();

        var actualUrls = links.stream().map(Link::getUrl).toList();
        assertThat(actualUrls).containsExactlyInAnyOrder(expectedURLs);
    }

    //--- BASE HREF Tests ------------------------------------------------------

    @LinkExtractorsTest
    void testBaseHrefLinkExtraction(
            LinkExtractor extractor) throws IOException {
        var docURL = "http://www.example.com/test/absolute/"
                + "LinkBaseHrefTest.html";
        var host = "http://www.sample.com";
        var baseURL = host + "/blah/";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "a/b/c.html",
                host + "/d/e/f.html",
                "http://www.sample.com/g/h/i.html",
                "http://www.anotherhost.com/k/l/m.html",
        };

        var is = getClass().getResourceAsStream("LinkBaseHrefTest.html");
        var links = extractor.extractLinks(
                WebStubber.crawlDoc(docURL, ContentType.HTML, is));
        is.close();

        var actualUrls = links.stream().map(Link::getUrl).toList();
        assertThat(actualUrls).containsExactlyInAnyOrder(expectedURLs);
    }


    @LinkExtractorsTest
    void testRelativeBaseHrefLinkExtraction(
            LinkExtractor extractor) throws IOException {

        if (extractor instanceof TikaLinkExtractor) {
            // TikaLinkExtractor does not support all the same test cases
            // as the generic one. Consider updating it or deprecating it.
            return;
        }

        var docURL = "http://www.example.com/test/relative/"
                + "LinkRelativeBaseHrefTest.html";

        // All these must be found
        String[] expectedURLs = {
                "http://www.example.com/test/relative/blah.html?param=value",
                "http://www.example.com/d/e/f.html",
                "http://www.example.com/test/relative/path^blah.html",
                "http://www.anotherhost.com/k/l/m.html",
        };

        var is = getClass().getResourceAsStream(
                "LinkRelativeBaseHrefTest.html");
        var links = extractor.extractLinks(
                WebStubber.crawlDoc(docURL, ContentType.HTML, is));
        is.close();

        var actualUrls = links.stream().map(Link::getUrl).toList();
        assertThat(actualUrls).containsExactlyInAnyOrder(expectedURLs);
    }

    //--- Referrer Data Tests --------------------------------------------------

    @LinkExtractorsTest
    void testLinkKeepReferrer(LinkExtractor extractor) throws IOException {
        // All these must be found
        Set<Link> expectedLinks = new HashSet<>(Arrays.asList(
                linkWithReferrer("1-notitle-notext.html", null, null, null),
                linkWithReferrer(
                        "2-notitle-yestext.html",
                        "2 Yes Text",
                        null,
                        null),
                linkWithReferrer(
                        "3-yestitle-yestext.html",
                        "3 Yes Text",
                        null,
                        "3 Yes Title"),
                linkWithReferrer(
                        "4-yestitle-notext.html", null, null, "4 Yes Title"),
                // Link 5 should not be there (no href).
                linkWithReferrer(
                        "6-yestitle-yestexthtml.html",
                        "[6]Yes Text",
                        (extractor instanceof TikaLinkExtractor)
                            ? null : "[<font color=\"red\">6</font>]Yes Text",
                        "6 Yes Title")
        ));

        var is = getClass().getResourceAsStream(
                "LinkKeepReferrerTest.html");
        var links = extractor.extractLinks(
                WebStubber.crawlDoc("http://www.site.com/parent.html",
                        ContentType.HTML, is));
        is.close();

        assertThat(links).containsExactlyInAnyOrderElementsOf(expectedLinks);
    }

    @LinkExtractorsTest
    void testWriteRead(LinkExtractor extractor) {
        LinkExtractor randomEx = WebStubber.randomize(extractor.getClass());
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(randomEx));
    }

    private Link linkWithReferrer(
            String relURL, String text, String markup, String title) {
        var link = new Link("http://www.site.com/" + relURL);
        link.setReferrer("http://www.site.com/parent.html");
        link.getMetadata().set("tag", "a");
        link.getMetadata().set("attr", "href");
        if (text != null) {
            link.getMetadata().set("text", text);
        }
        if (markup != null) {
            link.getMetadata().set("markup", markup);
        }
        if (title != null) {
            link.getMetadata().set("attr.title", title);
        }
        return link;
    }
}
