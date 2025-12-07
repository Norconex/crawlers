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

import com.norconex.crawler.core.CrawlConfig.OrphansStrategy;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.StartRefsQueueStep;
import com.norconex.crawler.core.cmd.crawl.pipeline.orphans.RequeueOrphansForDeletionStep;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessStep;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessStep.ProcessQueueAction;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultCrawlPipelineFactory implements CrawlPipelineFactory {

    //XXX     public static final String STEP_BOOTSTRAP = "bootstrap";
    public static final String STEP_INITIAL_QUEUE = "initialQueue";
    public static final String STEP_CRAWL_DOCUMENTS = "crawlDocuments";
    public static final String STEP_DELETE_ORPHANS = "deleteOrphans";
    public static final String STEP_CRAWL_ORPHANS = "crawlOrphans";

    @Override
    public Pipeline create(CrawlSession session) {
        var steps = new ArrayList<Step>();
        //XXX        steps.add(new CrawlBootstrapStep(STEP_BOOTSTRAP));

        steps.add(new StartRefsQueueStep(STEP_INITIAL_QUEUE)
                .setDistributed(false));

        steps.add(new CrawlProcessStep(
                STEP_CRAWL_DOCUMENTS, ProcessQueueAction.CRAWL_ALL)
                        .setDistributed(true));

        var orphStrategy =
                session.getCrawlContext().getCrawlConfig().getOrphansStrategy();
        if (orphStrategy == null || orphStrategy == OrphansStrategy.IGNORE) {
            LOG.info("Ignoring possible orphans as per orphan strategy.");
        } else if (orphStrategy == OrphansStrategy.DELETE) {
            steps.add(new RequeueOrphansForDeletionStep(
                    "queueOrphansForDeletion"));
            steps.add(new CrawlProcessStep(STEP_DELETE_ORPHANS,
                    ProcessQueueAction.DELETE_ALL)
                            .setDistributed(true));
        } else if (orphStrategy == OrphansStrategy.PROCESS) {
            steps.add(new RequeueOrphansForDeletionStep(
                    "queueOrphansForProcessing"));
            steps.add(new CrawlProcessStep(STEP_CRAWL_ORPHANS,
                    ProcessQueueAction.CRAWL_ALL)
                            .setDistributed(true));
        }
        return new Pipeline("crawlPipeline", steps);
    }
}
