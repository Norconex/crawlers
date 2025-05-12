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
package com.norconex.crawler.core.cmd.crawl.pipeline;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.crawler.core.CrawlConfig.OrphansStrategy;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.BaseGridTask.SingleNodeTask;

import lombok.extern.slf4j.Slf4j;

/**
 * Queue orphans for reprocessing or deletion.
 */
@Slf4j
public class CrawlHandleOrphansTask extends SingleNodeTask {

    private static final long serialVersionUID = 1L;

    public CrawlHandleOrphansTask(String id) {
        super(id);
    }

    @Override
    public void process(Grid grid) {
        var ctx = CrawlContext.get(grid);

        var strategy = ctx.getCrawlConfig().getOrphansStrategy();
        if (strategy == null || strategy == OrphansStrategy.IGNORE) {
            LOG.info("Ignoring possible orphans as per orphan strategy.");
            return;
        }
        var orphanCount = ctx.getDocLedger().getCachedCount();
        if (orphanCount == 0) {
            LOG.info("There are no orphans to process.");
            return;
        }

        // If PROCESS, we do not care to validate if really orphan since
        // all cache items will be reprocessed regardless
        if (strategy == OrphansStrategy.PROCESS) {
            queueForProcessing(ctx);
        } else if (strategy == OrphansStrategy.DELETE) {
            queueForDeletion(ctx);
        }
    }

    void queueForProcessing(CrawlContext ctx) {
        if (ctx.getDocLedger().isMaxDocsProcessedReached()) {
            LOG.info("""
                Max documents reached. \
                Not reprocessing orphans (if any). \
                Run the crawler again to resume.""");
            return;
        }
        LOG.info("Queueing orphan references for processing...");
        var count = new MutableLong();
        ctx.getDocLedger().forEachCached((ref, docCtx) -> {
            docCtx.setOrphan(true);
            ctx.getDocPipelines()
                    .getQueuePipeline()
                    .accept(new QueuePipelineContext(ctx, docCtx));
            count.increment();
            return true;
        });

        //        LOG.info("Reprocessing {} orphan references...", count);
        //        new CrawlProcessTask(ProcessQueueAction.CRAWL_ALL).execute(ctx);
        //        LOG.info("Reprocessed {} cached/orphan references.", count);
    }

    void queueForDeletion(CrawlContext ctx) {
        LOG.info("Queueing orphan references for deletion...");

        var count = new MutableLong();

        ctx.getDocLedger().forEachCached((k, docCtx) -> {
            docCtx.setDeleted(true);
            ctx.getDocLedger().queue(docCtx);
            count.increment();
            return true;
        });
        //        LOG.info("Deleting {} orphan references...", count);
        //        new CrawlProcessTask(ProcessQueueAction.DELETE_ALL).execute(ctx);
        //        LOG.info("Deleted {} orphan references.", count);
    }
}
