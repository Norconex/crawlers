/* Copyright 2025 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger.DocLedgerBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.QueueBootstrapper;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.fetch.MultiFetcher.ResponseAggregator;
import com.norconex.crawler.core.fetch.MultiFetcher.UnsuccessfulResponseFactory;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Builder(builderClassName = "Builder")
@Getter
@Accessors(fluent = true)
@NonNull
public class CrawlDriver {

    @Default
    private final Class<? extends CrawlConfig> crawlerConfigClass =
            CrawlConfig.class;

    @Default
    private final List<CrawlBootstrapper> bootstrappers =
            new ArrayList<>(List.of(
                    new DocLedgerBootstrapper(),
                    new QueueBootstrapper()));
    private final CrawlDocPipelines docPipelines;
    @Default
    private final CrawlCallbacks callbacks = CrawlCallbacks.builder().build();
    @Default
    private final BeanMapper beanMapper = BeanMapper.DEFAULT;
    @Default
    private final EventManager eventManager = new EventManager();
    /**
     * Provides a required fetcher implementation, responsible for obtaining
     * resources being crawled.
     */
    private final FetchDriver fetchDriver;

    /**
     * The exact type of {@link CrawlDocContext} if your crawler is subclassing
     * it. Defaults to {@link CrawlDocContext} class.
     */
    @Default
    private final Class<? extends CrawlDocContext> docContextType =
            CrawlDocContext.class;

    @Accessors(fluent = true)
    @Data
    @NonNull
    public static class FetchDriver {
        private UnsuccessfulResponseFactory unsuccesfulResponseFactory;
        private ResponseAggregator responseAggregator;
    }
}
