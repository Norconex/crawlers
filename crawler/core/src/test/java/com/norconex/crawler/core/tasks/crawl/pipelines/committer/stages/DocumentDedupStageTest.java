/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.pipelines.committer.stages;

import org.junit.jupiter.api.Disabled;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.junit.CrawlerTest;

@Disabled("TO MIGRATE")
class DocumentDedupStageTest {

    @CrawlerTest(config = """
            documentDeduplicate: true
            """)
    void testTest(CrawlerContext crawler) {

        //        crawler.getDocTrackerService().prepareForCrawl();
        //        crawler.getServices().getDedupService().init(crawler);
        //
        //        // first time checksum is not found and will cache it.
        //        var doc1 = CrawlDocStubs.crawlDoc("ref1", "content1");
        //        doc1.getDocContext().setContentChecksum("content-checksum");
        //        var ctx1 = new CommitterPipelineContext(crawler, doc1);
        //        assertThat(new DocumentDedupStage().test(ctx1)).isTrue();
        //
        //        // second time checksum is found and will reject the dupl.
        //        var doc2 = CrawlDocStubs.crawlDoc("ref2", "content2");
        //        doc2.getDocContext().setContentChecksum("content-checksum");
        //        var ctx2 = new CommitterPipelineContext(crawler, doc2);
        //        assertThat(new DocumentDedupStage().test(ctx2)).isFalse();
    }
}
