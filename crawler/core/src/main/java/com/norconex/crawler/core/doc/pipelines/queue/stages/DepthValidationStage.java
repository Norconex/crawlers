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
package com.norconex.crawler.core.doc.pipelines.queue.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DepthValidationStage implements Predicate<QueuePipelineContext> {

    @Override
    public boolean test(QueuePipelineContext ctx) {
        var cfg = ctx.getCrawlContext().getCrawlConfig();
        var docCtx = ctx.getDocContext();

        if (cfg.getMaxDepth() >= 0 && docCtx.getDepth() > cfg.getMaxDepth()) {
            LOG.debug(
                    "URL too deep to process ({}): {}",
                    docCtx.getDepth(),
                    docCtx.getReference());
            docCtx.setState(CrawlDocStatus.TOO_DEEP);
            ctx.getCrawlContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_TOO_DEEP)
                            .source(ctx.getCrawlContext())
                            .subject(docCtx.getDepth())
                            .docContext(docCtx)
                            .build());
            return false;
        }
        return true;
    }
}
