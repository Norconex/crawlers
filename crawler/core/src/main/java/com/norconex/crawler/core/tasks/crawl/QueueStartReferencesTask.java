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
package com.norconex.crawler.core.tasks.crawl;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridTask;

/**
 * Prepare the doc processing ledger for a new or incremental crawl.
 * Takes care of caching processed documents on previously completed crawls
 * or simply logs the current progress if resuming from an incomplete
 * previous crawl.
 */
public class QueueStartReferencesTask implements GridTask {

    private static final long serialVersionUID = 1L;

    @Override
    public void run(CrawlerContext crawlerContext, String arg) {
        crawlerContext.getGrid().storage().getGlobalCache();
        //    globalCache.put(CrawlCommand.KEY_START_REFS_QUEUED, "false");
        crawlerContext.getDocPipelines()
                .getQueuePipeline()
                .initializeQueue(crawlerContext);
        //    globalCache.put(CrawlCommand.KEY_START_REFS_QUEUED, "true");
    }
}
