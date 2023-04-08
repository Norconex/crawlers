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
package com.norconex.crawler.web.crawler.impl;

import java.util.List;

import com.norconex.crawler.core.crawler.CoreQueueInitializer;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.web.crawler.WebCrawlerContext;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;
import com.norconex.crawler.web.pipeline.committer.WebCommitterPipeline;
import com.norconex.crawler.web.pipeline.importer.WebImporterPipeline;
import com.norconex.crawler.web.pipeline.queue.WebQueuePipeline;

public class WebCrawlerImplFactory {
    public static CrawlerImpl create() {
        return CrawlerImpl.builder()
                //TODO make fetcher*s* part of crawler-core CONFIG instead?
                .crawlerImplContext(WebCrawlerContext::new)
                .fetcherProvider(new HttpFetcherProvider())
                .beforeCrawlerExecution(new BeforeWebCrawlerExecution())
                .queueInitializer(new CoreQueueInitializer(List.of(
                        new SitemapQueueInitializer(),
                        CoreQueueInitializer.fromList,
                        CoreQueueInitializer.fromFiles,
                        CoreQueueInitializer.fromProviders
                        )))
                .queuePipeline(new WebQueuePipeline())
                .importerPipeline(new WebImporterPipeline())
                .committerPipeline(new WebCommitterPipeline())
                .beforeDocumentProcessing(new WebCrawlDocInitializer())
                .beforeDocumentFinalizing(new BeforeWebCrawlDocFinalizing())

                // Needed??
                .crawlDocRecordType(WebDocRecord.class)
                .docRecordFactory(ctx -> new WebDocRecord(
                        ctx.reference()
                        //TODO What about depth, cached doc, etc? It should be
                        // set here unless this is really used just for queue
                        // initialization or set by caller
                        //, 999

                        ))
                .build();
    }
}
