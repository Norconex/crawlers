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

import org.springframework.stereotype.Service;

import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlEventDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequest;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlSampleRequestMapper;
import com.norconex.crawler.web.WebCrawlSession;

import reactor.core.publisher.Flux;

/**
 * Web crawler service.
 */
@Service
public class WebCrawlerService {

    /**
     * Crawl a sample set of web pages matching the request.
     * @param req crawl sample request
     * @return flux of crawled objects, either {@link CrawlEventDTO}
     *      or {@link CrawlDocDTO}
     */
    public Flux<Object> crawlSample(CrawlSampleRequest req) {
        return Flux.create(sink -> {
            try {
                WebCrawlSession.createSession(
                        CrawlSampleRequestMapper.mapRequest(req, sink)).start();
            } catch (RuntimeException e) {
                sink.error(e);
            } finally {
                sink.complete();
            }
        });
    }
}

