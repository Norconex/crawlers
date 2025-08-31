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

import com.norconex.crawler.core.CrawlConfig.OrphansStrategy;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapStep;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessStep;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessStep.ProcessQueueAction;
import com.norconex.crawler.core.session.CrawlSession;

public final class CrawlPipelineFactory {

    private CrawlPipelineFactory() {
    }

    public static Pipeline create(CrawlSession session) {
        var steps = new ArrayList<Step>();
        steps.add(new CrawlBootstrapStep("bootstrap"));
        steps.add(new CrawlProcessStep("crawlDocuments",
                ProcessQueueAction.CRAWL_ALL)
                        .setDistributed(true));

        var orphStrategy = session.getCrawlContext().getCrawlConfig().getOrphansStrategy();
        if (orphStrategy == OrphansStrategy.DELETE) {
            // queue orphans for deletion
            // delete orphans (distrib)
//            new CrawlHandleOrphansPrepareStep("handleOrphansPrepare"),
//            new CrawlProcessStep("handleOrphansExecute",
//                    ProcessQueueAction.CRAWL_ALL)
//                            .setDistributed(true)
        } else if (orphStrategy == OrphansStrategy.PROCESS) {
            // queue orphans for crawling 
            // crawl orphans (distrib)
//            new CrawlHandleOrphansPrepareStep("handleOrphansPrepare"),
//            new CrawlProcessStep("handleOrphansExecute",
//                    ProcessQueueAction.CRAWL_ALL)
        }
        return new Pipeline("crawlPipeline", steps);
        // step: process orphans
        // step: cleanup?

        /*
        return () -> {
            var taskManager = session.getCluster().getTaskManager();

            // Bootstrap the cluster, making it ready for crawling (once per session)
        DONE: taskManager.runOnOneOnceSync(
                    "crawlBootstrapTask", new CrawlBootstrapTask());

            // Start main continuous crawl across all nodes
        DONE: taskManager.startContinuous(
                    "crawlMainProcess",
                    new CrawlProcessContinuousWorker(
                            ProcessQueueAction.CRAWL_ALL));
            // Wait for completion (auto or explicit stop)
            taskManager.awaitContinuousCompletion("crawlMainProcess").join();

            // Resolve orphans (on one)
        DONE    var orphanActionOpt = taskManager.runOnOneOnceSync(
                    "crawlHandleOrphansTask",
                    new CrawlHandleOrphansTask());

            orphanActionOpt.ifPresent(action -> {
                // Process orphans using another continuous phase so late joiners can still help
                taskManager.startContinuous(
                        "crawlOrphansProcess" + action,
                        new CrawlProcessContinuousWorker(action));
                taskManager.awaitContinuousCompletion(
                        "crawlOrphansProcess" + action).join();
            });
        };
        */
    }
    //    public static GridPipeline create(CrawlContext ctx) {
    //        return new GridPipeline("crawlPipeline", List.of(
    //
    //                // Prepare for crawling (on one)
    //                new Stage(new CrawlBootstrapTask("crawlBootstrapTask"))
    //                        .withAlways(true),
    //
    //                // Crawl (on all)
    //                new Stage(new CrawlProcessTask(
    //                        "crawlMainProcessTask", ProcessQueueAction.CRAWL_ALL)),
    //
    //                // Resolve orphans (on one)
    //                new Stage(new CrawlHandleOrphansTask("crawlHandleOrphansTask")),
    //
    //                // Crawl/delete orphans (on all)
    //                new Stage((grid, prev) -> Optional.ofNullable(prev.getResult())
    //                        .map(ProcessQueueAction.class::cast)
    //                        .map(action -> new CrawlProcessTask(
    //                                "crawlOrphanTask" + action, action))
    //                        .orElse(null))));
    //    }
}
