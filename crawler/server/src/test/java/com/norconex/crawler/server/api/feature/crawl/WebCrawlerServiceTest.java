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
package com.norconex.crawler.server.api.feature.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.server.CrawlServerApplication;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlEventDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlEventMatcher;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequest;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequest.FetcherType;

import reactor.test.StepVerifier;

@SpringBootTest(
    classes = CrawlServerApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT
)
class WebCrawlerServiceTest {

    @Autowired
    private WebCrawlerService service;

    @LocalServerPort
    private int serverPort;

    @Test
    void testCrawlSample() {
        // crawl ourselves (swagger page).
        var req = new CrawlSampleRequest();
        req.setFetcher(FetcherType.GENERIC);
        req.setDelay(100);
        req.setMaxDocs(5);
        req.setStartUrl("http://localhost:" + serverPort);
        var eventMatcher = new CrawlEventMatcher();
        eventMatcher.setName(CrawlerEvent.CRAWLER_RUN_END);
        req.getEventMatchers().add(eventMatcher);

        var flux = service.crawlSample(req);

        StepVerifier.create(flux)
            .assertNext(obj -> {
                var doc = (CrawlDocDTO) obj;
                assertThat(doc.getReference()).contains("swagger");
                assertThat(doc.getMetadata().get("dc:title").get(0))
                    .isEqualTo("Swagger UI");
            })
            .assertNext(obj -> {
                var event = (CrawlEventDTO) obj;
                assertThat(event.getName()).contains(
                        CrawlerEvent.CRAWLER_RUN_END);
            })
            .verifyComplete();
    }
}
