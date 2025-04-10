/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static com.norconex.crawler.core.fetch.FetchDirective.DOCUMENT;
import static com.norconex.crawler.core.fetch.FetchDirective.METADATA;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class ImporterPipelineContextTest {

    @Test
    void testImporterPipelineContext(@TempDir Path tempDir) {
        var doc = CrawlDocStubs.crawlDoc(
                "ref", "content", "myfield", "somevalue");
        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        var ctx = new ImporterPipelineContext(crawlerContext, doc);

        // metadata: disabled; document: enabled
        crawlerContext.getConfiguration().setMetadataFetchSupport(
                FetchDirectiveSupport.DISABLED);
        assertThat(ctx.isMetadataDirectiveExecuted(METADATA)).isFalse();
        assertThat(ctx.isMetadataDirectiveExecuted(DOCUMENT)).isFalse();
        assertThat(ctx.isFetchDirectiveEnabled(METADATA)).isFalse();
        assertThat(ctx.isFetchDirectiveEnabled(DOCUMENT)).isTrue();

        // metadata: enabled; document: enabled
        crawlerContext.getConfiguration().setMetadataFetchSupport(
                FetchDirectiveSupport.REQUIRED);
        assertThat(ctx.isMetadataDirectiveExecuted(METADATA)).isFalse();
        assertThat(ctx.isMetadataDirectiveExecuted(DOCUMENT)).isTrue();
        assertThat(ctx.isFetchDirectiveEnabled(METADATA)).isTrue();
        assertThat(ctx.isFetchDirectiveEnabled(DOCUMENT)).isTrue();
    }
}
