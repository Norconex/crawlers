/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.services.CrawlerServices;
import com.norconex.crawler.core.services.DedupService;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

@MockitoSettings
class MetadataDedupStageTest {

    @Mock
    private Crawler crawler;
    @Mock
    private CrawlerServices services;
    @Mock
    private DedupService dedupService;

    @Test
    void testMetadataDedupStage() {
        when(dedupService.findOrTrackMetadata(Mockito.any()))
                .thenReturn(Optional.of("someRef"));
        when(crawler.getServices()).thenReturn(services);
        when(crawler.getServices().getDedupService()).thenReturn(dedupService);

        var cfg = new CrawlerConfig();
        cfg.setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED);
        when(crawler.getConfiguration()).thenReturn(cfg);

        var doc = CrawlDocStubs.crawlDoc("ref", "content");
        doc.getDocContext().setMetaChecksum("somechecksum");

        // Has duplicate meta
        var ctx = new ImporterPipelineContext(crawler, doc);
        doc.getDocContext().setState(CrawlDocState.NEW);
        new MetadataDedupStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getDocContext().getState()).isSameAs(
                CrawlDocState.REJECTED);

        // Does not have duplicate meta
        when(dedupService.findOrTrackMetadata(Mockito.any()))
                .thenReturn(Optional.empty());
        doc.getDocContext().setState(CrawlDocState.NEW);
        new MetadataDedupStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getDocContext().getState()).isSameAs(CrawlDocState.NEW);
    }
}
