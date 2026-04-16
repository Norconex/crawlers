/* Copyright 2015-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.ledger.CrawlEntryLedger;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.WebCrawlConfig.ReferencedLinkType;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.operations.canon.CanonicalLinkDetector;
import com.norconex.crawler.web.doc.operations.scope.UrlScope;
import com.norconex.crawler.web.doc.pipelines.importer.stages.CanonicalStage;
import com.norconex.crawler.web.doc.pipelines.importer.stages.LinkExtractorStage;
import com.norconex.crawler.web.doc.pipelines.importer.stages.RecrawlableResolverStage;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.ledger.WebCrawlEntry;
import com.norconex.crawler.web.util.Web;
import com.norconex.importer.doc.Doc;

@Timeout(30)
class WebImporterPipelineTest {

    @WebCrawlTest
    void testCanonicalStageSameReferenceContent(CrawlContext crawlerCtx) {
        var reference = "http://www.example.com/file.pdf";
        var contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n"
                + reference + "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        var docEntry = new WebCrawlEntry(reference, 0);
        var doc = new Doc(reference).setInputStream(
                new CachedStreamFactory(1000, 1000)
                        .newInputStream(
                                new ByteArrayInputStream(
                                        contentValid.getBytes())));
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);
        Assertions.assertTrue(
                new CanonicalStage(
                        FetchDirective.DOCUMENT)
                                .test(ctx));
    }

    @WebCrawlTest
    void testCanonicalStageSameReferenceHeader(CrawlContext crawlerCtx) {
        var reference = "http://www.example.com/file.pdf";
        var docEntry = new WebCrawlEntry(reference, 0);
        var doc = new Doc(reference).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        doc.getMetadata().set("Link",
                "<" + reference + "> rel=\"canonical\"");
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);
        Assertions.assertTrue(
                new CanonicalStage(
                        FetchDirective.METADATA)
                                .test(ctx));
    }

    @WebCrawlTest
    void testKeepMaxDepthLinks(CrawlContext crawlerCtx) {
        var reference = "http://www.example.com/file.html";
        var content = "<html><head><title>Test</title>\n"
                + "</head><body><a href=\"link.html\">A link</a></body></html>";

        var docEntry = new WebCrawlEntry(reference, 2);
        var doc = new Doc(reference)
                .setContentType(ContentType.HTML)
                .setInputStream(
                        new CachedStreamFactory(1000, 1000)
                                .newInputStream(
                                        new ByteArrayInputStream(
                                                content.getBytes())));

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        // queueURL calls getDocPipelines().getQueuePipeline() — must be mocked
        var pipelines = mock(CrawlDocPipelines.class);
        when(crawlerCtx.getDocPipelines()).thenReturn(pipelines);
        when(pipelines.getQueuePipeline())
                .thenReturn(mock(QueuePipeline.class));
        var ctx = new WebImporterPipelineContext(session, docCtx);
        Web.config(crawlerCtx).setMaxDepth(2);

        var stage = new LinkExtractorStage();

        // By default do not extract urls on max depth
        stage.test(ctx);
        Assertions.assertEquals(
                0, doc.getMetadata().getStrings(
                        WebDocMetadata.REFERENCED_URLS)
                        .size());

        // Here 1 URL should be extracted even if max depth is reached.
        Web.config(crawlerCtx).setKeepReferencedLinks(
                Set.of(
                        ReferencedLinkType.INSCOPE,
                        ReferencedLinkType.MAXDEPTH));
        stage.test(ctx);
        Assertions.assertEquals(
                1, doc.getMetadata().getStrings(
                        WebDocMetadata.REFERENCED_URLS)
                        .size());
    }

    // -----------------------------------------------------------------------
    // CanonicalStage – additional cases
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testCanonicalStageNullDetector(CrawlContext crawlerCtx) {
        // null detector → always pass (return true)
        Web.config(crawlerCtx).setCanonicalLinkDetector(null);

        var reference = "http://www.example.com/page.html";
        var docEntry = new WebCrawlEntry(reference, 0);
        var doc = new Doc(reference).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new CanonicalStage(FetchDirective.DOCUMENT).test(ctx))
                .isTrue();
    }

    @WebCrawlTest
    void testCanonicalStageDifferentCanonicalUrl(CrawlContext crawlerCtx) {
        // Canonical URL is different from current URL → reject current, queue canonical
        var canonical = "http://www.example.com/canonical.html";
        var current = "http://www.example.com/dup.html";
        var content = "<html><head>"
                + "<link rel=\"canonical\" href=\"" + canonical + "\" />"
                + "</head><body>Duplicate</body></html>";

        var docEntry = new WebCrawlEntry(current, 0);
        var doc = new Doc(current)
                .setContentType(ContentType.HTML)
                .setInputStream(
                        new CachedStreamFactory(1000, 1000)
                                .newInputStream(
                                        new ByteArrayInputStream(
                                                content.getBytes())));
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        // Mock doc pipelines so the queue pipeline accept doesn't NPE
        var pipelines = mock(CrawlDocPipelines.class);
        when(crawlerCtx.getDocPipelines()).thenReturn(pipelines);
        when(pipelines.getQueuePipeline())
                .thenReturn(mock(QueuePipeline.class));

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new CanonicalStage(FetchDirective.DOCUMENT).test(ctx))
                .isFalse();
        assertThat(docEntry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.REJECTED);
    }

    @WebCrawlTest
    void testCanonicalStageCircularRedirectTrail(CrawlContext crawlerCtx) {
        // Canonical URL already in redirect trail → process normally (return true)
        var canonical = "http://www.example.com/canonical.html";
        var current = "http://www.example.com/page.html";
        var content = "<html><head>"
                + "<link rel=\"canonical\" href=\"" + canonical + "\" />"
                + "</head><body>Content</body></html>";

        var docEntry = new WebCrawlEntry(current, 0);
        docEntry.addRedirectURL(canonical); // seed the trail so canonical is "already seen"
        var doc = new Doc(current).setInputStream(
                new CachedStreamFactory(1000, 1000)
                        .newInputStream(
                                new ByteArrayInputStream(content.getBytes())));
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        // Circular reference → returns true (process the current page)
        assertThat(new CanonicalStage(FetchDirective.DOCUMENT).test(ctx))
                .isTrue();
    }

    @WebCrawlTest
    void testCanonicalStageDifferentUrlFromHeaders(CrawlContext crawlerCtx) {
        // Canonical URL in HTTP Link header is different from current → REJECTED
        var canonical = "http://www.example.com/canonical.html";
        var current = "http://www.example.com/dup.html";

        var docEntry = new WebCrawlEntry(current, 0);
        var doc = new Doc(current).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        // Set Link header so detectFromMetadata returns the canonical URL
        doc.getMetadata().set("Link",
                "<" + canonical + ">; rel=\"canonical\"");
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        var pipelines = mock(CrawlDocPipelines.class);
        when(crawlerCtx.getDocPipelines()).thenReturn(pipelines);
        when(pipelines.getQueuePipeline())
                .thenReturn(mock(QueuePipeline.class));

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        // METADATA directive → only checks headers, not content
        assertThat(new CanonicalStage(FetchDirective.METADATA).test(ctx))
                .isFalse();
        assertThat(docEntry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.REJECTED);
    }

    @WebCrawlTest
    void testCanonicalStageCanonicalOutOfScope(CrawlContext crawlerCtx) {
        // Canonical URL detected but is out of crawl scope → process current URL normally
        var canonical = "http://out-of-scope.com/canonical.html";
        var current = "http://www.example.com/page.html";
        var content = "<html><head>"
                + "<link rel=\"canonical\" href=\"" + canonical + "\" />"
                + "</head><body>Content</body></html>";

        // Reject every URL that goes through the scope resolver
        Web.config(crawlerCtx).setUrlScopeResolver(
                (sourceUrl, targetEntry) -> UrlScope
                        .out("test: all out of scope"));

        var docEntry = new WebCrawlEntry(current, 0);
        var doc = new Doc(current).setInputStream(
                new CachedStreamFactory(1000, 1000)
                        .newInputStream(
                                new ByteArrayInputStream(content.getBytes())));
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        // Canonical is out of scope → ignore it and process this URL normally
        assertThat(new CanonicalStage(FetchDirective.DOCUMENT).test(ctx))
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // RecrawlableResolverStage – all branches
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testRecrawlableResolverStageOrphan(CrawlContext crawlerCtx) {
        // Orphan docs are never skipped by the recrawl resolver
        var docEntry =
                new WebCrawlEntry("http://www.example.com/orphan.html", 0);
        docEntry.setOrphan(true);

        var doc = new Doc(docEntry.getReference()).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new RecrawlableResolverStage().test(ctx)).isTrue();
    }

    @WebCrawlTest
    void testRecrawlableResolverStageNoResolver(CrawlContext crawlerCtx) {
        // null resolver → always recrawlable
        Web.config(crawlerCtx).setRecrawlableResolver(null);

        var docEntry = new WebCrawlEntry("http://www.example.com/page.html", 0);
        var doc = new Doc(docEntry.getReference()).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new RecrawlableResolverStage().test(ctx)).isTrue();
    }

    @WebCrawlTest
    void testRecrawlableResolverStageNoPreviousEntry(CrawlContext crawlerCtx) {
        // No previous crawl entry (first crawl) → always recrawlable
        Web.config(crawlerCtx).setRecrawlableResolver(prev -> false); // always not-recrawlable

        var docEntry = new WebCrawlEntry("http://www.example.com/page.html", 0);
        var doc = new Doc(docEntry.getReference()).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                // No previousCrawlEntry → stage should pass through
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new RecrawlableResolverStage().test(ctx)).isTrue();
    }

    @WebCrawlTest
    void testRecrawlableResolverStageNotRecrawlable(CrawlContext crawlerCtx) {
        // Resolver says "not recrawlable" → reject, set PREMATURE outcome
        Web.config(crawlerCtx).setRecrawlableResolver(prev -> false);

        var prevEntry =
                new WebCrawlEntry("http://www.example.com/page.html", 0);
        var currentEntry =
                new WebCrawlEntry("http://www.example.com/page.html", 0);
        var doc = new Doc(currentEntry.getReference()).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(currentEntry)
                .previousCrawlEntry(prevEntry)
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new RecrawlableResolverStage().test(ctx)).isFalse();
        assertThat(currentEntry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.PREMATURE);
    }

    @WebCrawlTest
    void testRecrawlableResolverStageRequeuesRedirectTarget(
            CrawlContext crawlerCtx) {
        // When the previous entry had a redirect target and the doc is not
        // recrawlable, the redirect target should be re-queued so it is not
        // wrongly treated as an orphan.
        Web.config(crawlerCtx).setRecrawlableResolver(prev -> false);

        var targetUrl = "http://www.example.com/redirect-target.html";
        var prevEntry =
                new WebCrawlEntry("http://www.example.com/page.html", 0);
        prevEntry.setRedirectTarget(targetUrl);

        var currentEntry =
                new WebCrawlEntry("http://www.example.com/page.html", 0);
        var doc = new Doc(currentEntry.getReference()).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(currentEntry)
                .previousCrawlEntry(prevEntry)
                .build();

        // Mock ledger to return the redirect target entry
        var targetEntry = new WebCrawlEntry(targetUrl, 0);
        var ledger = mock(CrawlEntryLedger.class);
        when(crawlerCtx.getCrawlEntryLedger()).thenReturn(ledger);
        when(ledger.getBaselineEntry(targetUrl))
                .thenReturn(Optional.of(targetEntry));

        // Mock doc pipelines so the queue pipeline accept doesn't NPE
        var queuePipeline = mock(QueuePipeline.class);
        var pipelines = mock(CrawlDocPipelines.class);
        when(crawlerCtx.getDocPipelines()).thenReturn(pipelines);
        when(pipelines.getQueuePipeline()).thenReturn(queuePipeline);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        assertThat(new RecrawlableResolverStage().test(ctx)).isFalse();
        assertThat(currentEntry.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.PREMATURE);
        // The redirect target must have been accepted by the queue pipeline
        verify(queuePipeline).accept(any());
    }

    @WebCrawlTest
    void testCanonicalStageNullAfterNormalization(CrawlContext crawlerCtx) {
        // If the detected canonical URL normalizes to null, the stage returns
        // false (the current URL is rejected / not processed).
        var canonical = "http://example.com/canonical.html";
        var current = "http://example.com/page.html";

        // Detector always returns the canonical URL
        Web.config(crawlerCtx).setCanonicalLinkDetector(
                new CanonicalLinkDetector() {
                    @Override
                    public String detectFromMetadata(
                            String reference, Properties metadata) {
                        return canonical;
                    }

                    @Override
                    public String detectFromContent(
                            String reference, InputStream is,
                            ContentType contentType) {
                        return canonical;
                    }
                });

        // Normalizer returns null for every URL → canonical becomes null
        Web.config(crawlerCtx).setUrlNormalizers(List.of(url -> null));

        var content = "<html><head><link rel=\"canonical\" href=\""
                + canonical + "\" /></head><body></body></html>";
        var docEntry = new WebCrawlEntry(current, 0);
        var doc = new Doc(current).setInputStream(
                new CachedStreamFactory(1000, 1000)
                        .newInputStream(
                                new ByteArrayInputStream(content.getBytes())));
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
        var ctx = new WebImporterPipelineContext(session, docCtx);

        // canonical normalizes to null → resolveCanonical returns false
        assertThat(new CanonicalStage(FetchDirective.DOCUMENT).test(ctx))
                .isFalse();
    }
}
