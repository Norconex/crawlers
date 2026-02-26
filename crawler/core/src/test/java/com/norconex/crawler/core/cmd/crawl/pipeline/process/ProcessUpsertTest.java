/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.service.CommitterService;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipeline;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipeline;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.response.ImporterResponse;
import com.norconex.importer.response.ImporterResponse.Status;

/**
 * Tests for {@link ProcessUpsert}.
 */
class ProcessUpsertTest {

    private CrawlSession session;
    private CrawlContext crawlCtx;
    private CrawlEntryLedger ledger;
    private ImporterPipeline importerPipeline;
    private CommitterPipeline committerPipeline;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        importerPipeline = mock(ImporterPipeline.class);
        committerPipeline = mock(CommitterPipeline.class);
        ledger = mock(CrawlEntryLedger.class);

        var config = mock(CrawlConfig.class);
        var committerService = mock(CommitterService.class);

        var docPipelines = CrawlDocPipelines.builder()
                .importerPipeline(importerPipeline)
                .committerPipeline(committerPipeline)
                .build();

        crawlCtx = mock(CrawlContext.class);
        when(crawlCtx.getDocPipelines()).thenReturn(docPipelines);
        when(crawlCtx.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlCtx.getCrawlConfig()).thenReturn(config);
        when(crawlCtx.getCommitterService()).thenReturn(committerService);
        when(ledger.getBaselineEntry(anyString())).thenReturn(Optional.empty());

        session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlCtx);
    }

    private ProcessContext buildCtx(String ref, ProcessingOutcome outcome) {
        var entry = new CrawlEntry(ref);
        entry.setProcessingOutcome(outcome);
        var docCtx = CrawlDocContext.builder()
                .doc(new Doc(ref))
                .currentCrawlEntry(entry)
                .build();
        return new ProcessContext()
                .crawlSession(session)
                .docContext(docCtx);
    }

    // -----------------------------------------------------------------
    // Null importer response
    // -----------------------------------------------------------------

    @Test
    void execute_nullResponse_newEntry_setsRejectedAndFinalizes() {
        when(importerPipeline.apply(any())).thenReturn(null);

        var ctx = buildCtx("ref:null-new", ProcessingOutcome.NEW);
        ProcessUpsert.execute(ctx);

        assertThat(
                ctx.docContext().getCurrentCrawlEntry().getProcessingOutcome())
                        .isEqualTo(ProcessingOutcome.REJECTED);
        assertThat(ctx.finalized()).isTrue();
    }

    @Test
    void execute_nullResponse_modifiedEntry_setsRejectedAndFinalizes() {
        when(importerPipeline.apply(any())).thenReturn(null);

        var ctx = buildCtx("ref:null-modified", ProcessingOutcome.MODIFIED);
        ProcessUpsert.execute(ctx);

        assertThat(
                ctx.docContext().getCurrentCrawlEntry().getProcessingOutcome())
                        .isEqualTo(ProcessingOutcome.REJECTED);
        assertThat(ctx.finalized()).isTrue();
    }

    @Test
    void execute_nullResponse_unmodifiedEntry_doesNotSetRejected() {
        // UNMODIFIED is not NEW/MODIFIED, so outcome should not change
        when(importerPipeline.apply(any())).thenReturn(null);

        var ctx = buildCtx("ref:null-unmodified", ProcessingOutcome.UNMODIFIED);
        ProcessUpsert.execute(ctx);

        assertThat(
                ctx.docContext().getCurrentCrawlEntry().getProcessingOutcome())
                        .isEqualTo(ProcessingOutcome.UNMODIFIED);
        assertThat(ctx.finalized()).isTrue();
    }

    @Test
    void execute_nullResponse_nullOutcome_doesNotSetRejected() {
        // null outcome: the null-check guards the branch
        when(importerPipeline.apply(any())).thenReturn(null);

        var entry = new CrawlEntry("ref:null-outcome");
        // leave processingOutcome null
        var docCtx = CrawlDocContext.builder()
                .doc(new Doc("ref:null-outcome"))
                .currentCrawlEntry(entry)
                .build();
        var ctx = new ProcessContext()
                .crawlSession(session)
                .docContext(docCtx);

        ProcessUpsert.execute(ctx);

        // The null-check prevents REJECTED: ProcessFinalize then sets BAD_STATUS
        assertThat(
                ctx.docContext().getCurrentCrawlEntry().getProcessingOutcome())
                        .isEqualTo(ProcessingOutcome.BAD_STATUS);
        assertThat(ctx.finalized()).isTrue();
    }

    // -----------------------------------------------------------------
    // Failed (rejected) importer response
    // -----------------------------------------------------------------

    @Test
    void execute_rejectedResponse_setsRejectedOutcomeAndFinalizes() {
        var response = new ImporterResponse("ref:rejected", Status.REJECTED);
        when(importerPipeline.apply(any())).thenReturn(response);

        var ctx = buildCtx("ref:rejected", ProcessingOutcome.NEW);
        ProcessUpsert.execute(ctx);

        assertThat(
                ctx.docContext().getCurrentCrawlEntry().getProcessingOutcome())
                        .isEqualTo(ProcessingOutcome.REJECTED);
        assertThat(ctx.finalized()).isTrue();
    }

    @Test
    void execute_errorResponse_setsRejectedOutcomeAndFinalizes() {
        var response = new ImporterResponse("ref:error", Status.ERROR);
        when(importerPipeline.apply(any())).thenReturn(response);

        var ctx = buildCtx("ref:error", ProcessingOutcome.NEW);
        ProcessUpsert.execute(ctx);

        assertThat(
                ctx.docContext().getCurrentCrawlEntry().getProcessingOutcome())
                        .isEqualTo(ProcessingOutcome.REJECTED);
        assertThat(ctx.finalized()).isTrue();
    }

    // -----------------------------------------------------------------
    // Successful importer response (no children)
    // -----------------------------------------------------------------

    @Test
    void execute_successResponse_invokesCommitterPipeline() {
        var response = new ImporterResponse("ref:success", Status.SUCCESS);
        when(importerPipeline.apply(any())).thenReturn(response);

        var ctx = buildCtx("ref:success", ProcessingOutcome.NEW);
        ProcessUpsert.execute(ctx);

        verify(committerPipeline).accept(any());
        // ProcessFinalize is NOT called directly for success path by
        // ProcessUpsert, so finalized flag remains false here
        assertThat(ctx.finalized()).isFalse();
    }

    // -----------------------------------------------------------------
    // Successful response with nested (child) documents
    // -----------------------------------------------------------------

    @Test
    void execute_successResponseWithChildren_processesEachChild() {
        var child1 = new ImporterResponse("ref:child-1", Status.SUCCESS);
        child1.setDoc(new Doc("ref:child-1"));
        var child2 = new ImporterResponse("ref:child-2", Status.REJECTED);
        child2.setDoc(new Doc("ref:child-2"));

        var parent = new ImporterResponse("ref:parent", Status.SUCCESS);
        parent.setNestedResponses(List.of(child1, child2));

        when(importerPipeline.apply(any())).thenReturn(parent);
        when(crawlCtx.createCrawlEntry("ref:child-1"))
                .thenReturn(new CrawlEntry("ref:child-1"));
        when(crawlCtx.createCrawlEntry("ref:child-2"))
                .thenReturn(new CrawlEntry("ref:child-2"));

        var ctx = buildCtx("ref:parent", ProcessingOutcome.NEW);
        ProcessUpsert.execute(ctx);

        // Parent + child-1 (success) each invoke committer pipeline → 2 calls
        // child-2 (rejected) does NOT invoke committer pipeline
        verify(committerPipeline, times(2)).accept(any());
    }

    @Test
    void execute_successResponseWithRejectedChild_finalizesChild() {
        var child = new ImporterResponse("ref:rejected-child", Status.REJECTED);
        child.setDoc(new Doc("ref:rejected-child"));

        var parent = new ImporterResponse("ref:parent-success", Status.SUCCESS);
        parent.setNestedResponses(List.of(child));

        when(importerPipeline.apply(any())).thenReturn(parent);
        var childEntry = new CrawlEntry("ref:rejected-child");
        when(crawlCtx.createCrawlEntry("ref:rejected-child"))
                .thenReturn(childEntry);

        var ctx = buildCtx("ref:parent-success", ProcessingOutcome.NEW);
        ProcessUpsert.execute(ctx);

        // Parent commits; child is rejected, so child's outcome = REJECTED
        assertThat(childEntry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.REJECTED);
        // Parent committer invoke: 1 time
        verify(committerPipeline, times(1)).accept(any());
    }
}
