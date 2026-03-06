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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Set;

import org.junit.jupiter.api.Assertions;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.WebCrawlerConfig.ReferencedLinkType;
import com.norconex.crawler.web.doc.WebCrawlEntry;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.pipelines.importer.stages.CanonicalStage;
import com.norconex.crawler.web.doc.pipelines.importer.stages.LinkExtractorStage;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.util.Web;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetaConstants;

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
        var doc = new Doc(reference).setInputStream(
                new CachedStreamFactory(1000, 1000)
                        .newInputStream(
                                new ByteArrayInputStream(
                                        content.getBytes())));
        doc.getMetadata().set(DocMetaConstants.CONTENT_TYPE,
                "text/html");

        var docCtx = CrawlDocContext.builder()
                .doc(doc)
                .currentCrawlEntry(docEntry)
                .build();
        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlerCtx);
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
}
