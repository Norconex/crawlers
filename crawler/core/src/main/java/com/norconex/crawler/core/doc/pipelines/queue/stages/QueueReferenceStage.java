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
package com.norconex.crawler.core.doc.pipelines.queue.stages;

import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDocContext.Stage;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Common pipeline stage for queuing documents.
 */
@Slf4j
public class QueueReferenceStage implements Predicate<QueuePipelineContext> {

    @Override
    public boolean test(QueuePipelineContext ctx) {
        //TODO document and make sure it cannot be blank and remove this check?
        String ref = ctx.getDocContext().getReference();
        if (StringUtils.isBlank(ref)) {
            return true;
        }

        var docTracker = ctx.getCrawler().getServices().getDocTrackerService();
        var stage = docTracker.getProcessingStage(ref);

        //TODO make this a reusable method somewhere, or part of the
        //DocTrackerService?
        if (Stage.ACTIVE.is(stage)) {
            debug("Already being processed: %s", ref);
        } else if (Stage.QUEUED.is(stage)) {
            debug("Already queued: %s", ref);
        } else if (Stage.PROCESSED.is(stage)) {
            debug("Already processed: %s", ref);
        } else {
            docTracker.queue(ctx.getDocContext());
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
