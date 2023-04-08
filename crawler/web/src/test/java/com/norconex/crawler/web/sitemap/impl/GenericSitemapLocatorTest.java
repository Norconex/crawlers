/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web.sitemap.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.web.WebStubber;
import com.norconex.crawler.web.crawler.impl.WebCrawlerImplFactory;
import com.norconex.crawler.web.util.Web;

class GenericSitemapLocatorTest {

    @Test
    void testGenericSitemapLocator(@TempDir Path tempDir) {

        var locator = new GenericSitemapLocator();
        assertThat(locator.getPaths()).contains(
                "/sitemap.xml", "/sitemap_index.xml");

        locator.setPaths(List.of("abc.xml", "def.xml"));
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(locator, "sitemapLocator"));

        var session = WebStubber.crawlSession(
                tempDir, "http://example.com/index.html");
        var crawler = Crawler.builder()
                .crawlSession(session)
                .crawlerImpl(WebCrawlerImplFactory.create())
                .crawlerConfig(session.getCrawlSessionConfig()
                        .getCrawlerConfigs().get(0))
                .build();
        Web.config(crawler).setRobotsTxtProvider(null);
        assertThat(locator.locations(
                "http://example.com/index.html", crawler))
            .containsExactly(
                    "http://example.com/abc.xml",
                    "http://example.com/def.xml");

        // try with empty paths
        locator.setPaths(null);
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(locator, "sitemapLocator"));
    }
}
