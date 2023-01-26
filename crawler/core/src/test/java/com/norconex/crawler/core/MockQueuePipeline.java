/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.core.pipeline.queue.QueuePipeline;

public class MockQueuePipeline implements QueuePipeline {

    @Override
    public void accept(DocRecordPipelineContext ctx) {
        for (ReferenceFilter f : ctx.getConfig().getReferenceFilters()) {
            if (!f.acceptReference(ctx.getDocRecord().getReference())) {
                ctx.getDocRecord().setState(CrawlDocState.REJECTED);
                ctx.fire(CrawlerEvent.REJECTED_FILTER);
                return;
            }
        }
        ctx.getDocInfoService().queue(ctx.getDocRecord());
    }
}
