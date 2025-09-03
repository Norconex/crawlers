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
package com.norconex.crawler.core.doc.pipelines;

import static com.norconex.crawler.core.doc.pipelines.ChecksumStageUtil.resolveDocumentChecksum;
import static com.norconex.crawler.core.doc.pipelines.ChecksumStageUtil.resolveMetaChecksum;
import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stubs.CrawlDocContextStubber;

class ChecksumStageUtilTest {

    @CrawlTest(focus = Focus.SESSION)
    void testResolveMetaChecksum(CrawlSession session) {
        // true is new/modified
        var docCtx = CrawlDocContextStubber.fresh("ref");
        new ImporterPipelineContext(session, docCtx);

        //--- NO CACHE ---
        var currentEntry = docCtx.getCurrentCrawlEntry();

        // no checksum - no cache
        assertThat(resolveMetaChecksum(null, docCtx)).isTrue();
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        // checksum - no cache
        assertThat(resolveMetaChecksum("abc", docCtx)).isTrue();
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        //--- WITH CACHE ---
        docCtx = CrawlDocContextStubber.incremental("ref", "content");
        currentEntry = docCtx.getCurrentCrawlEntry();
        var prevEntry = docCtx.getPreviousCrawlEntry();
        new ImporterPipelineContext(session, docCtx);

        // no checksum - cache with no checksum
        // we considered null-null to mean modified
        prevEntry.setMetaChecksum(null);
        assertThat(resolveMetaChecksum("abc", docCtx)).isTrue();
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);

        // no checksum - cache with checksum
        prevEntry.setMetaChecksum("abc");
        assertThat(resolveMetaChecksum(null, docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);

        // checksum - cache with no checksum
        prevEntry.setMetaChecksum(null);
        assertThat(resolveMetaChecksum("abc", docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);

        // checksum - cache with checksum - same
        prevEntry.setMetaChecksum("abc");
        assertThat(resolveMetaChecksum("abc", docCtx)).isFalse();
        assertThat(
                docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                        .isSameAs(ProcessingOutcome.UNMODIFIED);

        // checksum - cache with checksum - different
        prevEntry.setMetaChecksum("cde");
        assertThat(resolveMetaChecksum("abc", docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);
    }

    @CrawlTest(focus = Focus.SESSION)
    void testResolveDocumentChecksum(CrawlSession session) {
        // true is new/modified

        var docCtx = CrawlDocContextStubber.fresh("ref");
        var currentEntry = docCtx.getCurrentCrawlEntry();
        new CommitterPipelineContext(session, docCtx);

        //--- NO CACHE ---

        // no checksum - no cache
        assertThat(resolveDocumentChecksum(null, docCtx)).isTrue();
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        // checksum - no cache
        assertThat(resolveDocumentChecksum("abc", docCtx)).isTrue();
        assertThat(currentEntry.getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        //--- WITH CACHE ---
        docCtx = CrawlDocContextStubber.incremental("ref", "content");
        currentEntry = docCtx.getCurrentCrawlEntry();
        var prevEntry = docCtx.getPreviousCrawlEntry();
        new CommitterPipelineContext(session, docCtx);

        // no checksum - cache with no checksum
        // we considered null-null to mean modified
        prevEntry.setContentChecksum(null);
        assertThat(resolveDocumentChecksum("abc", docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);

        // no checksum - cache with checksum
        prevEntry.setContentChecksum("abc");
        assertThat(resolveDocumentChecksum(null, docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);

        // checksum - cache with no checksum
        prevEntry.setContentChecksum(null);
        assertThat(resolveDocumentChecksum("abc", docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);

        // checksum - cache with checksum - same
        prevEntry.setContentChecksum("abc");
        assertThat(resolveDocumentChecksum("abc", docCtx)).isFalse();
        assertThat(
                docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                        .isSameAs(ProcessingOutcome.UNMODIFIED);

        // checksum - cache with checksum - different
        prevEntry.setContentChecksum("cde");
        assertThat(resolveDocumentChecksum("abc", docCtx)).isTrue();
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.MODIFIED);
    }
}
