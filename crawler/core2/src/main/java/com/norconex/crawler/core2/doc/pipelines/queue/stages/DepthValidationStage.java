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
package com.norconex.crawler.core2.doc.pipelines.queue.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core2.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DepthValidationStage implements Predicate<QueuePipelineContext> {

    @Override
    public boolean test(QueuePipelineContext ctx) {
        var cfg = ctx.getCrawlSession().getCrawlContext().getCrawlConfig();
        var entry = ctx.getCrawlEntry();

        if (cfg.getMaxDepth() >= 0 && entry.getDepth() > cfg.getMaxDepth()) {
            LOG.debug("URL too deep to process ({}): {}",
                    entry.getDepth(),
                    entry.getReference());
            entry.setProcessingOutcome(ProcessingOutcome.TOO_DEEP);
            ctx.getCrawlSession().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.REJECTED_TOO_DEEP)
                            .crawlSession(ctx.getCrawlSession())
                            .source(entry.getDepth())
                            .crawlEntry(entry)
                            .build());
            return false;
        }
        return true;
    }
}
