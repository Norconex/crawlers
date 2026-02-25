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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.importer.doc.Doc;

/**
 * Tests for {@link MetadataChecksumStage}.
 */
class MetadataChecksumStageTest {

    // Helper: create a full ImporterPipelineContext for a given config
    private ImporterPipelineContext buildCtx(
            CrawlConfig config, CrawlDocContext docContext) {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        return new ImporterPipelineContext(session, docContext);
    }

    private CrawlDocContext simpleDocContext(String ref) {
        return CrawlDocContext.builder()
                .doc(new Doc(ref))
                .currentCrawlEntry(new CrawlEntry(ref))
                .build();
    }

    // -----------------------------------------------------------------
    // Early return: directive disabled
    // -----------------------------------------------------------------

    @Test
    void directiveDisabled_returnsTrueEarly() {
        var config = new CrawlConfig();
        // DOCUMENT directive is REQUIRED by default; disable it
        config.setDocumentFetchSupport(FetchDirectiveSupport.DISABLED);

        var ctx = buildCtx(config, simpleDocContext("ref"));
        // Stage is created with DOCUMENT directive
        var stage = new MetadataChecksumStage(FetchDirective.DOCUMENT);
        assertThat(stage.test(ctx)).isTrue();
    }

    // -----------------------------------------------------------------
    // No checksummer configured (null)
    // -----------------------------------------------------------------

    @Test
    void noChecksummer_setsNewOutcome_returnsTrue() {
        // Default config: documentFetchSupport=REQUIRED, metadataChecksummer=null
        var config = new CrawlConfig();
        // Ensure metadataChecksummer is null (it is by default)
        assertThat(config.getMetadataChecksummer()).isNull();

        var entry = new CrawlEntry("ref");
        var docContext = CrawlDocContext.builder()
                .doc(new Doc("ref"))
                .currentCrawlEntry(entry)
                .build();
        var ctx = buildCtx(config, docContext);
        var stage = new MetadataChecksumStage(FetchDirective.DOCUMENT);

        assertThat(stage.test(ctx)).isTrue();
        assertThat(entry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.NEW);
    }

    // -----------------------------------------------------------------
    // With checksummer — no previous entry (treat as new)
    // -----------------------------------------------------------------

    @Test
    void withChecksummer_noPreviousEntry_returnsTrueAsNew() {
        var config = new CrawlConfig();
        var checksummer = mock(MetadataChecksummer.class);
        when(checksummer.createMetadataChecksum(any()))
                .thenReturn("checksum-abc");
        config.setMetadataChecksummer(checksummer);

        var entry = new CrawlEntry("ref");
        var docContext = CrawlDocContext.builder()
                .doc(new Doc("ref"))
                .currentCrawlEntry(entry)
                .build();

        // No previousCrawlEntry → treated as new
        var ctx = buildCtx(config, docContext);
        var stage = new MetadataChecksumStage(FetchDirective.DOCUMENT);

        assertThat(stage.test(ctx)).isTrue();
        assertThat(entry.getMetaChecksum()).isEqualTo("checksum-abc");
    }

    // -----------------------------------------------------------------
    // With checksummer — same checksum (unmodified)
    // -----------------------------------------------------------------

    @Test
    void withChecksummer_sameChecksumAsPrevious_returnsFalseAndFiresEvent() {
        var config = new CrawlConfig();
        var checksummer = mock(MetadataChecksummer.class);
        when(checksummer.createMetadataChecksum(any()))
                .thenReturn("same-checksum");
        config.setMetadataChecksummer(checksummer);

        var entry = new CrawlEntry("ref");
        var prevEntry = new CrawlEntry("ref");
        prevEntry.setMetaChecksum("same-checksum"); // same as new

        var docContext = CrawlDocContext.builder()
                .doc(new Doc("ref"))
                .currentCrawlEntry(entry)
                .previousCrawlEntry(prevEntry)
                .build();

        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        var ctx = new ImporterPipelineContext(session, docContext);

        var stage = new MetadataChecksumStage(FetchDirective.DOCUMENT);

        assertThat(stage.test(ctx)).isFalse();
        assertThat(entry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.UNMODIFIED);
        verify(session).fire(any());
    }

    // -----------------------------------------------------------------
    // With checksummer — different checksum (modified)
    // -----------------------------------------------------------------

    @Test
    void withChecksummer_differentChecksum_returnsTrue() {
        var config = new CrawlConfig();
        var checksummer = mock(MetadataChecksummer.class);
        when(checksummer.createMetadataChecksum(any()))
                .thenReturn("new-checksum");
        config.setMetadataChecksummer(checksummer);

        var entry = new CrawlEntry("ref");
        var prevEntry = new CrawlEntry("ref");
        prevEntry.setMetaChecksum("old-checksum"); // different from new

        var docContext = CrawlDocContext.builder()
                .doc(new Doc("ref"))
                .currentCrawlEntry(entry)
                .previousCrawlEntry(prevEntry)
                .build();

        var ctx = buildCtx(config, docContext);
        var stage = new MetadataChecksumStage(FetchDirective.DOCUMENT);

        assertThat(stage.test(ctx)).isTrue();
        assertThat(entry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.MODIFIED);
        verify(ctx.getCrawlSession(), never()).fire(any());
    }
}
