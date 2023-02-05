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
package com.norconex.crawler.core.pipeline;

import static com.norconex.crawler.core.doc.CrawlDocState.MODIFIED;
import static com.norconex.crawler.core.doc.CrawlDocState.UNMODIFIED;
import static com.norconex.crawler.core.pipeline.ChecksumStageUtil.resolveDocumentChecksum;
import static com.norconex.crawler.core.pipeline.ChecksumStageUtil.resolveMetaChecksum;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.Stubber;
import com.norconex.crawler.core.doc.CrawlDocState;

class ChecksumStageUtilTest {

    @TempDir
    private Path tempDir;

    @Test
    void testResolveMetaChecksum() {
        // true is new/modified

        var crawler = Stubber.crawler(tempDir);
        var doc = Stubber.crawlDoc("ref");
        var ctx = new DocumentPipelineContext(crawler, doc);

        //--- NO CACHE ---

        // no checksum - no cache
        assertThat(resolveMetaChecksum(null, ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(CrawlDocState.NEW);

        // checksum - no cache
        assertThat(resolveMetaChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(CrawlDocState.NEW);

        //--- WITH CACHE ---
        doc = Stubber.crawlDocWithCache("ref", "content");
        ctx = new DocumentPipelineContext(crawler, doc);

        // no checksum - cache with no checksum
        // we considered null-null to mean modified
        ctx.getCachedDocRecord().setMetaChecksum(null);
        assertThat(resolveMetaChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);

        // no checksum - cache with checksum
        ctx.getCachedDocRecord().setMetaChecksum("abc");
        assertThat(resolveMetaChecksum(null, ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);

        // checksum - cache with no checksum
        ctx.getCachedDocRecord().setMetaChecksum(null);
        assertThat(resolveMetaChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);

        // checksum - cache with checksum - same
        ctx.getCachedDocRecord().setMetaChecksum("abc");
        assertThat(resolveMetaChecksum("abc", ctx, this)).isFalse();
        assertThat(ctx.getDocRecord().getState()).isSameAs(UNMODIFIED);

        // checksum - cache with checksum - different
        ctx.getCachedDocRecord().setMetaChecksum("cde");
        assertThat(resolveMetaChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);
    }

    @Test
    void testResolveDocumentChecksum() {
        // true is new/modified

        var crawler = Stubber.crawler(tempDir);
        var doc = Stubber.crawlDoc("ref");
        var ctx = new DocumentPipelineContext(crawler, doc);

        //--- NO CACHE ---

        // no checksum - no cache
        assertThat(resolveDocumentChecksum(null, ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(CrawlDocState.NEW);

        // checksum - no cache
        assertThat(resolveDocumentChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(CrawlDocState.NEW);

        //--- WITH CACHE ---
        doc = Stubber.crawlDocWithCache("ref", "content");
        ctx = new DocumentPipelineContext(crawler, doc);

        // no checksum - cache with no checksum
        // we considered null-null to mean modified
        ctx.getCachedDocRecord().setContentChecksum(null);
        assertThat(resolveDocumentChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);

        // no checksum - cache with checksum
        ctx.getCachedDocRecord().setContentChecksum("abc");
        assertThat(resolveDocumentChecksum(null, ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);

        // checksum - cache with no checksum
        ctx.getCachedDocRecord().setContentChecksum(null);
        assertThat(resolveDocumentChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);

        // checksum - cache with checksum - same
        ctx.getCachedDocRecord().setContentChecksum("abc");
        assertThat(resolveDocumentChecksum("abc", ctx, this)).isFalse();
        assertThat(ctx.getDocRecord().getState()).isSameAs(UNMODIFIED);

        // checksum - cache with checksum - different
        ctx.getCachedDocRecord().setContentChecksum("cde");
        assertThat(resolveDocumentChecksum("abc", ctx, this)).isTrue();
        assertThat(ctx.getDocRecord().getState()).isSameAs(MODIFIED);
    }
}
