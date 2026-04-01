/* Copyright 2026 Norconex Inc.
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
package com.norconex.crawler.web.callbacks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.doc.WebCrawlEntry;
import com.norconex.importer.doc.Doc;

class BeforeWebCrawlDocFinalizingTest {

    @Test
    void accept_whenOutcomeIsUnmodified_requeuesCachedReferencedUrls() {
        var ref = "https://example.com/page";
        var childRef = "https://example.com/child";

        var queuedContexts = new ArrayList<
                com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext>();
        var queuePipeline = QueuePipeline.builder()
                .stage(ctx -> {
                    queuedContexts.add(ctx);
                    return true;
                })
                .build();

        var session = mockSessionWithLedger(
                ref,
                currentEntry(ref, 2, ProcessingOutcome.UNMODIFIED),
                baselineEntry(ref, List.of(childRef)),
                queuePipeline);

        new BeforeWebCrawlDocFinalizing().accept(session, new Doc(ref));

        assertThat(queuedContexts).hasSize(1);
        var child = (WebCrawlEntry) queuedContexts.get(0).getCrawlEntry();
        assertThat(child.getReference()).isEqualTo(childRef);
        assertThat(child.getReferrerReference()).isEqualTo(ref);
        assertThat(child.getDepth()).isEqualTo(3);
    }

    @Test
    void accept_whenOutcomeIsNew_doesNotRequeueCachedReferencedUrls() {
        var ref = "https://example.com/page";

        var queuedContexts = new ArrayList<
                com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext>();
        var queuePipeline = QueuePipeline.builder()
                .stage(ctx -> {
                    queuedContexts.add(ctx);
                    return true;
                })
                .build();

        var session = mockSessionWithLedger(
                ref,
                currentEntry(ref, 2, ProcessingOutcome.NEW),
                baselineEntry(ref, List.of("https://example.com/child")),
                queuePipeline);

        new BeforeWebCrawlDocFinalizing().accept(session, new Doc(ref));

        assertThat(queuedContexts).isEmpty();
    }

    private static CrawlSession mockSessionWithLedger(
            String ref,
            WebCrawlEntry current,
            WebCrawlEntry baseline,
            QueuePipeline queuePipeline) {
        var ledger = mock(CrawlEntryLedger.class);
        when(ledger.getEntry(ref)).thenReturn(Optional.of(current));
        when(ledger.getBaselineEntry(ref)).thenReturn(Optional.of(baseline));

        var docPipelines = CrawlDocPipelines.builder()
                .queuePipeline(queuePipeline)
                .build();

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getDocPipelines()).thenReturn(docPipelines);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        return session;
    }

    private static WebCrawlEntry currentEntry(
            String ref, int depth, ProcessingOutcome outcome) {
        var entry = new WebCrawlEntry(ref, depth);
        entry.setProcessingOutcome(outcome);
        return entry;
    }

    private static WebCrawlEntry baselineEntry(String ref, List<String> urls) {
        var entry = new WebCrawlEntry(ref, 2);
        entry.setReferencedUrls(urls);
        return entry;
    }
}
