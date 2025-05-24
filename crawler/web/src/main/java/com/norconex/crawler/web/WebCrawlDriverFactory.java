/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.web;

import java.util.List;
import java.util.function.Supplier;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.CrawlDriver.FetchDriver;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger.DocLedgerBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.QueueBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.RefFileEnqueuer;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.RefListEnqueuer;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.RefProviderEnqueuer;
import com.norconex.crawler.web.callbacks.WebCrawlerCallbacks;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.pipelines.WebDocPipelines;
import com.norconex.crawler.web.doc.pipelines.queue.SitemapEnqueuer;
import com.norconex.crawler.web.fetch.AggregatedWebFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetchResponse;

public class WebCrawlDriverFactory implements Supplier<CrawlDriver> {

    public static CrawlDriver create() {
        return new WebCrawlDriverFactory().get();
    }

    @Override
    public CrawlDriver get() {
        return CrawlDriver.builder()
                .fetchDriver(createFetchDriver())
                .bootstrappers(List.of(
                        new DocLedgerBootstrapper(),
                        new QueueBootstrapper(List.of(
                                new SitemapEnqueuer(),
                                new RefListEnqueuer(),
                                new RefFileEnqueuer(),
                                new RefProviderEnqueuer()))))
                .crawlerConfigClass(WebCrawlerConfig.class)
                .callbacks(WebCrawlerCallbacks.get())
                .docPipelines(WebDocPipelines.create())
                .docContextType(WebCrawlDocContext.class)
                .build();
    }

    private static FetchDriver createFetchDriver() {
        return new FetchDriver()
                .responseAggregator(
                        (req, resps) -> new AggregatedWebFetchResponse(resps))
                .unsuccesfulResponseFactory(
                        (state, msg, e) -> HttpClientFetchResponse
                                .builder()
                                .resolutionStatus(state)
                                .reasonPhrase(msg)
                                .exception(e)
                                .statusCode(-1)
                                .build());
    }
}
