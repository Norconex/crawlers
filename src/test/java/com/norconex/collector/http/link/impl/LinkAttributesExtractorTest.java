/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.link.impl;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.TestUtil;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.collector.http.link.Link;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.parser.ParseState;


/**
 * Tests that link attributes are captured properly with links.
 * @author Pascal Essiembre
 */
public class LinkAttributesExtractorTest {

    private final String baseURL = "http://www.example.com/";
    private final String pageName = "LinkAttributesExtractorTest.html";
    private final String pageURL = baseURL + pageName;

    private final Set<Link> expectedLinks;

    public LinkAttributesExtractorTest() {
        super();
        Link link0 = new Link(baseURL + "0-meta.html");
        link0.setReferrer(pageURL);
        link0.getMetadata().add("tag", "meta");
        link0.getMetadata().add("attr", "content");
        link0.getMetadata().add("attr.http-equiv", "refresh");

        Link link1 = new Link(baseURL + "1-a.html");
        link1.setReferrer(pageURL);
        link1.getMetadata().add("tag", "a");
        link1.getMetadata().add("attr", "href");
        link1.getMetadata().add("text", "A Link Text");
        link1.getMetadata().add("attr.class", "btn btn-primary");
        link1.getMetadata().add("attr.title", "A Title");
        link1.getMetadata().add("attr.data-type", "regular");

        Link link2 = new Link(baseURL + "2-link.css");
        link2.setReferrer(pageURL);
        link2.getMetadata().add("tag", "link");
        link2.getMetadata().add("attr", "href");
        link2.getMetadata().add("attr.rel", "stylesheet");
        link2.getMetadata().add("attr.type", "text/css");

        Link link3 = new Link(baseURL + "3-img.jpg");
        link3.setReferrer(pageURL);
        link3.getMetadata().add("tag", "img");
        link3.getMetadata().add("attr", "src");
        link3.getMetadata().add("attr.title", "Image Title");
        link3.getMetadata().add("attr.style",
                "width: 64px; display: inline-block");
        link3.getMetadata().add("attr.alt", "Image Alt");

        this.expectedLinks = new TreeSet<>(
                Arrays.asList(link0, link1, link2, link3));
    }



    @ParameterizedTest(name = "{index} {1}")
    @MethodSource(value= {
            "linkExtractorProvider"
    })
    public void testLinkAttributesExtract(
            ILinkExtractor ex, String testName) throws Exception {


        Set<Link> links;
        try (InputStream is = getClass().getResourceAsStream(
                "LinkAttributesExtractorTest.html")) {
            HttpDocInfo docInfo = new HttpDocInfo();
            docInfo.setReference(baseURL + "LinkAttributesExtractorTest.html");
            docInfo.setContentType(ContentType.HTML);
            CrawlDoc doc = new CrawlDoc(docInfo, CachedInputStream.cache(is));
            doc.getMetadata().set(DocMetadata.CONTENT_TYPE, ContentType.HTML);
            links = ex.extractLinks(doc, ParseState.PRE);
        }

        TestUtil.assertSameEntries(expectedLinks, links);
    }


    static Stream<Arguments> linkExtractorProvider() {
        HtmlLinkExtractor htmlLE = new HtmlLinkExtractor();
        htmlLE.addLinkTag("link", "href");
        return Stream.of(
                TestUtil.args(htmlLE),
                TestUtil.args(new DOMLinkExtractor())
                // Tika one does not extract every attributes.
                //arg(new TikaLinkExtractor())
        );
    }
}
