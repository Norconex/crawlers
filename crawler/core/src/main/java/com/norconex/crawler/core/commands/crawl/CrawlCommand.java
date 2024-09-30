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
package com.norconex.crawler.core.commands.crawl;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.commands.Command;
import com.norconex.crawler.core.doc.process.DocProcessingLedger;
import com.norconex.crawler.core.grid.GridTxOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlCommand implements Command {

    //TODO have a general life-cycle facade or procesing-state facade
    // where all the global keys can be found.  Or is the
    // DocProcessingLedger already for that?
    public static final String KEY_START_REFS_QUEUED =
            "crawl.start.refs.queued";

    @Override
    public void execute(Crawler crawler) {
        var cfg = crawler.getConfiguration();
        var grid = crawler.getGridSystem();

        LOG.info("Preparing document processing ledger for crawling...");
        grid.getCompute().runTask(ResetDocLedgerTask.class, "RUN",
                GridTxOptions.builder()
                        .name("reset-ledger")
                        .block(true)
                        .singleton(true)
                        .build());

        if (Boolean.parseBoolean(
                grid.getGlobalCache().get(DocProcessingLedger.KEY_RESUMING))) {
            LOG.info("Unfinished previous crawl detected. Resuming...");
        } else {
            LOG.info("Queuing start references ({})...",
                    cfg.isStartReferencesAsync() ? "async" : "");
            grid.getCompute().runTask(QueueStartReferencesTask.class, "RUN",
                    GridTxOptions.builder()
                            .name("queue-start-refs")
                            .block(!cfg.isStartReferencesAsync())
                            .singleton(true)
                            .build());
        }
    }
}
