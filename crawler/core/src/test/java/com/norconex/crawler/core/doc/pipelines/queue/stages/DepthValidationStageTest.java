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
package com.norconex.crawler.core.doc.pipelines.queue.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.stubs.CrawlerStubs;

class DepthValidationStageTest {

    @Test
    void testDepthValidationStage(@TempDir Path tempDir) {

        var docRec = new CrawlDocContext("ref");
        docRec.setDepth(3);
        var crawler = CrawlerStubs.memoryCrawler(tempDir);
        var ctx = new QueuePipelineContext(crawler, docRec);

        // Unlimited depth
        crawler.getConfiguration().setMaxDepth(-1);
        docRec.setState(CrawlDocState.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(docRec.getState()).isSameAs(CrawlDocState.NEW);

        // Max depth
        crawler.getConfiguration().setMaxDepth(3);
        docRec.setState(CrawlDocState.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(docRec.getState()).isSameAs(CrawlDocState.NEW);

        // Over max depth
        crawler.getConfiguration().setMaxDepth(2);
        docRec.setState(CrawlDocState.NEW);
        new DepthValidationStage().test(ctx);
        assertThat(docRec.getState()).isSameAs(
                CrawlDocState.TOO_DEEP);
    }
}
