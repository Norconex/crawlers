/* Copyright 2022-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.fetch.IFetchRequest;
import com.norconex.crawler.core.fetch.IFetchResponse;
import com.norconex.crawler.core.fetch.IFetcher;
import com.norconex.crawler.core.pipeline.DocInfoPipelineContext;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.importer.response.ImporterResponse;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

//Name could be: CrawlerDefinition, CrawlerInitializer, CrawlerStrategies
// CrawlerFactory, CrawlerAssets, CrawlerSettings
// CrawlerStrategyProvider, CrawlerImplementation, CrawlerExtensions

//CrawlerBuilder?  CrawlerFlavor?  CrawlerConnector

//@Data
//@Setter
//@Getter
//@Accessors(fluent = true)

//Maybe have a builder directly on Crawler instead?

/**
* Inner workings specific to a given crawler implementation. Not meant
* to be configured <i>directly</i> at runtime.
*/
@Builder
@Getter
@Accessors(fluent = true)
// maybe have better naming?
public class CrawlerImpl {

    //--- Required -------------------------------------------------------------

    @NonNull
    Function<Crawler, IFetcher<IFetchRequest, IFetchResponse>> fetcherProvider;

    //TODO needed?
    @Default
    @NonNull
    Class<? extends CrawlDocRecord> crawlDocRecordType = CrawlDocRecord.class;

    //--- Optional/per implementation ------------------------------------------



//     IFetcher<?, ?> fetcher;
    // required
    //TODO maybe use DocRecord#withReference instead?
//    Function<Crawler, CrawlDocRecord> crawlDocInfoCloner;

    @NonNull
    BiFunction<Crawler, Boolean, MutableBoolean> queueInitializer;

    // required
    @NonNull
    Consumer<DocInfoPipelineContext> queuePipelineExecutor;  //TODO Rename queueExecutor ?
    // required
    @NonNull
    Function<ImporterPipelineContext, ImporterResponse>
            importerPipelineExecutor;  //TODO Rename importExecutor ?
    // required
    @NonNull
    BiConsumer<Crawler, CrawlDoc> committerPipelineExecutor;  //TODO Rename commitExecutor ?
    // required (unless we use reflection? is there value in controlling the child creation per-crawler?)


    //TODO pass equivalent of RecordCreatorContext which holds
    // - parent doc record
    // - whether parent doc record is from being embedded
    // - cached doc record, if any

    @Builder
    @Getter
    public static class DocRecordFactoryContext {
        private final String reference;
        private final CrawlDocRecord parentDocRecord;
        private final CrawlDocRecord cachedDocRecord;
    }

    @NonNull
    @Default
    Function<DocRecordFactoryContext, CrawlDocRecord> docRecordFactory =
            ctx -> new CrawlDocRecord(ctx.parentDocRecord);


    // required
    @NonNull
    CrawlerConfig crawlerConfig;

    // optional
    /**
     * Gives crawler implementations a chance to prepare before execution
     * starts. Invoked right after the
     * {@link CrawlerEvent#CRAWLER_RUN_BEGIN} event is fired.
     * This method is different than the {@link #initCrawler()} method,
     * which is invoked for any type of actions where as this one is only
     * invoked before an effective request for crawling.
     * -- SETTER --
     * @param beforeCrawlerExecution bi-consumer accepting a crawler and
     *     a "resume" indicator session.
     * -- GETTER --
     * @return bi-consumer accepting a crawler and a "resume" indicator
     */
    BiConsumer<Crawler, Boolean> beforeCrawlerExecution;
    // optional
    /**
     * Gives crawler implementations a chance to do something right after
     * the crawler is done processing its last reference, before all resources
     * are shut down.
     * Invoked right after {@link CrawlerEvent#CRAWLER_STOP_END} or
     * {@link CrawlerEvent#CRAWLER_RUN_END} (depending which of the two is
     * triggered).
     * -- SETTER --
     * @param afterCrawlerExecution consumer accepting a crawler
     * -- GETTER --
     * @return consumer accepting a crawler
     */
    Consumer<Crawler> afterCrawlerExecution;
    // optional
    BiConsumer<Crawler, CrawlDoc> beforeDocumentProcessing;
    // optional
    BiConsumer<Crawler, CrawlDoc> afterDocumentProcessing;

    // optional
    BiConsumer<Crawler, CrawlDoc> beforeDocumentFinalizing;
    // optional
    BiConsumer<Crawler, CrawlDoc> afterDocumentFinalizing;



//        protected abstract void markReferenceVariationsAsProcessed(
//                CrawlDocRecord crawlRef);
//
//
//        protected abstract CrawlDocRecord createChildDocInfo(
//                String embeddedReference, CrawlDocRecord parentCrawlRef);
//
//        protected abstract ImporterResponse executeImporterPipeline(
//                ImporterPipelineContext context);
//        //TODO, replace with DocumentPipelineContext?
//        protected abstract void executeCommitterPipeline(
//                Crawler crawler, CrawlDoc doc);

//        private Builder() {}
//
//        public Crawler build(@NonNull Collector collector) {
//            //TODO validate mandatories are there (or validate in crawler constructor
//
//            return new Crawler(collector, this);
//        }
}
