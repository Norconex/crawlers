/* Copyright 2016-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.importer.stages;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>Determines whether to recrawl a document or not.</p>
 * <p>Only does so if the document is not an orphan.</p>
 * @since 2.5.0
 */
@Slf4j
public class RecrawlableResolverStage extends AbstractImporterStage {

    @Override
    protected boolean executeStage(ImporterPipelineContext pipeCtx) {
        // skip if doc is an orphan
        if (pipeCtx.getDoc().isOrphan()) {
            return true;
        }

        var crawlerCtx = pipeCtx.getCrawlerContext();
        var rr = Web.config(crawlerCtx).getRecrawlableResolver();
        if (rr == null) {
            // no resolver means we process it.
            return true;
        }

        var cachedDocContext = Web.cachedDocContext(pipeCtx.getDoc());
        if (cachedDocContext == null) {
            // this document was not previously crawled so process it.
            return true;
        }

        var currentDocContext = pipeCtx.getDoc().getDocContext();

        var isRecrawlable = rr.isRecrawlable(cachedDocContext);
        if (!isRecrawlable) {
            LOG.debug("{} is not ready to be recrawled, skipping it.",
                    cachedDocContext.getReference());
            crawlerCtx.fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_PREMATURE)
                            .source(crawlerCtx)
                            .subject(rr)
                            .docContext(currentDocContext)
                            .build());
            currentDocContext.setState(CrawlDocStatus.PREMATURE);

            // If the URL was redirected (as per cache) and the redirect URL
            // target has not been processed already (still in cache),
            // re-queue the redirect URL target or it may be wrongfully
            // considered orphan if not referenced somewhere else during the
            // crawl.
            if (StringUtils.isNotBlank(cachedDocContext.getRedirectTarget())) {
                crawlerCtx.getDocLedger()
                        .getCached(cachedDocContext.getRedirectTarget())
                        .ifPresent(targetDocInfo -> crawlerCtx
                                .getPipelines()
                                .getQueuePipeline()
                                .accept(new QueuePipelineContext(
                                        crawlerCtx, targetDocInfo)));
            }
        }
        return isRecrawlable;
    }
}
