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
package com.norconex.crawler.core.doc.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.importer.doc.Doc;

/**
 * Tests for {@link DocumentDedupStage}.
 */
@Timeout(30)
class DocumentDedupStageTest {

    private CommitterPipelineContext buildCtx(CrawlSession session,
            String ref) {
        var entry = new CrawlEntry(ref);
        var doc = new Doc(ref);
        var docContext = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();
        return new CommitterPipelineContext(session, docContext);
    }

    private CrawlSession buildSession(DedupService dedupService) {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getDedupService()).thenReturn(dedupService);
        return session;
    }

    @Test
    void noDuplicate_returnsTrue() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackDocument(any(CrawlEntry.class)))
                .thenReturn(Optional.empty());

        var session = buildSession(dedupService);
        var ctx = buildCtx(session, "http://example.com/page");

        assertThat(new DocumentDedupStage().test(ctx)).isTrue();
    }

    @Test
    void noDuplicate_doesNotFireEvent() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackDocument(any(CrawlEntry.class)))
                .thenReturn(Optional.empty());

        var session = buildSession(dedupService);
        var ctx = buildCtx(session, "http://example.com/page");

        new DocumentDedupStage().test(ctx);

        verify(session, never()).fire(any());
    }

    @Test
    void noDuplicate_doesNotSetRejectedOutcome() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackDocument(any(CrawlEntry.class)))
                .thenReturn(Optional.empty());

        var session = buildSession(dedupService);
        var ctx = buildCtx(session, "http://example.com/page");

        new DocumentDedupStage().test(ctx);

        assertThat(ctx.getDocContext().getCurrentCrawlEntry()
                .getProcessingOutcome()).isNull();
    }

    @Test
    void duplicateFound_returnsFalse() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackDocument(any(CrawlEntry.class)))
                .thenReturn(Optional.of("http://example.com/original"));

        var session = buildSession(dedupService);
        var ctx = buildCtx(session, "http://example.com/copy");

        assertThat(new DocumentDedupStage().test(ctx)).isFalse();
    }

    @Test
    void duplicateFound_setsRejectedOutcome() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackDocument(any(CrawlEntry.class)))
                .thenReturn(Optional.of("http://example.com/original"));

        var session = buildSession(dedupService);
        var ctx = buildCtx(session, "http://example.com/copy");

        new DocumentDedupStage().test(ctx);

        assertThat(ctx.getDocContext().getCurrentCrawlEntry()
                .getProcessingOutcome()).isEqualTo(ProcessingOutcome.REJECTED);
    }

    @Test
    void duplicateFound_firesRejectedDuplicateEvent() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackDocument(any(CrawlEntry.class)))
                .thenReturn(Optional.of("http://example.com/original"));

        var session = buildSession(dedupService);
        var ctx = buildCtx(session, "http://example.com/copy");

        new DocumentDedupStage().test(ctx);

        verify(session).fire(any(CrawlerEvent.class));
    }
}
