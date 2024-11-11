/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.pipelines.importer.stages;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.tasks.crawl.pipelines.ChecksumStageUtil;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipelineContext;

import lombok.NonNull;

/**
 * Creates a checksum based on fetched metadata. Invoked only
 * once (even if both metadata and document directives are enabled).
 */
public class MetadataChecksumStage extends AbstractImporterStage {

    public MetadataChecksumStage(@NonNull FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        //TODO only if an INCREMENTAL run... else skip.
        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())
                || ctx.isMetadataDirectiveExecuted(getFetchDirective())) {
            return true;
        }

        var check = ctx.getCrawlerContext().getConfiguration()
                .getMetadataChecksummer();
        if (check == null) {
            // NEW is default state (?)
            ctx.getDoc().getDocContext().setState(DocResolutionStatus.NEW);
            return true;
        }
        var headers = ctx.getDoc().getMetadata();
        var newHeadChecksum = check.createMetadataChecksum(headers);

        var accepted = ChecksumStageUtil.resolveMetaChecksum(
                newHeadChecksum, ctx.getDoc());
        if (!accepted) {
            var s = new StringBuilder()
                    .append(check.getClass().getSimpleName())
                    .append(" - ")
                    .append("Checksum=")
                    .append(StringUtils.abbreviate(newHeadChecksum, 200));
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_UNMODIFIED)
                            .source(ctx.getCrawlerContext())
                            .docContext(ctx.getDoc().getDocContext())
                            .subject(check)
                            .message(s.toString())
                            .build());
        }
        return accepted;
    }
}