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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.committer.core.service.CommitterService;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.importer.doc.Doc;

/**
 * Tests for {@link ProcessDelete}.
 */
@Timeout(30)
class ProcessDeleteTest {

    @SuppressWarnings("unchecked")
    private ProcessContext buildCtx(String ref) {
        var entry = new CrawlEntry(ref);
        var doc = new Doc(ref);
        var docContext = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();

        var committerService = mock(CommitterService.class);
        var crawlContext = mock(CrawlContext.class);
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCommitterService()).thenReturn(committerService);

        return new ProcessContext()
                .crawlSession(session)
                .docContext(docContext)
                .finalized(true); // prevents ProcessFinalize from executing
    }

    @Test
    void execute_setsDeletedOutcome() {
        var ctx = buildCtx("http://example.com/deleted");

        ProcessDelete.execute(ctx);

        assertThat(ctx.docContext().getCurrentCrawlEntry()
                .getProcessingOutcome()).isEqualTo(ProcessingOutcome.DELETED);
    }

    @Test
    void execute_callsCommitterServiceDelete() {
        var ctx = buildCtx("http://example.com/deleted");
        var committerService =
                ctx.crawlSession()
                        .getCrawlContext().getCommitterService();

        ProcessDelete.execute(ctx);

        verify(committerService).delete(any(Doc.class));
    }

    @Test
    void execute_callsDeleteWithCorrectDoc() {
        var ctx = buildCtx("http://example.com/my-doc");

        ProcessDelete.execute(ctx);

        assertThat(ctx.docContext().getDoc().getReference())
                .isEqualTo("http://example.com/my-doc");
    }
}
