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
package com.norconex.crawler.core.doc.pipelines.queue.stages;

import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;

class DepthValidationStageTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testDepthValidationStage(CrawlContext crawlCtx) {

        var docRec = new CrawlDocContext("ref");
        docRec.setDepth(3);
        var ctx = new QueuePipelineContext(crawlCtx, docRec);

        // Unlimited depth
        crawlCtx.getCrawlConfig().setMaxDepth(-1);
        docRec.setState(CrawlDocStatus.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(docRec.getState()).isSameAs(CrawlDocStatus.NEW);

        // Max depth
        crawlCtx.getCrawlConfig().setMaxDepth(3);
        docRec.setState(CrawlDocStatus.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(docRec.getState()).isSameAs(CrawlDocStatus.NEW);

        // Over max depth
        crawlCtx.getCrawlConfig().setMaxDepth(2);
        docRec.setState(CrawlDocStatus.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(docRec.getState()).isSameAs(CrawlDocStatus.TOO_DEEP);
    }
}
