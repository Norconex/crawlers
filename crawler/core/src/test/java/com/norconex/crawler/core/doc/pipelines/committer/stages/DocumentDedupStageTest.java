/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class DocumentDedupStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testTest() {
        new MockCrawlerBuilder(tempDir).configModifier(c -> {
            c.setDocumentDeduplicate(true);
        }).withInitializedCrawlerContext(ctx -> {
            // first time checksum is not found and will cache it.
            var doc1 = CrawlDocStubs.crawlDoc("ref1", "content1");
            doc1.getDocContext().setContentChecksum("content-checksum");
            var pipeCtx1 = new CommitterPipelineContext(ctx, doc1);
            assertThat(new DocumentDedupStage().test(pipeCtx1)).isTrue();
            return null;
        });

        new MockCrawlerBuilder(tempDir).configModifier(c -> {
            c.setDocumentDeduplicate(true);
        }).withInitializedCrawlerContext(ctx -> {
            // second time checksum is found and will reject the dupl.
            var doc2 = CrawlDocStubs.crawlDoc("ref2", "content2");
            doc2.getDocContext().setContentChecksum("content-checksum");
            var pipeCtx2 = new CommitterPipelineContext(ctx, doc2);
            assertThat(new DocumentDedupStage().test(pipeCtx2)).isFalse();
            return null;
        });

    }
}
