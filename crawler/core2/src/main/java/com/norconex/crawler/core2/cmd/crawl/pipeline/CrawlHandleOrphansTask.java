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
package com.norconex.crawler.core2.cmd.crawl.pipeline;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.crawler.core2.CrawlConfig.OrphansStrategy;
import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.cmd.crawl.pipeline.process.CrawlProcessTask.ProcessQueueAction;
import com.norconex.crawler.core2.context.CrawlContext;
import com.norconex.crawler.core2.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Queue orphans for reprocessing or deletion.
 */
@Slf4j
public class CrawlHandleOrphansTask implements ClusterTask<ProcessQueueAction> {

    // returns the type of processing we do on orphans, or null if there
    // is nothing to do
    @Override
    public ProcessQueueAction execute(CrawlSession session) {
        var crawlCtx = session.getCrawlContext();

        var strategy = crawlCtx.getCrawlConfig().getOrphansStrategy();
        if (strategy == null || strategy == OrphansStrategy.IGNORE) {
            LOG.info("Ignoring possible orphans as per orphan strategy.");
            return null;
        }
        var orphanCount =
                crawlCtx.getCrawlEntryLedger().getPreviousEntryCount();
        if (orphanCount == 0) {
            LOG.info("There are no orphans to process.");
            return null;
        }

        // If PROCESS, we do not care to validate if really orphan since
        // all cache items will be reprocessed regardless
        if (strategy == OrphansStrategy.PROCESS) {
            queueForProcessing(session);
            return ProcessQueueAction.CRAWL_ALL;
        }
        if (strategy == OrphansStrategy.DELETE) {
            queueForDeletion(crawlCtx);
            return ProcessQueueAction.DELETE_ALL;
        }
        return null;
    }

    void queueForProcessing(CrawlSession session) {
        var crawlCtx = session.getCrawlContext();
        if (crawlCtx.getCrawlEntryLedger().isMaxDocsProcessedReached()) {
            LOG.info("""
                Max documents reached. \
                Not reprocessing orphans (if any). \
                Run the crawler again to resume.""");
            return;
        }
        LOG.info("Queueing orphan references for processing...");
        var count = new MutableLong();
        crawlCtx.getCrawlEntryLedger().forEachPrevious(entry -> {
            entry.setOrphan(true);
            crawlCtx.getDocPipelines()
                    .getQueuePipeline()
                    .accept(new QueuePipelineContext(session, entry));
            count.increment();
        });

        LOG.info("{} orphan references queued for processing.", count);
    }

    void queueForDeletion(CrawlContext ctx) {
        LOG.info("Queueing orphan references for deletion...");

        var count = new MutableLong();
        ctx.getCrawlEntryLedger().forEachPrevious(entry -> {
            entry.setDeleted(true);
            ctx.getCrawlEntryLedger().queue(entry);
            count.increment();
        });
        LOG.info("{} orphan references queued for deletion.", count);
    }
}
