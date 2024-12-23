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
import com.norconex.crawler.core.init.CrawlerInitializers;
import com.norconex.crawler.core.init.ledger.DocLedgerInitializer;
import com.norconex.crawler.core.init.queue.FileRefEnqueuer;
import com.norconex.crawler.core.init.queue.ListRefEnqueuer;
import com.norconex.crawler.core.init.queue.ProviderRefEnqueuer;
import com.norconex.crawler.core.init.queue.QueueInitializer;
import com.norconex.crawler.web.callbacks.WebCrawlerCallbacks;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;
import com.norconex.crawler.web.pipelines.WebPipelines;
import com.norconex.crawler.web.pipelines.queue.SitemapEnqueuer;

public class WebCrawlerSpecProvider implements CrawlerSpecProvider {

    @Override
    public CrawlerSpec get() {
        return new CrawlerSpec()
                .initializers(CrawlerInitializers.builder()
                        .initializer(new DocLedgerInitializer())
                        .initializer(new QueueInitializer(List.of(
                                new SitemapEnqueuer(),
                                new ListRefEnqueuer(),
                                new FileRefEnqueuer(),
                                new ProviderRefEnqueuer())))
                        .build())
                .crawlerConfigClass(WebCrawlerConfig.class)
                .fetcherProvider(new HttpFetcherProvider())
                .callbacks(WebCrawlerCallbacks.get())
                .pipelines(WebPipelines.create())
                .docContextType(WebCrawlDocContext.class);
    }
}
