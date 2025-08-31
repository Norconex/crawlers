/* Copyright 2023-2025 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.context.CrawlContext;
import com.norconex.crawler.core2.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;

class DepthValidationStageTest {

    @CrawlTest(focus = Focus.SESSION)
    void testDepthValidationStage(
            CrawlSession session, CrawlContext crawlCtx) {

        var docCtx = CrawlDocContextStubber.fresh("ref");
        var currentEntry = docCtx.getCurrentCrawlEntry();
        currentEntry.setDepth(3);
        var ctx = new QueuePipelineContext(session, currentEntry);

        // Unlimited depth
        crawlCtx.getCrawlConfig().setMaxDepth(-1);
        currentEntry.setProcessingOutcome(ProcessingOutcome.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        // Max depth
        crawlCtx.getCrawlConfig().setMaxDepth(3);
        currentEntry.setProcessingOutcome(ProcessingOutcome.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        // Over max depth
        crawlCtx.getCrawlConfig().setMaxDepth(2);
        currentEntry.setProcessingOutcome(ProcessingOutcome.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.TOO_DEEP);
    }
}
