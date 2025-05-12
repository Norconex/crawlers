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

import java.util.ArrayList;
import java.util.List;

import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapTask;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessTask;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessTask.ProcessQueueAction;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridPipeline.Stage;

public final class CrawlPipelineFactory {

    private CrawlPipelineFactory() {
    }

    public static GridPipeline create(CrawlContext ctx) {
        List<Stage> stages = new ArrayList<>(List.of(
                new Stage(new CrawlBootstrapTask("crawlBootstrapTask"))
                        .withAlways(true),
                new Stage(new CrawlProcessTask(
                        "crawlMainProcessTask",
                        ProcessQueueAction.CRAWL_ALL)),
                new Stage(
                        new CrawlHandleOrphansTask("crawlHandleOrphansTask"))));

        switch (ctx.getCrawlConfig().getOrphansStrategy()) {
            case PROCESS: {
                stages.add(new Stage(new CrawlProcessTask(
                        "crawlOrphanProcessTask",
                        ProcessQueueAction.CRAWL_ALL)));
            }
            case DELETE: {
                stages.add(
                        new Stage(new CrawlProcessTask(
                                "crawlOrphanDeleteTask",
                                ProcessQueueAction.DELETE_ALL)));
            }
            default: // NOOP
        }
        return new GridPipeline("crawlPipeline", stages);

        //        return GridPipeline.of("crawlPipeline",
        //                new Stage(new CrawlBootstrapTask("crawlBootstrapTask"))
        //                        .withAlways(true),
        //                new Stage(new CrawlProcessTask(
        //                        "crawlMainProcessTask",
        //                        ProcessQueueAction.CRAWL_ALL)),
        //                new Stage(new CrawlHandleOrphansTask("crawlHandleOrphansTask")),
        //                new Stage(new CrawlProcessTask(
        //                        "crawlOrphanProcessTask",
        //                        ProcessQueueAction.CRAWL_ALL)).withOnlyIf(
        //                                grid -> strategy == OrphansStrategy.PROCESS),
        //                new Stage(new CrawlProcessTask(
        //                        "crawlOrphanDeleteTask",
        //                        ProcessQueueAction.DELETE_ALL)).withOnlyIf(
        //                                grid -> strategy == OrphansStrategy.DELETE)
        //        //              new CrawlShutdownTask()
        //
        //        );
    }

}
