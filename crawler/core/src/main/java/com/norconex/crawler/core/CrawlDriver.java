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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.cmd.crawl.pipeline.CrawlPipelineFactory;
import com.norconex.crawler.core.cmd.crawl.pipeline.DefaultCrawlPipelineFactory;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.ledger.CrawlEntryLedgerBootstrapper;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.fetch.MultiFetcher.ResponseAggregator;
import com.norconex.crawler.core.fetch.MultiFetcher.UnsuccessfulResponseFactory;
import com.norconex.crawler.core.ledger.CrawlEntry;

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
    private final CrawlPipelineFactory crawlPipelineFactory =
            new DefaultCrawlPipelineFactory();

    @Default
    private final Class<? extends CrawlConfig> crawlerConfigClass =
            CrawlConfig.class;

    @Default
    private final List<CrawlBootstrapper> bootstrappers =
            new ArrayList<>(List.of(
                    new CrawlEntryLedgerBootstrapper()));

    private final CrawlDocPipelines docPipelines;
    @Default
    private final CrawlCallbacks callbacks =
            CrawlCallbacks.builder().build();
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
     * The exact type of {@link CrawlEntry} if your crawler is subclassing
     * it. Defaults to {@link CrawlEntry} class.
     */
    @Default
    private final Class<? extends CrawlEntry> crawlEntryType =
            CrawlEntry.class;

    /**
     * Optional map of Hazelcast map-config-name (or wildcard pattern as
     * declared in the YAML, e.g. {@code "ledger_*"}) to the concrete Java
     * type that should be (de)serialized for values in that map.
     * The framework automatically adds the {@code ledger_*} mapping using
     * {@link #crawlEntryType}. Extensions that persist their own types should
     * add their entries here.
     */
    @Default
    private final Map<String, Class<?>> cacheTypes = new HashMap<>();

    @Accessors(fluent = true)
    @Data
    @NonNull
    public static class FetchDriver {
        private UnsuccessfulResponseFactory unsuccesfulResponseFactory;
        private ResponseAggregator responseAggregator;
    }
}
