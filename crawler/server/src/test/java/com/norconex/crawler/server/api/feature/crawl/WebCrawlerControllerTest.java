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

import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.server.api.TestUtil;
import com.norconex.crawler.server.api.common.ServerSentEventName;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlEventDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequest;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@WebFluxTest(WebCrawlerController.class)
class WebCrawlerControllerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private WebCrawlerService crawlerService;

    @Test
    void testCrawlSample() {
        var event1 = new CrawlEventDTO();
        event1.setName(CrawlerEvent.CRAWLER_INIT_BEGIN);
        event1.getProperties().put("mykey", "myvalue");

        var doc1 = new CrawlDocDTO();
        doc1.setReference("https://example.com");
        doc1.setContent("Sample content");

        var event2 = new CrawlEventDTO();
        event1.setName(CrawlerEvent.CRAWLER_INIT_END);

        var req = new CrawlSampleRequest();
        req.setStartUrl("https://example.com");

        given(crawlerService.crawlSample(
                Mockito.any(CrawlSampleRequest.class)))
            .willReturn(Flux.just(event1, doc1, event2));

        var result =
            webClient.post()
                .uri("/api/v1/crawler/sample")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(ServerSentEvent.class);

        var eventFlux = result.getResponseBody();

        StepVerifier.create(eventFlux)
            .assertNext(sse -> TestUtil.assertServerSentEvent(
                    sse, ServerSentEventName.CRAWL_EVENT, event1))
            .assertNext(sse -> TestUtil.assertServerSentEvent(
                    sse, ServerSentEventName.CRAWL_DOC, doc1))
            .assertNext(sse -> TestUtil.assertServerSentEvent(
                    sse, ServerSentEventName.CRAWL_EVENT, event2))
            .verifyComplete();
    }

}