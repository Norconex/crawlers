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
package com.norconex.crawler.core.client.commands;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.grid.GridTxOptions;
import com.norconex.crawler.core.tasks.impl.CrawlTask;
import com.norconex.crawler.core.tasks.impl.PrepareDocLedgerTask;
import com.norconex.crawler.core.tasks.impl.QueueStartReferencesTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlCommand implements Command {

    //TODO have a general life-cycle facade or procesing-state facade
    // where all the global keys can be found.  Or is the
    // DocProcessingLedger already for that?
    public static final String KEY_START_REFS_QUEUED =
            "crawl.start.refs.queued";

    @Override
    public void execute(Crawler crawlerClient) {
        prepareDocLedger(crawlerClient);
        queueStartReferences(crawlerClient);
        crawlDocs(crawlerClient);
        //Needed? -->        finalizeExecution(crawlerClient);
        //   Yes: if only to have each nodes call "close()" on themselves.
    }

    // On 1 node, block
    private void prepareDocLedger(Crawler crawlerClient) {
        LOG.info("Preparing document processing ledger for crawling...");
        crawlerClient.getGrid().compute().runTask(
                PrepareDocLedgerTask.class,
                null,
                GridTxOptions.builder()
                        .name("reset-ledger")
                        .block(true)
                        .singleton(true)
                        .build());
    }

    // On 1 node, block unless config says async
    private void queueStartReferences(Crawler crawlerClient) {
        var cfg = crawlerClient.getConfiguration();
        var grid = crawlerClient.getGrid();
        if (crawlerClient.getState().isResuming()) {
            LOG.info("Unfinished previous crawl detected. Resuming...");
        } else {
            LOG.info("Queuing start references ({})...",
                    cfg.isStartReferencesAsync() ? "async" : "");
            grid.compute().runTask(
                    QueueStartReferencesTask.class,
                    null,
                    GridTxOptions.builder()
                            .name("queue-start-refs")
                            .block(!cfg.isStartReferencesAsync())
                            .singleton(true)
                            .build());
        }
    }

    // On all nodes, block
    private void crawlDocs(Crawler crawlerClient) {
        LOG.info("Crawling...");
        crawlerClient.getGrid().compute().runTask(CrawlTask.class, null,
                GridTxOptions.builder()
                        .name("crawl")
                        .block(true)
                        .build());
    }

    //    private void finalizeExecution(Crawler crawlerClient) {
    //        // TODO Auto-generated method stub
    //
    //    }

    //    // On all nodes, block
    //    private void handleOrphanDocs(CrawlerClient crawlerClient) {
    //        LOG.warn("TODO: Implement me");
    //    }
}
