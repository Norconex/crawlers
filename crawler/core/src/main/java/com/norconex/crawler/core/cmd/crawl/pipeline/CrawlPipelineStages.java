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

import java.util.List;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapTask;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessTask;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessTask.ProcessQueueAction;
import com.norconex.grid.core.compute.GridCompute.RunOn;
import com.norconex.grid.core.pipeline.GridPipelineStage;

public final class CrawlPipelineStages {

    private CrawlPipelineStages() {
    }

    public static List<? extends GridPipelineStage<CrawlerContext>>
            create() {

        return List.of(
                // Init
                GridPipelineStage.<CrawlerContext>builder()
                        .name("crawlInitStage")
                        .runOn(RunOn.ONE)
                        .task(new CrawlBootstrapTask())
                        .always(true)
                        .build(),
                // Main crawl
                GridPipelineStage.<CrawlerContext>builder()
                        .name("crawlProcessStage")
                        .runOn(RunOn.ALL)
                        .task(new CrawlProcessTask(
                                ProcessQueueAction.CRAWL_ALL))
                        .build(),
                // Handle orphans
                GridPipelineStage.<CrawlerContext>builder()
                        .name("crawlOrphansStage")
                        .runOn(RunOn.ALL)
                        .task(new CrawlHandleOrphansTask())
                        .build()

        //                // Shutdown
        //                GridPipelineStage.<CrawlerContext>builder()
        //                        .name("crawlShutdownStage")
        //                        .runOn(RunOn.ALL)
        //                        .task(new CrawlShutdownTask())
        //                        .build(),
        );
    }

}
