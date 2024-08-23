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

import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericSitemapLocatorTest {

    //TODO MIGRATE ME

    @Test
    @Disabled
    void testGenericSitemapLocator(@TempDir Path tempDir) {

//        var locator = new GenericSitemapLocator();
//        assertThat(locator.getConfiguration().getPaths()).contains(
//                "/sitemap.xml", "/sitemap_index.xml");
//
//        locator.getConfiguration().setPaths(List.of("abc.xml", "def.xml"));
//        assertThatNoException().isThrownBy(
//                () -> BeanMapper.DEFAULT.assertWriteRead(locator));
//
//        var session = WebStubber.crawlSession(
//                tempDir, "http://example.com/index.html");
//        var crawler = Crawler.builder()
//                .crawlSession(session)
//                .crawlerImpl(WebCrawlerImplFactory.create())
//                .crawlerConfig(session.getCrawlSessionConfig()
//                        .getCrawlerConfigs().get(0))
//                .build();
//        Web.config(crawler).setRobotsTxtProvider(null);
//        assertThat(locator.locations(
//                "http://example.com/index.html", crawler))
//            .containsExactly(
//                    "http://example.com/abc.xml",
//                    "http://example.com/def.xml");
//
//        // try with empty paths
//        locator.getConfiguration().setPaths(null);
//        assertThatNoException().isThrownBy(
//                () -> BeanMapper.DEFAULT.assertWriteRead(locator));
    }
}
