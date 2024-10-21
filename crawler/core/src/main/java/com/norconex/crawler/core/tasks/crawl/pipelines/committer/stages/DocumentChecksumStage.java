/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.pipelines.committer.stages;

import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.crawl.pipelines.ChecksumStageUtil;
import com.norconex.crawler.core.tasks.crawl.pipelines.committer.CommitterPipelineContext;

/**
 * Common pipeline stage for creating a document checksum.
 *
 */
public class DocumentChecksumStage
        implements Predicate<CommitterPipelineContext> {

    @Override
    public boolean test(CommitterPipelineContext ctx) {
        var checksummer =
                ctx.getCrawler().getConfiguration().getDocumentChecksummer();
        var docContext = ctx.getDoc().getDocContext();

        // if there are no checksum defined and state is not new/modified,
        // we treat all docs as new.
        if (checksummer == null) {
            if (docContext.getState() == null
                    || !docContext.getState().isNewOrModified()) {
                // NEW is default state (?)
                docContext.setState(CrawlDocState.NEW);
            }
            return true;
        }
        var newDocChecksum = checksummer.createDocumentChecksum(ctx.getDoc());

        var accepted = ChecksumStageUtil.resolveDocumentChecksum(
                newDocChecksum, ctx.getDoc());
        if (!accepted) {
            var s = new StringBuilder()
                    .append(checksummer.getClass().getSimpleName())
                    .append(" - ")
                    .append("Checksum=")
                    .append(StringUtils.abbreviate(newDocChecksum, 200));
            ctx.getCrawler().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_UNMODIFIED)
                            .source(ctx.getCrawler())
                            .docContext(ctx.getDoc().getDocContext())
                            .subject(checksummer)
                            .message(s.toString())
                            .build());
        }
        return accepted;
    }
}