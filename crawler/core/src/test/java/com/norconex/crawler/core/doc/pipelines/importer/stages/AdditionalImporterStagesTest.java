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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.importer.doc.Doc;

@Timeout(30)
class AdditionalImporterStagesTest {

    @Test
    void commonAttribsResolution_usesDocumentValuesWhenPresent() {
        var current = new CrawlEntry("ref");
        var previous = new CrawlEntry("ref");
        previous.setContentType(ContentType.valueOf("application/old"));
        previous.setCharset(StandardCharsets.ISO_8859_1);
        var doc = new Doc("ref");
        doc.setContentType(ContentType.TEXT);
        doc.setCharset(StandardCharsets.UTF_8);

        var ctx = new ImporterPipelineContext(
                mock(CrawlSession.class),
                CrawlDocContext.builder()
                        .doc(doc)
                        .currentCrawlEntry(current)
                        .previousCrawlEntry(previous)
                        .build());

        assertThat(new CommonAttribsResolutionStage().test(ctx)).isTrue();
        assertThat(current.getContentType()).isEqualTo(ContentType.TEXT);
        assertThat(current.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void commonAttribsResolution_appliesResolverDefaultsBeforePreviousValues() {
        var current = new CrawlEntry("ref");
        var previous = new CrawlEntry("ref");
        previous.setContentType(ContentType.valueOf("application/json"));
        previous.setCharset(StandardCharsets.UTF_16);
        var doc = new Doc("ref");

        var ctx = new ImporterPipelineContext(
                mock(CrawlSession.class),
                CrawlDocContext.builder()
                        .doc(doc)
                        .currentCrawlEntry(current)
                        .previousCrawlEntry(previous)
                        .build());

        assertThat(new CommonAttribsResolutionStage().test(ctx)).isTrue();
        assertThat(current.getContentType())
                .isEqualTo(ContentType.valueOf("application/octet-stream"));
        assertThat(current.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void commonAttribsResolution_fallsBackToPreviousEntryWhenDocValuesStayNull() {
        var current = new CrawlEntry("ref");
        var previous = new CrawlEntry("ref");
        previous.setContentType(ContentType.valueOf("application/json"));
        previous.setCharset(StandardCharsets.UTF_16);
        var doc = new NonDetectingDoc("ref");

        var ctx = new ImporterPipelineContext(
                mock(CrawlSession.class),
                CrawlDocContext.builder()
                        .doc(doc)
                        .currentCrawlEntry(current)
                        .previousCrawlEntry(previous)
                        .build());

        assertThat(new CommonAttribsResolutionStage().test(ctx)).isTrue();
        assertThat(current.getContentType())
                .isEqualTo(ContentType.valueOf("application/json"));
        assertThat(current.getCharset()).isEqualTo(StandardCharsets.UTF_16);
    }

    @Test
    void commonAttribsResolution_keepsCurrentValuesNullWithoutPreviousEntry() {
        var current = new CrawlEntry("ref");
        var doc = new NonDetectingDoc("ref");

        var ctx = new ImporterPipelineContext(
                mock(CrawlSession.class),
                CrawlDocContext.builder()
                        .doc(doc)
                        .currentCrawlEntry(current)
                        .build());

        assertThat(new CommonAttribsResolutionStage().test(ctx)).isTrue();
        assertThat(current.getContentType()).isNull();
        assertThat(current.getCharset()).isNull();
    }

    @Test
    void documentFiltersStage_rejectsDocumentAndFiresEvent() {
        DocumentFilter filter = doc -> false;
        var session = sessionWithConfig(
                config -> config.setDocumentFilters(List.of(filter)));
        var ctx = ctx(session, "ref");

        assertThat(new DocumentFiltersStage().test(ctx)).isFalse();
        assertThat(ctx.getDocContext().getCurrentCrawlEntry()
                .getProcessingOutcome()).isEqualTo(ProcessingOutcome.REJECTED);
        verify(session).fire(any(CrawlerEvent.class));
    }

    @Test
    void documentFiltersStage_acceptsDocumentWithoutEvent() {
        DocumentFilter filter = doc -> true;
        var session = sessionWithConfig(
                config -> config.setDocumentFilters(List.of(filter)));
        var ctx = ctx(session, "ref");

        assertThat(new DocumentFiltersStage().test(ctx)).isTrue();
        verify(session, never()).fire(any());
    }

    @Test
    void metadataFiltersStage_returnsEarlyWhenMetadataAlreadyExecuted() {
        var session = sessionWithConfig(config -> config
                .setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED));
        var ctx = ctx(session, "ref");

        assertThat(new MetadataFiltersStage(FetchDirective.DOCUMENT).test(ctx))
                .isTrue();
        verify(session, never()).fire(any());
    }

    @Test
    void metadataFiltersStage_rejectsMetadataAndSetsOutcome() {
        MetadataFilter filter = (ref, meta) -> false;
        var session = sessionWithConfig(config -> {
            config.setMetadataFetchSupport(FetchDirectiveSupport.OPTIONAL);
            config.setMetadataFilters(List.of(filter));
        });
        var ctx = ctx(session, "ref");

        assertThat(new MetadataFiltersStage(FetchDirective.METADATA).test(ctx))
                .isFalse();
        assertThat(ctx.getDocContext().getCurrentCrawlEntry()
                .getProcessingOutcome()).isEqualTo(ProcessingOutcome.REJECTED);
        verify(session).fire(any(CrawlerEvent.class));
    }

    @Test
    void metadataFiltersStage_acceptsMetadataWithoutEvent() {
        MetadataFilter filter = (ref, meta) -> true;
        var session = sessionWithConfig(config -> {
            config.setMetadataFetchSupport(FetchDirectiveSupport.OPTIONAL);
            config.setMetadataFilters(List.of(filter));
        });
        var ctx = ctx(session, "ref");

        assertThat(new MetadataFiltersStage(FetchDirective.METADATA).test(ctx))
                .isTrue();
        verify(session, never()).fire(any());
    }

    @Test
    void metadataDedupStage_returnsEarlyWhenMetadataAlreadyExecuted() {
        var dedupService = mock(DedupService.class);
        var session = sessionWithConfig(
                config -> config.setMetadataFetchSupport(
                        FetchDirectiveSupport.REQUIRED),
                dedupService);
        var ctx = ctx(session, "ref");

        assertThat(new MetadataDedupStage(FetchDirective.DOCUMENT).test(ctx))
                .isTrue();
        verify(dedupService, never()).findOrTrackMetadata(any());
    }

    @Test
    void metadataDedupStage_rejectsDuplicateAndFiresEvent() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackMetadata(any()))
                .thenReturn(Optional.of("original-ref"));
        var session = sessionWithConfig(
                config -> config.setMetadataFetchSupport(
                        FetchDirectiveSupport.OPTIONAL),
                dedupService);
        var ctx = ctx(session, "ref");

        assertThat(new MetadataDedupStage(FetchDirective.METADATA).test(ctx))
                .isFalse();
        assertThat(ctx.getDocContext().getCurrentCrawlEntry()
                .getProcessingOutcome()).isEqualTo(ProcessingOutcome.REJECTED);
        verify(session).fire(any(CrawlerEvent.class));
    }

    @Test
    void metadataDedupStage_acceptsNewMetadata() {
        var dedupService = mock(DedupService.class);
        when(dedupService.findOrTrackMetadata(any()))
                .thenReturn(Optional.empty());
        var session = sessionWithConfig(
                config -> config.setMetadataFetchSupport(
                        FetchDirectiveSupport.OPTIONAL),
                dedupService);
        var ctx = ctx(session, "ref");

        assertThat(new MetadataDedupStage(FetchDirective.METADATA).test(ctx))
                .isTrue();
        verify(session, never()).fire(any());
    }

    private CrawlSession sessionWithConfig(
            java.util.function.Consumer<CrawlConfig> customizer) {
        return sessionWithConfig(customizer, mock(DedupService.class));
    }

    private CrawlSession sessionWithConfig(
            java.util.function.Consumer<CrawlConfig> customizer,
            DedupService dedupService) {
        var config = new CrawlConfig();
        customizer.accept(config);

        var crawlContext = mock(CrawlContext.class);
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        when(crawlContext.getDedupService()).thenReturn(dedupService);
        return session;
    }

    private ImporterPipelineContext ctx(CrawlSession session, String ref) {
        var doc = new Doc(ref);
        doc.getMetadata().set("k", "v");
        var docContext = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(new CrawlEntry(ref))
                .build();
        return new ImporterPipelineContext(session, docContext);
    }

    private static final class NonDetectingDoc extends Doc {

        private final AtomicBoolean blockDetectedValues =
                new AtomicBoolean(true);

        private NonDetectingDoc(String reference) {
            super(reference);
        }

        @Override
        public ContentType getContentType() {
            return blockDetectedValues.get() ? null : super.getContentType();
        }

        @Override
        public java.nio.charset.Charset getCharset() {
            return blockDetectedValues.get() ? null : super.getCharset();
        }

        @Override
        public Doc setContentType(ContentType contentType) {
            if (!blockDetectedValues.get()) {
                super.setContentType(contentType);
            }
            return this;
        }

        @Override
        public Doc setCharset(java.nio.charset.Charset charset) {
            if (!blockDetectedValues.get()) {
                super.setCharset(charset);
            }
            return this;
        }
    }
}
