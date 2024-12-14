/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipelines.committer.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.cmd.crawl.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Provided both document checksum and deduplication are enabled,
 * verify there are no other documents with the same content checksum
 * encountered in the same crawl session.
 * This is not 100% fool-proof due to concurrency but should capture
 * the vast majority of duplicates.
 */
@Slf4j
public class DocumentDedupStage implements Predicate<CommitterPipelineContext> {

    @Override
    public boolean test(CommitterPipelineContext ctx) {
        var docContext = ctx.getDoc().getDocContext();

        var dedupService = ctx.getCrawlerContext().getDedupService();

        var duplRef = dedupService.findOrTrackDocument(docContext);
        if (duplRef.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "REJECTED duplicate content checkum found for: {}",
                        docContext.getReference());
            }
            docContext.setState(DocResolutionStatus.REJECTED);
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_DUPLICATE)
                            .source(ctx.getCrawlerContext())
                            .subject(duplRef.get())
                            .docContext(ctx.getDoc().getDocContext())
                            .message("A document with the same content "
                                    + "checksum was already processed: "
                                    + duplRef.get())
                            .build());
            return false;
        }
        return true;
    }
}
