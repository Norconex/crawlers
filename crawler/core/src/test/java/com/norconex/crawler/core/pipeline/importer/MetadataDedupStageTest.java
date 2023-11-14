/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.store.DataStore;

@MockitoSettings
class MetadataDedupStageTest {

    @Mock
    private Crawler crawler;
    @Mock
    private DataStore<String> dedupStore;
    @Mock
    private EventManager eventManager;

    @Test
    void testMetadataDedupStage() {
        when(dedupStore.find(Mockito.anyString()))
            .thenReturn(Optional.of("someRef"));
        doNothing().when(eventManager).fire(Mockito.any());
        when(crawler.getDedupMetadataStore()).thenReturn(dedupStore);
        when(crawler.getEventManager()).thenReturn(eventManager);

        var cfg = new CrawlerConfig();
        cfg.setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED);
        when(crawler.getConfiguration()).thenReturn(cfg);

        var doc = CoreStubber.crawlDoc("ref", "content");
        doc.getDocRecord().setMetaChecksum("somechecksum");

        // Has duplicate meta
        var ctx = new ImporterPipelineContext(crawler, doc);
        doc.getDocRecord().setState(CrawlDocState.NEW);
        new MetadataDedupStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getDocRecord().getState()).isSameAs(
                CrawlDocState.REJECTED);

        // Does not have duplicate meta
        when(dedupStore.find(Mockito.anyString())).thenReturn(Optional.empty());
        doNothing().when(dedupStore).save(
                Mockito.anyString(), Mockito.anyString());
        doc.getDocRecord().setState(CrawlDocState.NEW);
        new MetadataDedupStage(FetchDirective.METADATA).test(ctx);
        assertThat(doc.getDocRecord().getState()).isSameAs(CrawlDocState.NEW);
    }
}
