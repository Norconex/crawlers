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
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.init.CrawlerInitializers;
import com.norconex.crawler.core.init.ledger.DocLedgerInitializer;
import com.norconex.crawler.core.init.queue.QueueInitializer;
import com.norconex.crawler.core.pipelines.CrawlerPipelines;

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
    private CrawlerInitializers initializers = CrawlerInitializers.builder()
            .initializers(Predicates.allOf(
                    new DocLedgerInitializer(),
                    new QueueInitializer()))
            .build();
    private CrawlerPipelines pipelines;
    private CrawlerCallbacks callbacks = CrawlerCallbacks.builder().build();
    private BeanMapper beanMapper = BeanMapper.DEFAULT;
    private EventManager eventManager = new EventManager();

    /**
     * The exact type of {@link CrawlDocContext} if your crawler is subclassing
     * it. Defaults to {@link CrawlDocContext} class.
     */
    private Class<? extends CrawlDocContext> docContextType =
            CrawlDocContext.class;

    /**
     * Provides a required fetcher implementation, responsible for obtaining
     * resources being crawled.
     */
    private Function<CrawlerContext, ? extends Fetcher<
            ? extends FetchRequest, ? extends FetchResponse>> fetcherProvider;
}
