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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.WebStubber;
import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.link.Link;
import com.norconex.crawler.web.link.LinkExtractor;
import com.norconex.importer.doc.DocMetadata;

/**
 * Tests {@link LinkExtractor} implementations that are common
 * to HTML and DOM link extractors.
 */
class HtmlDomLinkExtractorTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("testExtractBetweenProvider")
    void testExtractBetween(LinkExtractor extractor) throws IOException {

        var baseURL = "http://www.example.com/";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "include1.html",
                baseURL + "include2.html",
                baseURL + "include3.html",
                baseURL + "include4.html",
                baseURL + "include5.html",
        };

        Set<Link> links;
        try (var is = getClass().getResourceAsStream(
                "LinkExtractBetweenTest.html")) {
            links = extractor.extractLinks(
                    WebStubber.crawlDoc(
                            baseURL + "LinkExtractBetweenTest.html",
                            ContentType.HTML, is));
        }

        var actualUrls = links.stream().map(Link::getUrl).toList();
        assertThat(actualUrls).containsExactlyInAnyOrder(expectedURLs);
    }
    static Stream<LinkExtractor> testExtractBetweenProvider() {
        var htmlExtractor = new HtmlLinkExtractor();
        htmlExtractor.addExtractSelectors(List.of("include1", "include2"));
        htmlExtractor.addNoExtractSelectors(List.of("exclude1", "exclude2"));
        var domExtractor = new DOMLinkExtractor();
        domExtractor.addExtractSelectors(List.of("include1", "include2"));
        domExtractor.addNoExtractSelectors(List.of("exclude1", "exclude2"));
        return Stream.of(
                htmlExtractor,
                domExtractor
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testExtractAttributesProvider")
    void testExtractAttributes(LinkExtractor extractor) throws IOException {
        var baseURL = "http://www.example.com/";
        var pageName = "LinkAttributesExtractorTest.html";
        var pageURL = baseURL + pageName;

        var link0 = new Link(baseURL + "0-meta.html");
        link0.setReferrer(pageURL);
        link0.getMetadata().add("tag", "meta");
        link0.getMetadata().add("attr", "content");
        link0.getMetadata().add("attr.http-equiv", "refresh");

        var link1 = new Link(baseURL + "1-a.html");
        link1.setReferrer(pageURL);
        link1.getMetadata().add("tag", "a");
        link1.getMetadata().add("attr", "href");
        link1.getMetadata().add("text", "A Link Text");
        link1.getMetadata().add("attr.class", "btn btn-primary");
        link1.getMetadata().add("attr.title", "A Title");
        link1.getMetadata().add("attr.data-type", "regular");

        var link2 = new Link(baseURL + "2-link.css");
        link2.setReferrer(pageURL);
        link2.getMetadata().add("tag", "link");
        link2.getMetadata().add("attr", "href");
        link2.getMetadata().add("attr.rel", "stylesheet");
        link2.getMetadata().add("attr.type", "text/css");

        var link3 = new Link(baseURL + "3-img.jpg");
        link3.setReferrer(pageURL);
        link3.getMetadata().add("tag", "img");
        link3.getMetadata().add("attr", "src");
        link3.getMetadata().add("attr.title", "Image Title");
        link3.getMetadata().add("attr.style",
                "width: 64px; display: inline-block");
        link3.getMetadata().add("attr.alt", "Image Alt");

        Set<Link> expectedLinks = new TreeSet<>(
                Arrays.asList(link0, link1, link2, link3));

        Set<Link> links;
        try (var is = getClass().getResourceAsStream(
                "LinkAttributesExtractorTest.html")) {
            var docRecord = new HttpDocRecord();
            docRecord.setReference(baseURL + "LinkAttributesExtractorTest.html");
            docRecord.setContentType(ContentType.HTML);
            var doc = new CrawlDoc(docRecord, CachedInputStream.cache(is));
            doc.getMetadata().set(DocMetadata.CONTENT_TYPE, ContentType.HTML);
            links = extractor.extractLinks(doc);
        }
        assertThat(links).containsExactlyInAnyOrderElementsOf(expectedLinks);
    }
    static Stream<LinkExtractor> testExtractAttributesProvider() {
        var htmlExtractor = new HtmlLinkExtractor();
        htmlExtractor.addLinkTag("link", "href");
        var domExtractor = new DOMLinkExtractor();
        return Stream.of(
                htmlExtractor,
                domExtractor
        );
    }
}
