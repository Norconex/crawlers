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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static com.norconex.crawler.core.fetch.FetchDirective.DOCUMENT;
import static com.norconex.crawler.core.fetch.FetchDirective.METADATA;
import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class ImporterPipelineContextTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testImporterPipelineContext(CrawlContext crawlCtx) {
        var doc = CrawlDocStubs.crawlDoc(
                "ref", "content", "myfield", "somevalue");
        var ctx = new ImporterPipelineContext(crawlCtx, doc);

        // metadata: disabled; document: enabled
        crawlCtx.getCrawlConfig().setMetadataFetchSupport(
                FetchDirectiveSupport.DISABLED);
        assertThat(ctx.isMetadataDirectiveExecuted(METADATA)).isFalse();
        assertThat(ctx.isMetadataDirectiveExecuted(DOCUMENT)).isFalse();
        assertThat(ctx.isFetchDirectiveEnabled(METADATA)).isFalse();
        assertThat(ctx.isFetchDirectiveEnabled(DOCUMENT)).isTrue();

        // metadata: enabled; document: enabled
        crawlCtx.getCrawlConfig().setMetadataFetchSupport(
                FetchDirectiveSupport.REQUIRED);
        assertThat(ctx.isMetadataDirectiveExecuted(METADATA)).isFalse();
        assertThat(ctx.isMetadataDirectiveExecuted(DOCUMENT)).isTrue();
        assertThat(ctx.isFetchDirectiveEnabled(METADATA)).isTrue();
        assertThat(ctx.isFetchDirectiveEnabled(DOCUMENT)).isTrue();
    }
}
