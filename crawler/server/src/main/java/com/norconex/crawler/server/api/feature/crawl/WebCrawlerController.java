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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.norconex.crawler.server.api.common.ServerSentEventName;
import com.norconex.crawler.server.api.common.config.AppConfig;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequest;
import com.norconex.crawler.web.canon.CanonicalLinkDetector;
import com.norconex.crawler.web.canon.impl.GenericCanonicalLinkDetector;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Web crawling operations.
 */
@Validated
@RestController
@Tag(
    name = "Web Crawler",
    description = "Web crawler operations."
)
@RequestMapping(AppConfig.REQUEST_MAPPING_API_V1 + "/crawler")
public class WebCrawlerController {

    @Autowired
    private WebCrawlerService crawlerService;


    /**
     * Executes a "light" version of the crawler for testing or sampling
     * purposes. It offers limited configuration options over a regular crawl
     * and has hard limits that can't be overwritten.
     * (e.g., the maximum number of documents
     * returned). Not for for regular web crawling.
     * @param crawlRequest crawl test request
     * @param exchange server web exchange
     * @return flux of crawled documents
     */
    @Operation(summary = "Light crawl for testing.", responses = {
        @ApiResponse(
            description = """
                A flux of
                <a href="#model-ServerSentEventObject">\
                ServerSentEventObject</a>s having for <code>data</code> either a
                <a href="#model-CrawlDoc">CrawlDoc</a> or a
                <a href="#model-CrawlEvent">CrawlEvent</a>.""",
            content = {@Content(examples = {@ExampleObject(value = """
                id:560b6692-9317-4dcf-85c1-58a789983d55
                event:crawl_event
                data:{"name":"EVENT_NAME","properties":\
                {"property_name":"some value","property_name":"some value"}}

                id:59ad3aa9-d59d-4914-a16b-1b524881f4dd
                event:crawl_doc
                data:{"reference":"https://example.com","metadata":\
                {"field_name":["field_value"],"field_name":["field_value",\
                "field_value"]},"content":"Some document content."}"""
            )})}
        )
    })
    @PostMapping(
        value = "/sample",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE,
        consumes = APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    Flux<ServerSentEvent<Object>> crawlSample(
            @RequestBody
            CrawlSampleRequest crawlRequest,
            @Parameter(hidden = true)
            final ServerWebExchange exchange) {

        return crawlerService.crawlSample(crawlRequest).map(
                data -> ServerSentEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .event(data instanceof CrawlDocDTO
                            ? ServerSentEventName.CRAWL_DOC.name()
                            : ServerSentEventName.CRAWL_EVENT.name())
                    .data(data)
                    .build());
    }


    @PostMapping(
        value = "/tempPostConfig",
        consumes = APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    Mono<CanonicalLinkDetector> tempPostConfig(
            @RequestBody WebCrawlerConfig webCrawlerConfig) {
        // just for testing the REST API/swagger
        return Mono.just(webCrawlerConfig.getCanonicalLinkDetector());
    }

    @GetMapping(
        value = "/tempGetConfig",
        produces = APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    Mono<CanonicalLinkDetector> testGetConfig() {
        // just for testing the REST API/swagger
        return Mono.just(new GenericCanonicalLinkDetector());
    }


}
