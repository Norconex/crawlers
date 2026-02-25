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
package com.norconex.crawler.core.doc.pipelines.queue.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link DepthValidationStage}.
 */
class DepthValidationStageTest {

    // Helper: build a QueuePipelineContext with the given maxDepth and entry
    private QueuePipelineContext buildCtx(int maxDepth, CrawlEntry entry) {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        var config = new CrawlConfig();
        config.setMaxDepth(maxDepth);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        return new QueuePipelineContext(session, entry);
    }

    // -----------------------------------------------------------------
    // Within depth limit
    // -----------------------------------------------------------------

    @Test
    void depthWithinLimit_returnsTrue() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(2);

        var ctx = buildCtx(5, entry);
        assertThat(new DepthValidationStage().test(ctx)).isTrue();
    }

    @Test
    void depthExactlyAtLimit_returnsTrue() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(3);

        var ctx = buildCtx(3, entry);
        assertThat(new DepthValidationStage().test(ctx)).isTrue();
    }

    // -----------------------------------------------------------------
    // Unlimited depth (maxDepth = -1)
    // -----------------------------------------------------------------

    @Test
    void unlimitedDepth_anyDepth_returnsTrue() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(9999);

        var ctx = buildCtx(-1, entry);
        assertThat(new DepthValidationStage().test(ctx)).isTrue();
    }

    // -----------------------------------------------------------------
    // Exceeds depth limit
    // -----------------------------------------------------------------

    @Test
    void depthExceedsLimit_returnsFalse() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(6);

        var ctx = buildCtx(5, entry);
        assertThat(new DepthValidationStage().test(ctx)).isFalse();
    }

    @Test
    void depthExceedsLimit_setsOutcomeTooDeep() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(4);

        var ctx = buildCtx(2, entry);
        new DepthValidationStage().test(ctx);
        assertThat(entry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.TOO_DEEP);
    }

    @Test
    void depthExceedsLimit_firesRejectedEvent() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(10);

        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        var config = new CrawlConfig();
        config.setMaxDepth(5);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        var ctx = new QueuePipelineContext(session, entry);

        new DepthValidationStage().test(ctx);

        verify(session).fire(any());
    }

    @Test
    void depthWithinLimit_doesNotFireEvent() {
        var entry = new CrawlEntry("http://example.com");
        entry.setDepth(1);

        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        var config = new CrawlConfig();
        config.setMaxDepth(5);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        var ctx = new QueuePipelineContext(session, entry);

        new DepthValidationStage().test(ctx);

        verify(session, never()).fire(any());
    }
}
