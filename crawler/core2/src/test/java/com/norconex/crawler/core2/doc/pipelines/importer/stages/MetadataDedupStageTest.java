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
package com.norconex.crawler.core2.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;

import com.norconex.crawler.core2.CrawlConfig;
import com.norconex.crawler.core2.context.CrawlContext;
import com.norconex.crawler.core2.doc.pipelines.DedupService;
import com.norconex.crawler.core2.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core2.fetch.FetchDirective;
import com.norconex.crawler.core2.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;

@MockitoSettings
class MetadataDedupStageTest {

    @Mock
    private CrawlSession crawlSession;
    @Mock
    private CrawlContext crawlContext;
    @Mock
    private DedupService dedupService;

    @Test
    void testMetadataDedupStage() {
        when(crawlSession.getCrawlContext()).thenReturn(crawlContext);
        when(dedupService.findOrTrackMetadata(Mockito.any()))
                .thenReturn(Optional.of("someRef"));
        when(crawlContext.getDedupService()).thenReturn(dedupService);

        var cfg = new CrawlConfig();
        cfg.setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED);
        when(crawlContext.getCrawlConfig()).thenReturn(cfg);

        var docCtx = CrawlDocContextStubber.fresh("ref", "content");
        docCtx.getCurrentCrawlEntry().setMetaChecksum("somechecksum");

        // Has duplicate meta
        var ctx = new ImporterPipelineContext(crawlSession, docCtx);
        docCtx.getCurrentCrawlEntry()
                .setProcessingOutcome(ProcessingOutcome.NEW);
        new MetadataDedupStage(FetchDirective.METADATA).test(ctx);
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.REJECTED);

        // Does not have duplicate meta
        when(dedupService.findOrTrackMetadata(Mockito.any()))
                .thenReturn(Optional.empty());
        docCtx.getCurrentCrawlEntry()
                .setProcessingOutcome(ProcessingOutcome.NEW);
        new MetadataDedupStage(FetchDirective.METADATA).test(ctx);
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);
    }
}
