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
import java.util.Optional;

import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapTask;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessTask;
import com.norconex.crawler.core.cmd.crawl.pipeline.process.CrawlProcessTask.ProcessQueueAction;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.Stage;

public final class CrawlPipelineFactory {

    private CrawlPipelineFactory() {
    }

    public static GridPipeline create(CrawlContext ctx) {
        return new GridPipeline("crawlPipeline", List.of(

                // Prepare for crawling (on one)
                new Stage(new CrawlBootstrapTask("crawlBootstrapTask"))
                        .withAlways(true),

                // Crawl (on all)
                new Stage(new CrawlProcessTask(
                        "crawlMainProcessTask", ProcessQueueAction.CRAWL_ALL)),

                // Resolve orphans (on one)
                new Stage(new CrawlHandleOrphansTask("crawlHandleOrphansTask")),

                // Crawl/delete orphans (on all)
                new Stage((grid, prev) -> Optional.ofNullable(prev.getResult())
                        .map(ProcessQueueAction.class::cast)
                        .map(action -> new CrawlProcessTask(
                                "crawlOrphanTask" + action, action))
                        .orElse(null))));
    }
}
