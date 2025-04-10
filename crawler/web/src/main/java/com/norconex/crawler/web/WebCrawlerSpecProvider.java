/* Copyright 2024 Norconex Inc.
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

import com.norconex.crawler.core.CrawlerSpec;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrappers;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger.DocLedgerBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.FileRefEnqueuer;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.ListRefEnqueuer;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.ProviderRefEnqueuer;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.QueueBootstrapper;
import com.norconex.crawler.web.callbacks.WebCrawlerCallbacks;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.pipelines.WebDocPipelines;
import com.norconex.crawler.web.doc.pipelines.queue.SitemapEnqueuer;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;

public class WebCrawlerSpecProvider implements CrawlerSpecProvider {

    @Override
    public CrawlerSpec get() {
        return new CrawlerSpec()
                .bootstrappers(CrawlBootstrappers.builder()
                        .bootstrapper(new DocLedgerBootstrapper())
                        .bootstrapper(new QueueBootstrapper(List.of(
                                new SitemapEnqueuer(),
                                new ListRefEnqueuer(),
                                new FileRefEnqueuer(),
                                new ProviderRefEnqueuer())))
                        .build())
                .crawlerConfigClass(WebCrawlerConfig.class)
                .fetcherProvider(new HttpFetcherProvider())
                .callbacks(WebCrawlerCallbacks.get())
                .pipelines(WebDocPipelines.create())
                .docContextType(WebCrawlDocContext.class);
    }
}
