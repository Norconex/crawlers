/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.doc.operations.scope.UrlScope;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.ledger.WebCrawlEntry;
import com.norconex.crawler.web.util.Web;
import com.norconex.importer.doc.Doc;

@Timeout(30)
class PostImportLinksStageTest {

    // -----------------------------------------------------------------------
    // Pass-through cases (always return true)
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testBlankPatternPassesThrough(CrawlContext ctx) {
        // Default postImportLinks has an empty pattern → no-op, always true
        Web.config(ctx).setPostImportLinks(new TextMatcher()); // blank pattern

        var result = runStage(ctx, "http://example.com/page.html",
                new WebCrawlEntry("http://example.com/page.html", 0));

        assertThat(result).isTrue();
    }

    @WebCrawlTest
    void testNoMatchingFieldsPassesThrough(CrawlContext ctx)
            throws IOException {
        // Pattern set but no metadata field matches it → no-op, always true
        Web.config(ctx).setPostImportLinks(TextMatcher.basic("myLinks"));

        var entry = new WebCrawlEntry("http://example.com/page.html", 0);
        try (@SuppressWarnings("resource")
        var doc = new Doc("http://example.com/page.html").setInputStream(
                new CachedStreamFactory(1, 1).newInputStream())) {
            // Intentionally no "myLinks" field in metadata

            var docCtx = CrawlDocContext.builder()
                    .doc(doc)
                    .currentCrawlEntry(entry)
                    .build();

            var session = mock(CrawlSession.class);
            when(session.getCrawlContext()).thenReturn(ctx);

            var pipeCtx = new CommitterPipelineContext(session, docCtx);
            assertThat(new PostImportLinksStage().test(pipeCtx)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // In-scope URLs get queued
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testInScopeUrlsAreQueued(CrawlContext ctx) {
        Web.config(ctx).setPostImportLinks(TextMatcher.basic("myLinks"));
        // Accept all URLs as in-scope
        Web.config(ctx).setUrlScopeResolver(
                (src, target) -> UrlScope.in());

        var queuePipeline = mock(QueuePipeline.class);
        var pipelines = mock(CrawlDocPipelines.class);
        when(ctx.getDocPipelines()).thenReturn(pipelines);
        when(pipelines.getQueuePipeline()).thenReturn(queuePipeline);

        var url1 = "http://example.com/link1.html";
        var url2 = "http://example.com/link2.html";

        var entry = new WebCrawlEntry("http://example.com/page.html", 1);
        var doc = buildDocWithLinks("http://example.com/page.html",
                "myLinks", url1, url2);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();

        var result = new PostImportLinksStage().test(
                new CommitterPipelineContext(session, docCtx));

        assertThat(result).isTrue();
        // Both in-scope URLs should now be in the entry's referencedUrls
        assertThat(entry.getReferencedUrls()).contains(url1, url2);
    }

    // -----------------------------------------------------------------------
    // Out-of-scope URLs are not queued
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testOutOfScopeUrlsNotQueued(CrawlContext ctx) {
        Web.config(ctx).setPostImportLinks(TextMatcher.basic("myLinks"));
        // Reject all URLs as out-of-scope
        Web.config(ctx).setUrlScopeResolver(
                (src, target) -> UrlScope.out("all out of scope"));

        var url = "http://external.com/link.html";
        var entry = new WebCrawlEntry("http://example.com/page.html", 0);
        var doc = buildDocWithLinks("http://example.com/page.html",
                "myLinks", url);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();

        var result = new PostImportLinksStage().test(
                new CommitterPipelineContext(session, docCtx));

        assertThat(result).isTrue();
        // Out-of-scope URL must NOT appear in referencedUrls
        assertThat(entry.getReferencedUrls()).doesNotContain(url);
    }

    // -----------------------------------------------------------------------
    // postImportLinksKeep = false → field is removed from metadata
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testLinksFieldRemovedWhenKeepIsFalse(CrawlContext ctx) {
        Web.config(ctx).setPostImportLinks(TextMatcher.basic("myLinks"));
        Web.config(ctx).setPostImportLinksKeep(false); // default is false
        Web.config(ctx).setUrlScopeResolver(
                (src, target) -> UrlScope.out("out"));

        var entry = new WebCrawlEntry("http://example.com/page.html", 0);
        var doc = buildDocWithLinks("http://example.com/page.html",
                "myLinks", "http://example.com/link.html");

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();

        new PostImportLinksStage().test(
                new CommitterPipelineContext(session, docCtx));

        // The "myLinks" field must have been deleted
        assertThat(doc.getMetadata().getString("myLinks")).isNull();
    }

    // -----------------------------------------------------------------------
    // postImportLinksKeep = true → field is retained in metadata
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testLinksFieldKeptWhenKeepIsTrue(CrawlContext ctx) {
        Web.config(ctx).setPostImportLinks(TextMatcher.basic("myLinks"));
        Web.config(ctx).setPostImportLinksKeep(true);
        Web.config(ctx).setUrlScopeResolver(
                (src, target) -> UrlScope.out("out"));

        var url = "http://example.com/link.html";
        var entry = new WebCrawlEntry("http://example.com/page.html", 0);
        var doc = buildDocWithLinks("http://example.com/page.html",
                "myLinks", url);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();

        new PostImportLinksStage().test(
                new CommitterPipelineContext(session, docCtx));

        // The "myLinks" field must still be present
        assertThat(doc.getMetadata().getStrings("myLinks")).contains(url);
    }

    // -----------------------------------------------------------------------
    // Deduplication: URL already in referencedUrls is not re-queued
    // -----------------------------------------------------------------------

    @WebCrawlTest
    void testDuplicateUrlsNotRequeued(CrawlContext ctx) {
        Web.config(ctx).setPostImportLinks(TextMatcher.basic("myLinks"));
        Web.config(ctx).setUrlScopeResolver(
                (src, target) -> UrlScope.in());

        var queuePipeline = mock(QueuePipeline.class);
        var pipelines = mock(CrawlDocPipelines.class);
        when(ctx.getDocPipelines()).thenReturn(pipelines);
        when(pipelines.getQueuePipeline()).thenReturn(queuePipeline);

        var alreadyExtracted = "http://example.com/already.html";
        var newUrl = "http://example.com/new.html";

        var entry = new WebCrawlEntry("http://example.com/page.html", 0);
        // Pre-load the already-extracted URL to simulate prior link extraction
        entry.setReferencedUrls(List.of(alreadyExtracted));

        var doc = buildDocWithLinks("http://example.com/page.html",
                "myLinks", alreadyExtracted, newUrl);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();

        new PostImportLinksStage().test(
                new CommitterPipelineContext(session, docCtx));

        // Only the new URL should be added; alreadyExtracted must be present
        // but was filtered out before queuing
        assertThat(entry.getReferencedUrls()).contains(alreadyExtracted,
                newUrl);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    @SuppressWarnings("resource")
    private static Doc buildDocWithLinks(
            String ref, String fieldName, String... urls) {
        var doc = new Doc(ref).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        for (String url : urls) {
            doc.getMetadata().add(fieldName, url);
        }
        return doc;
    }

    @SuppressWarnings("resource")
    private static boolean runStage(
            CrawlContext ctx, String ref, WebCrawlEntry entry) {
        var doc = new Doc(ref).setInputStream(
                new CachedStreamFactory(1, 1).newInputStream());
        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(entry)
                .build();
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);
        return new PostImportLinksStage().test(
                new CommitterPipelineContext(session, docCtx));
    }
}
