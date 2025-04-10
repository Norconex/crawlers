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
package com.norconex.crawler.core;

import java.util.function.Function;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrappers;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger.DocLedgerBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.QueueBootstrapper;
import com.norconex.crawler.core.doc.CrawlDocLedgerEntry;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

//TODO document the optional ones and their default values
@Accessors(fluent = true)
@Data
@NonNull
public class CrawlerSpec {
    private Class<? extends CrawlerConfig> crawlerConfigClass =
            CrawlerConfig.class;
    private CrawlBootstrappers bootstrappers = CrawlBootstrappers.builder()
            .bootstrappers(Predicates.allOf(
                    new DocLedgerBootstrapper(),
                    new QueueBootstrapper()))
            .build();
    private CrawlDocPipelines pipelines;
    private CrawlerCallbacks callbacks = CrawlerCallbacks.builder().build();
    private BeanMapper beanMapper = BeanMapper.DEFAULT;
    private EventManager eventManager = new EventManager();

    /**
     * The exact type of {@link CrawlDocLedgerEntry} if your crawler is subclassing
     * it. Defaults to {@link CrawlDocLedgerEntry} class.
     */
    private Class<? extends CrawlDocLedgerEntry> docContextType =
            CrawlDocLedgerEntry.class;

    /**
     * Provides a required fetcher implementation, responsible for obtaining
     * resources being crawled.
     */
    private Function<CrawlerContext, ? extends Fetcher<
            ? extends FetchRequest, ? extends FetchResponse>> fetcherProvider;
}
