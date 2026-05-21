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
package com.norconex.crawler.web.doc.pipelines.queue.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.context.CrawlerContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlerSession;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.ledger.WebCrawlerEntry;

@Timeout(30)
class UrlNormalizerStageTest {

    private CrawlerSession session;
    private CrawlerContext crawlerCtx;
    private WebCrawlerConfig webConfig;

    @BeforeEach
    void setUp() {
        session = mock(CrawlerSession.class);
        crawlerCtx = mock(CrawlerContext.class);
        webConfig = new WebCrawlerConfig();
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        when(crawlerCtx.getCrawlConfig()).thenReturn(webConfig);
    }

    @Test
    void noNormalizersReturnTrue() {
        webConfig.setUrlNormalizers(List.of());
        var entry = new WebCrawlerEntry("http://example.com/page");
        var ctx = new QueuePipelineContext(session, entry);

        assertThat(new UrlNormalizerStage().test(ctx)).isTrue();
        assertThat(entry.getReference()).isEqualTo("http://example.com/page");
    }

    @Test
    void blankReferenceRejected() {
        webConfig.setUrlNormalizers(List.of(url -> url));
        var entry = new WebCrawlerEntry("   ");
        var ctx = new QueuePipelineContext(session, entry);

        assertThat(new UrlNormalizerStage().test(ctx)).isFalse();
        assertThat(entry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.REJECTED);
    }

    @Test
    void nullNormalizedUrlRejected() {
        webConfig.setUrlNormalizers(List.of(url -> null));
        var entry = new WebCrawlerEntry("http://example.com/page");
        var ctx = new QueuePipelineContext(session, entry);

        assertThat(new UrlNormalizerStage().test(ctx)).isFalse();
        assertThat(entry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.REJECTED);
    }

    @Test
    void referenceUpdatedWhenNormalized() {
        webConfig.setUrlNormalizers(
                List.of(url -> url.replaceAll("\\?.*", "")));
        var original = "http://example.com/page?session=abc";
        var entry = new WebCrawlerEntry(original);
        var ctx = new QueuePipelineContext(session, entry);

        assertThat(new UrlNormalizerStage().test(ctx)).isTrue();
        assertThat(entry.getReference()).isEqualTo("http://example.com/page");
        assertThat(entry.getReferenceTrail()).contains(original);
    }

    @Test
    void sameReferenceUnchanged() {
        webConfig.setUrlNormalizers(List.of(url -> url));
        var ref = "http://example.com/page";
        var entry = new WebCrawlerEntry(ref);
        var ctx = new QueuePipelineContext(session, entry);

        assertThat(new UrlNormalizerStage().test(ctx)).isTrue();
        assertThat(entry.getReference()).isEqualTo(ref);
        assertThat(entry.getReferenceTrail()).isEmpty();
    }

    @Test
    void multipleNormalizersAppliedInOrder() {
        webConfig.setUrlNormalizers(List.of(
                url -> url.toLowerCase(),
                url -> url.replaceAll("\\?.*", "")));
        var entry = new WebCrawlerEntry("HTTP://EXAMPLE.COM/PAGE?FOO=BAR");
        var ctx = new QueuePipelineContext(session, entry);

        assertThat(new UrlNormalizerStage().test(ctx)).isTrue();
        assertThat(entry.getReference()).isEqualTo("http://example.com/page");
    }
}
