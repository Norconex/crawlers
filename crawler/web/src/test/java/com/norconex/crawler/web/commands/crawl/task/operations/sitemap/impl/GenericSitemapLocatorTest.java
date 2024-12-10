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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.util.Web;

class GenericSitemapLocatorTest {

    @WebCrawlTest(focus = Focus.CONTEXT)
    void testGenericSitemapLocator(CrawlerContext ctx) {

        var locator = new GenericSitemapLocator();
        assertThat(locator.getConfiguration().getPaths()).contains(
                "/sitemap.xml", "/sitemap_index.xml");

        locator.getConfiguration().setPaths(List.of("abc.xml", "def.xml"));
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(locator));

        Web.config(ctx)
                .setRobotsTxtProvider(null)
                .setStartReferences(List.of("http://example.com/index.html"));

        assertThat(locator.locations(
                "http://example.com/index.html", ctx))
                        .containsExactly(
                                "http://example.com/abc.xml",
                                "http://example.com/def.xml");

        // try with empty paths
        locator.getConfiguration().setPaths(null);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(locator));
    }
}
