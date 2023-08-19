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
package com.norconex.crawler.server.api.feature.crawl.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequest.FetcherType;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;

import reactor.core.publisher.FluxSink;

@MockitoSettings
class CrawlSampleRequestMapperTest {

    @Mock
    private FluxSink<Object> sink;

    @Test
    void testMapRequest() {
        var req = new CrawlSampleRequest();
        req.setIgnoreRobotRules(true);
        req.setIgnoreSitemap(true);
        req.setFetcher(FetcherType.WEBDRIVER);
        req.getUrlExcludes().add("url1.com");
        req.getFieldIncludes().add("field1.com");

        var sessionConfig =
                CrawlSampleRequestMapper.mapRequest(req, sink);
        var crawlerConfig = (WebCrawlerConfig)
                sessionConfig.getCrawlerConfigs().get(0);
        assertThat(crawlerConfig.getRobotsMetaProvider()).isNull();
        assertThat(crawlerConfig.getRobotsTxtProvider()).isNull();
        assertThat(crawlerConfig.getSitemapLocator()).isNull();
        assertThat(crawlerConfig.getSitemapResolver()).isNull();


    }
}
