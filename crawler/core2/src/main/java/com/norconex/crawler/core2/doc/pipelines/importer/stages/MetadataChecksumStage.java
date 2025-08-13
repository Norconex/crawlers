/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.core2.doc.pipelines.importer.stages;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core2.doc.pipelines.ChecksumStageUtil;
import com.norconex.crawler.core2.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.fetch.FetchDirective;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;

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

        var check = ctx.getCrawlSession().getCrawlContext().getCrawlConfig()
                .getMetadataChecksummer();
        if (check == null) {
            // NEW is default state (?)
            ctx.getDocContext().getCurrentCrawlEntry()
                    .setProcessingOutcome(ProcessingOutcome.NEW);
            return true;
        }
        var doc = ctx.getDocContext().getDoc();
        var headers = doc.getMetadata();
        var newHeadChecksum = check.createMetadataChecksum(headers);

        var accepted = ChecksumStageUtil.resolveMetaChecksum(
                newHeadChecksum, ctx.getDocContext());
        if (!accepted) {
            var s = new StringBuilder()
                    .append(check.getClass().getSimpleName())
                    .append(" - ")
                    .append("Checksum=")
                    .append(StringUtils.abbreviate(newHeadChecksum, 200));
            ctx.getCrawlSession().getCrawlContext().fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_UNMODIFIED)
                    .source(ctx.getCrawlSession().getCrawlContext())
                    .crawlEntry(ctx.getDocContext().getCurrentCrawlEntry())
                    .subject(check)
                    .message(s.toString())
                    .build());
        }
        return accepted;
    }
}
