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
package com.norconex.crawler.core.tasks.crawl.process;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.crawler.core.CrawlerConfig.OrphansStrategy;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.tasks.crawl.process.DocsProcessor.ProcessFlags;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class OrphanDocsProcessor {

    private final CrawlerContext crawlerContext;
    private final DocsProcessor docProcessor;
    private final DocProcessingLedger ledger;

    OrphanDocsProcessor(DocsProcessor docProcessor) {
        this.docProcessor = docProcessor;
        crawlerContext = docProcessor.getCrawlerContext();
        ledger = crawlerContext.getDocProcessingLedger();
    }

    void handleOrphans() {

        var strategy = crawlerContext.getConfiguration().getOrphansStrategy();
        if (strategy == null) {
            // null is same as ignore
            strategy = OrphansStrategy.IGNORE;
        }

        // If PROCESS, we do not care to validate if really orphan since
        // all cache items will be reprocessed regardless
        if (strategy == OrphansStrategy.PROCESS) {
            reprocessCacheOrphans();
            return;
        }

        if (strategy == OrphansStrategy.DELETE) {
            deleteCacheOrphans();
        }
        // else, ignore (i.e. don't do anything)
        //TODO log how many where ignored (cache count)
    }

    void reprocessCacheOrphans() {
        if (docProcessor.isMaxDocsReached()) {
            LOG.info("Max documents reached. "
                    + "Not reprocessing orphans (if any).");
            return;
        }
        LOG.info("Reprocessing any cached/orphan references...");

        var count = new MutableLong();
        crawlerContext.getDocProcessingLedger()
                .forEachCached((ref, docInfo) -> {
                    docProcessor
                            .getCrawlerContext()
                            .getDocPipelines()
                            .getQueuePipeline()
                            .accept(new QueuePipelineContext(
                                    docProcessor.getCrawlerContext(),
                                    docInfo));
                    count.increment();
                    return true;
                });

        if (count.longValue() > 0) {
            docProcessor.crawlReferences(new ProcessFlags().orphan());
        }
        LOG.info("Reprocessed {} cached/orphan references.", count);
    }

    void deleteCacheOrphans() {
        LOG.info("Deleting orphan references (if any)...");

        var count = new MutableLong();

        ledger.forEachCached((k, v) -> {
            ledger.queue(v);
            count.increment();
            return true;
        });
        if (count.longValue() > 0) {
            docProcessor.crawlReferences(new ProcessFlags().delete());
        }
        LOG.info("Deleted {} orphan references.", count);
    }
}