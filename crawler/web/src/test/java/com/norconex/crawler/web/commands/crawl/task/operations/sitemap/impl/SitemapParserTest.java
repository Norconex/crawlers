/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.commands.crawl.task.operations.sitemap.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

class SitemapParserTest {

    @Test
    void testParse() throws IOException {
        List<WebCrawlDocContext> extractedLinks = new ArrayList<>();
        var p = new SitemapParser(false, new MutableBoolean(false));

        try (var is = getClass().getResourceAsStream("sitemap.xml")) {

            var childSitemaps = p.parse(
                    CrawlDocStubs.crawlDoc(
                            "https://example.com/index.html",
                            ContentType.XML,
                            is),
                    d -> {
                        extractedLinks.add(d);
                    });
            assertThat(childSitemaps).isEmpty();
        }

        // All links there?
        Assertions.assertEquals(
                Arrays.asList(
                        "https://example.com/linkA",
                        "https://example.com/linkB",
                        "https://example.com/linkC",
                        "https://example.com/linkD"),
                extractedLinks.stream()
                        .map(WebCrawlDocContext::getReference)
                        .collect(Collectors.toList()));

        // test second one:
        var doc = extractedLinks.get(1);
        Assertions.assertEquals(
                "https://example.com/linkB", doc.getReference());
        Assertions.assertEquals(
                "2021-04-01",
                doc.getSitemapLastMod().toLocalDate().toString());
        Assertions.assertEquals("daily", doc.getSitemapChangeFreq());
        Assertions.assertEquals(1f, doc.getSitemapPriority());
    }
}
