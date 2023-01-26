/* Copyright 2022-2023 Norconex Inc.
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
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.doc.CrawlDocRecordFactory;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.pipeline.committer.CommitterPipeline;
import com.norconex.crawler.core.pipeline.importer.ImporterPipeline;
import com.norconex.crawler.core.pipeline.queue.QueuePipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Inner workings specific to a given crawler implementation. Not
 * for general use, and not meant to be configured <i>directly</i> at runtime.
 * </p>
 */
@Builder
@Getter
@Accessors(fluent = true)
public class CrawlerImpl {

    /**
     * Provides a required fetcher implementation, responsible for obtaining
     * resources being crawled.
     * @param fetcherProvider fetcher provider function
     * @return a function returning a fetcher to associate with a given crawler.
     */
    @SuppressWarnings("javadoc")
    @NonNull
    Function<Crawler, Fetcher<FetchRequest, FetchResponse>> fetcherProvider;

    /**
     * The exact type of {@link CrawlDocRecord} if your crawler is subclassing
     * it. Defaults to {@link CrawlDocRecord} class.
     * @param crawlDocRecordType crawl document record class
     * @return crawl document record class
     */
    @SuppressWarnings("javadoc")
    @Default
    @NonNull
    //TODO Needed since we also have CrawlDocRecord Factory? keep only one?
    Class<? extends CrawlDocRecord> crawlDocRecordType = CrawlDocRecord.class;

    /**
     * Required pipeline to be executed for each reference that should
     * end-up in the crawler queue.
     * @param queuePipeline queue pipeline
     * @return queue pipeline
     */
    @SuppressWarnings("javadoc")
    @NonNull
    QueuePipeline queuePipeline;

    /**
     * Required pipeline to be executed for each reference read
     * from the crawler queue up to the importing of the corresponding
     * document.
     * @param importerPipeline importer pipeline
     * @return importer pipeline
     */
    @SuppressWarnings("javadoc")
    @NonNull
    ImporterPipeline importerPipeline;

    /**
     * Required pipeline to be executed for each document that has been
     * imported and are ready to be committed for insertion/update or
     * deletion.
     * @param committerPipeline committer pipeline
     * @return committer pipeline
     */
    @SuppressWarnings("javadoc")
    @NonNull
    CommitterPipeline committerPipeline;

    /**
     * Holds contextual objects necessary to initialize a crawler queue.
     */
    @Accessors(fluent = false)
    @AllArgsConstructor
    public static class QueueInitContext {
        @Getter
        private final Crawler crawler;
        @Getter
        private final boolean resuming;
        private final Consumer<CrawlDocRecord> queuer;
        public void queue(CrawlDocRecord rec) {
            queuer.accept(rec);
        }
    }

    /**
     * Function responsible for initializing a queue, which typically involved
     * inserting the initial references necessary to start crawling.
     * The function's return value indicate if we are done initializing
     * the queue.  Should always be <code>true</code> unless
     * the queue can be initialized asynchronously. In such case,
     * the mutable boolean can be returned right away, but must be set to
     * <code>true</code> when initialization is complete.
     * @param queueInitializer queue initializer function
     * @return queue initializer function
     */
    @SuppressWarnings("javadoc")
    Function<QueueInitContext, MutableBoolean> queueInitializer;

    /**
     * Holds contextual objects necessary to create new document records.
     */
    @Builder
    @Getter
    public static class DocRecordFactoryContext {
        private final String reference;
        private final CrawlDocRecord parentDocRecord;
        private final CrawlDocRecord cachedDocRecord;
    }

    /**
     * Function responsible for creating new document records
     * specific to this crawler implementation. The default factory
     * creates instances of {@link CrawlDocRecord} initialized with
     * a possible parent document record.
     * @param docRecordFactory factory function for creating doc records
     * @return factory function for creating doc records
     */
    @SuppressWarnings("javadoc")
    @NonNull
    @Default
    CrawlDocRecordFactory docRecordFactory =
            ctx -> new CrawlDocRecord(ctx.parentDocRecord);

    //TODO crawlerConfig needed here?
//    /**
//     * Required crawler configuration.
//     * @param crawlerConfig crawler configuration
//     * @return crawler configuration
//     */
//    @SuppressWarnings("javadoc")
//    @NonNull
//    CrawlerConfig crawlerConfig;

    /**
     * Gives crawler implementations a chance to prepare before execution
     * starts. Invoked right after the
     * {@link CrawlerEvent#CRAWLER_RUN_BEGIN} event is fired.
     * This method is different than the {@link #initCrawler()} method,
     * which is invoked for any type of actions where as this one is only
     * invoked before an effective request for crawling.
     * @param beforeCrawlerExecution bi-consumer accepting a crawler and
     *     a "resume" indicator.
     * @return bi-consumer accepting a crawler and a "resume" indicator
     */
    @SuppressWarnings("javadoc")
    BiConsumer<Crawler, Boolean> beforeCrawlerExecution;

    /**
     * Gives crawler implementations a chance to do something right after
     * the crawler is done processing its last reference, before all resources
     * are shut down.
     * Invoked right after {@link CrawlerEvent#CRAWLER_STOP_END} or
     * {@link CrawlerEvent#CRAWLER_RUN_END} (depending which of the two is
     * triggered).
     * @param afterCrawlerExecution consumer accepting a crawler
     * @return consumer accepting a crawler
     */
    @SuppressWarnings("javadoc")
    Consumer<Crawler> afterCrawlerExecution;



    //TODO are those used? Should they be?
    // Add those that are missing to ReferencesProcessor
    BiConsumer<Crawler, CrawlDoc> beforeDocumentProcessing;
    BiConsumer<Crawler, CrawlDoc> afterDocumentProcessing;

    // need those, or we can replace beforeDocumentFinalizing
    // (the only one used) with after processing?
    BiConsumer<Crawler, CrawlDoc> beforeDocumentFinalizing;
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
