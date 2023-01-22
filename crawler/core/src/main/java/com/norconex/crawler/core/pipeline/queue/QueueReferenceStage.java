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
package com.norconex.crawler.core.pipeline.queue;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.doc.CrawlDocRecord.Stage;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * Common pipeline stage for queuing documents.
 */
public class QueueReferenceStage
        implements IPipelineStage<DocRecordPipelineContext> {

    private static final Logger LOG =
            LoggerFactory.getLogger(QueueReferenceStage.class);

    /**
     * Constructor.
     */
    public QueueReferenceStage() {
        super();
    }

    @Override
    public boolean execute(DocRecordPipelineContext ctx) {
        //TODO document and make sure it cannot be blank and remove this check?
        String ref = ctx.getDocRecord().getReference();
        if (StringUtils.isBlank(ref)) {
            return true;
        }

        Stage stage = ctx.getDocInfoService().getProcessingStage(ref);

        //TODO make this a reusable method somewhere, or part of the
        //CrawlDocRecordService?
        if (Stage.ACTIVE.is(stage)) {
            debug("Already being processed: %s", ref);
        } else if (Stage.QUEUED.is(stage)) {
            debug("Already queued: %s", ref);
        } else if (Stage.PROCESSED.is(stage)) {
            debug("Already processed: %s", ref);
        } else {
            ctx.getDocInfoService().queue(ctx.getDocRecord());
            debug("Queued for processing: %s", ref);
        }
        return true;
    }

    private void debug(String message, Object... values) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format(message, values));
        }
    }
}
