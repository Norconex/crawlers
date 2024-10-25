/* Copyright 2015-2024 Norconex Inc.
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

import java.io.ByteArrayInputStream;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.tasks.TaskContext;
import com.norconex.crawler.web.WebCrawlerConfig.ReferencedLinkType;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.pipelines.importer.stages.CanonicalStage;
import com.norconex.crawler.web.doc.pipelines.importer.stages.LinkExtractorStage;
import com.norconex.crawler.web.junit.WithCrawlerTest;
import com.norconex.crawler.web.util.Web;
import com.norconex.importer.doc.DocMetadata;

@WithCrawlerTest
class WebImporterPipelineTest {

    @Test
    void testCanonicalStageSameReferenceContent(TaskContext crawler) {
        var reference = "http://www.example.com/file.pdf";
        var contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + reference + "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        var doc = new CrawlDoc(
                new WebCrawlDocContext(reference, 0), null,
                new CachedStreamFactory(1000, 1000).newInputStream(
                        new ByteArrayInputStream(contentValid.getBytes())),
                false);
        var ctx = new WebImporterPipelineContext(crawler, doc);
        Assertions.assertTrue(
                new CanonicalStage(
                        FetchDirective.DOCUMENT).test(ctx));
    }

    @Test
    void testCanonicalStageSameReferenceHeader(TaskContext crawler) {
        var reference = "http://www.example.com/file.pdf";
        var doc = new CrawlDoc(
                new WebCrawlDocContext(reference, 0), null,
                new CachedStreamFactory(1, 1).newInputStream(), false);
        doc.getMetadata().set("Link", "<" + reference + "> rel=\"canonical\"");
        var ctx = new WebImporterPipelineContext(crawler, doc);
        Assertions.assertTrue(
                new CanonicalStage(
                        FetchDirective.METADATA).test(ctx));
    }

    @Test
    void testKeepMaxDepthLinks(TaskContext crawler) {
        var reference = "http://www.example.com/file.html";
        var content = "<html><head><title>Test</title>\n"
                + "</head><body><a href=\"link.html\">A link</a></body></html>";

        var docRecord = new WebCrawlDocContext(reference, 2);
        docRecord.setContentType(ContentType.HTML);

        var doc = new CrawlDoc(
                docRecord, null,
                new CachedStreamFactory(1000, 1000).newInputStream(
                        new ByteArrayInputStream(content.getBytes())),
                false);
        doc.getMetadata().set(DocMetadata.CONTENT_TYPE, "text/html");

        var ctx = new WebImporterPipelineContext(crawler, doc);
        Web.config(ctx.getTaskContext()).setMaxDepth(2);

        var stage = new LinkExtractorStage();

        // By default do not extract urls on max depth
        stage.test(ctx);
        Assertions.assertEquals(
                0, doc.getMetadata().getStrings(
                        WebDocMetadata.REFERENCED_URLS).size());

        // Here 1 URL shouled be extracted even if max depth is reached.
        Web.config(ctx.getTaskContext()).setKeepReferencedLinks(
                Set.of(
                        ReferencedLinkType.INSCOPE,
                        ReferencedLinkType.MAXDEPTH));
        stage.test(ctx);
        Assertions.assertEquals(
                1, doc.getMetadata().getStrings(
                        WebDocMetadata.REFERENCED_URLS).size());
    }
}
