/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages;

import com.norconex.crawler.core.cmd.crawl.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.WebImporterPipelineContext;
import com.norconex.crawler.web.event.WebCrawlerEvent;

/**
 * Robots Meta NoIndex Check.
 */
public class RobotsMetaNoIndexStage extends AbstractImporterStage {

    @Override
    protected boolean executeStage(ImporterPipelineContext context) {
        var ctx = (WebImporterPipelineContext) context;
        var canIndex = ctx.getRobotsMeta() == null
                || !ctx.getRobotsMeta().isNoindex();
        if (!canIndex) {
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(WebCrawlerEvent.REJECTED_ROBOTS_META_NOINDEX)
                            .source(ctx.getCrawlerContext())
                            .subject(ctx.getRobotsMeta())
                            .docContext(ctx.getDoc().getDocContext())
                            .build());
            ctx.getDoc().getDocContext().setState(DocResolutionStatus.REJECTED);
            return false;
        }
        return canIndex;
    }
}