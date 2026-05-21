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

import com.norconex.crawler.core.context.CrawlerContext;
import com.norconex.crawler.core.doc.pipelines.CrawlerDocPipelines;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.ledger.CrawlerEntryLedger;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlerSession;
import com.norconex.crawler.web.ledger.WebCrawlerEntry;
import com.norconex.importer.doc.Doc;

class BeforeWebCrawlerDocFinalizingTest {

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

        new BeforeWebCrawlerDocFinalizing().accept(session, new Doc(ref));

        assertThat(queuedContexts).hasSize(1);
        var child = (WebCrawlerEntry) queuedContexts.get(0).getCrawlEntry();
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

        new BeforeWebCrawlerDocFinalizing().accept(session, new Doc(ref));

        assertThat(queuedContexts).isEmpty();
    }

    private static CrawlerSession mockSessionWithLedger(
            String ref,
            WebCrawlerEntry current,
            WebCrawlerEntry baseline,
            QueuePipeline queuePipeline) {
        var ledger = mock(CrawlerEntryLedger.class);
        when(ledger.getEntry(ref)).thenReturn(Optional.of(current));
        when(ledger.getBaselineEntry(ref)).thenReturn(Optional.of(baseline));

        var docPipelines = CrawlerDocPipelines.builder()
                .queuePipeline(queuePipeline)
                .build();

        var crawlContext = mock(CrawlerContext.class);
        when(crawlContext.getCrawlEntryLedger()).thenReturn(ledger);
        when(crawlContext.getDocPipelines()).thenReturn(docPipelines);

        var session = mock(CrawlerSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        return session;
    }

    private static WebCrawlerEntry currentEntry(
            String ref, int depth, ProcessingOutcome outcome) {
        var entry = new WebCrawlerEntry(ref, depth);
        entry.setProcessingOutcome(outcome);
        return entry;
    }

    private static WebCrawlerEntry baselineEntry(String ref,
            List<String> urls) {
        var entry = new WebCrawlerEntry(ref, 2);
        entry.setReferencedUrls(urls);
        return entry;
    }
}
