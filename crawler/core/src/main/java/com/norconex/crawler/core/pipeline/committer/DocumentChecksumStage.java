/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.committer;

import com.norconex.crawler.core.checksum.IDocumentChecksummer;
import com.norconex.crawler.core.doc.CrawlState;
import com.norconex.crawler.core.pipeline.ChecksumStageUtil;
import com.norconex.crawler.core.pipeline.DocumentPipelineContext;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * Common pipeline stage for creating a document checksum.
 *
 */
public class DocumentChecksumStage
        implements IPipelineStage<DocumentPipelineContext> {

    @Override
    public boolean execute(DocumentPipelineContext ctx) {
        IDocumentChecksummer checksummer =
                ctx.getConfig().getDocumentChecksummer();

        // if there are no checksum defined and state is not new/modified,
        // we treat all docs as new.
        if (checksummer == null) {
            if (ctx.getDocRecord().getState() == null
                    || !ctx.getDocRecord().getState().isNewOrModified()) {
                // NEW is default state (?)
                ctx.getDocRecord().setState(CrawlState.NEW);
            }
            return true;
        }
        String newDocChecksum =
                checksummer.createDocumentChecksum(ctx.getDocument());
        return ChecksumStageUtil.resolveDocumentChecksum(
                newDocChecksum, ctx, checksummer);
    }
}