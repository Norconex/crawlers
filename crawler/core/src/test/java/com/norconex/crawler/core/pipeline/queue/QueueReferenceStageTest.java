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
package com.norconex.crawler.core.pipeline.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.junit.WithinCrawlSessionTest;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;

class QueueReferenceStageTest {

    @WithinCrawlSessionTest
    void testQueueReferenceStage(Crawler crawler) {
        var docRecord = CoreStubber.crawlDocRecord("ref");
        var stage = new QueueReferenceStage();

        var ctx1 = new DocRecordPipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx1)).isTrue();

        // testing a second time with same ref should not fail.
        var ctx2 = new DocRecordPipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx2)).isTrue();

        // a null reference should not fail
        var ctx3 = new DocRecordPipelineContext(
                crawler, new CrawlDocRecord());
        assertThat(stage.test(ctx3)).isTrue();
    }
}
