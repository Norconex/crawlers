/* Copyright 2010-2023 Norconex Inc.
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

import java.util.function.Predicate;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DepthValidationStage
        implements Predicate<DocRecordPipelineContext> {

    @Override
    public boolean test(DocRecordPipelineContext ctx) {
        var cfg = ctx.getConfig();

        if (cfg.getMaxDepth() != -1
                && ctx.getDocRecord().getDepth()
                        > cfg.getMaxDepth()) {
            LOG.debug("URL too deep to process ({}): {}",
                    ctx.getDocRecord().getDepth(),
                    ctx.getDocRecord().getReference());
            ctx.getDocRecord().setState(CrawlDocState.TOO_DEEP);
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_TOO_DEEP)
                    .source(ctx.getCrawler())
                    .subject(ctx.getDocRecord().getDepth())
                    .crawlDocRecord(ctx.getDocRecord())
                    .build());
            return false;
        }
        return true;
    }
}