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
package com.norconex.crawler.core.pipelines;

import static com.norconex.crawler.core.doc.DocResolutionStatus.MODIFIED;
import static com.norconex.crawler.core.doc.DocResolutionStatus.NEW;
import static com.norconex.crawler.core.doc.DocResolutionStatus.UNMODIFIED;
import static com.norconex.crawler.core.pipelines.ChecksumStageUtil.resolveDocumentChecksum;
import static com.norconex.crawler.core.pipelines.ChecksumStageUtil.resolveMetaChecksum;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class ChecksumStageUtilTest {

    @TempDir
    private Path tempDir;

    @Test
    void testResolveMetaChecksum() {
        // true is new/modified

        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        var doc = CrawlDocStubs.crawlDoc("ref");
        var ctx = new ImporterPipelineContext(crawlerContext, doc);

        //--- NO CACHE ---

        // no checksum - no cache
        assertThat(resolveMetaChecksum(null, doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(NEW);

        // checksum - no cache
        assertThat(resolveMetaChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(NEW);

        //--- WITH CACHE ---
        doc = CrawlDocStubs.crawlDocWithCache("ref", "content");
        ctx = new ImporterPipelineContext(crawlerContext, doc);

        // no checksum - cache with no checksum
        // we considered null-null to mean modified
        ctx.getDoc().getCachedDocContext().setMetaChecksum(null);
        assertThat(resolveMetaChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);

        // no checksum - cache with checksum
        ctx.getDoc().getCachedDocContext().setMetaChecksum("abc");
        assertThat(resolveMetaChecksum(null, doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);

        // checksum - cache with no checksum
        ctx.getDoc().getCachedDocContext().setMetaChecksum(null);
        assertThat(resolveMetaChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);

        // checksum - cache with checksum - same
        ctx.getDoc().getCachedDocContext().setMetaChecksum("abc");
        assertThat(resolveMetaChecksum("abc", doc)).isFalse();
        assertThat(
                ctx.getDoc().getDocContext().getState()).isSameAs(UNMODIFIED);

        // checksum - cache with checksum - different
        ctx.getDoc().getCachedDocContext().setMetaChecksum("cde");
        assertThat(resolveMetaChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);
    }

    @Test
    void testResolveDocumentChecksum() {
        // true is new/modified

        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        var doc = CrawlDocStubs.crawlDoc("ref");
        var ctx = new CommitterPipelineContext(crawlerContext, doc);

        //--- NO CACHE ---

        // no checksum - no cache
        assertThat(resolveDocumentChecksum(null, doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(NEW);

        // checksum - no cache
        assertThat(resolveDocumentChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(NEW);

        //--- WITH CACHE ---
        doc = CrawlDocStubs.crawlDocWithCache("ref", "content");
        ctx = new CommitterPipelineContext(crawlerContext, doc);

        // no checksum - cache with no checksum
        // we considered null-null to mean modified
        ctx.getDoc().getCachedDocContext().setContentChecksum(null);
        assertThat(resolveDocumentChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);

        // no checksum - cache with checksum
        ctx.getDoc().getCachedDocContext().setContentChecksum("abc");
        assertThat(resolveDocumentChecksum(null, doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);

        // checksum - cache with no checksum
        ctx.getDoc().getCachedDocContext().setContentChecksum(null);
        assertThat(resolveDocumentChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);

        // checksum - cache with checksum - same
        ctx.getDoc().getCachedDocContext().setContentChecksum("abc");
        assertThat(resolveDocumentChecksum("abc", doc)).isFalse();
        assertThat(
                ctx.getDoc().getDocContext().getState()).isSameAs(UNMODIFIED);

        // checksum - cache with checksum - different
        ctx.getDoc().getCachedDocContext().setContentChecksum("cde");
        assertThat(resolveDocumentChecksum("abc", doc)).isTrue();
        assertThat(ctx.getDoc().getDocContext().getState()).isSameAs(MODIFIED);
    }
}
