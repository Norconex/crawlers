/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipeline.orphans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link RequeueOrphansForProcessingStep}.
 */
@Timeout(30)
class RequeueOrphansForProcessingStepTest {

    private CrawlSession buildSession(
            CrawlEntryLedger ledger, QueuePipeline queuePipeline) {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        var docPipelines = mock(CrawlDocPipelines.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getDocPipelines()).thenReturn(docPipelines);
        when(docPipelines.getQueuePipeline()).thenReturn(queuePipeline);
        return session;
    }

    @Test
    void noOrphans_doesNotQueueAnything() {
        var ledger = mock(CrawlEntryLedger.class);
        var queuePipeline = mock(QueuePipeline.class);
        when(ledger.getBaselineCount()).thenReturn(0L);

        var session = buildSession(ledger, queuePipeline);
        var step = new RequeueOrphansForProcessingStep("step-id");
        step.execute(session);

        verify(queuePipeline, never()).accept(any());
    }

    @Test
    void maxDocsReached_doesNotQueueAnything() {
        var ledger = mock(CrawlEntryLedger.class);
        var queuePipeline = mock(QueuePipeline.class);
        when(ledger.getBaselineCount()).thenReturn(5L);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(true);

        var session = buildSession(ledger, queuePipeline);
        var step = new RequeueOrphansForProcessingStep("step-id");
        step.execute(session);

        verify(queuePipeline, never()).accept(any());
    }

    @Test
    void hasOrphans_queuesAll() {
        var ledger = mock(CrawlEntryLedger.class);
        var queuePipeline = mock(QueuePipeline.class);
        var entry1 = new CrawlEntry("ref-1");
        var entry2 = new CrawlEntry("ref-2");

        when(ledger.getBaselineCount()).thenReturn(2L);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(false);
        doAnswer(inv -> {
            Consumer<CrawlEntry> consumer = inv.getArgument(0);
            consumer.accept(entry1);
            consumer.accept(entry2);
            return null;
        }).when(ledger).forEachBaseline(any());

        var session = buildSession(ledger, queuePipeline);
        var step = new RequeueOrphansForProcessingStep("step-id");
        step.execute(session);

        verify(queuePipeline, org.mockito.Mockito.times(2)).accept(any());
    }

    @Test
    void hasOrphans_setsOrphanFlagOnEachEntry() {
        var ledger = mock(CrawlEntryLedger.class);
        var queuePipeline = mock(QueuePipeline.class);
        var entry1 = new CrawlEntry("ref-a");
        var entry2 = new CrawlEntry("ref-b");
        var processed = new ArrayList<CrawlEntry>();

        when(ledger.getBaselineCount()).thenReturn(2L);
        when(ledger.isMaxDocsProcessedReached()).thenReturn(false);
        doAnswer(inv -> {
            Consumer<CrawlEntry> consumer = inv.getArgument(0);
            consumer.accept(entry1);
            consumer.accept(entry2);
            return null;
        }).when(ledger).forEachBaseline(any());
        doAnswer(inv -> {
            QueuePipelineContext ctx = inv.getArgument(0);
            processed.add(ctx.getCrawlEntry());
            return null;
        }).when(queuePipeline).accept(any());

        var session = buildSession(ledger, queuePipeline);
        var step = new RequeueOrphansForProcessingStep("step-id");
        step.execute(session);

        assertThat(processed).allMatch(CrawlEntry::isOrphan);
        assertThat(processed).extracting(CrawlEntry::getReference)
                .containsExactlyInAnyOrder("ref-a", "ref-b");
    }
}
