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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.stubs.CrawlDocStubs;
import com.norconex.importer.doc.DocMetaConstants;

class CommonAttribsResolutionStageTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testCommonAttribsResolutionStage(CrawlerContext crawlCtx) {
        var doc = CrawlDocStubs.crawlDoc(
                "ref",
                """
                <html>
                  <head><title>Sample 🧑‍💻 HTML</title></head>
                  <body>
                    <h1>HTML sample</h1>
                    <p>Some HTML</p>
                  </body>
                </html>
                """);
        var ctx = new ImporterPipelineContext(crawlCtx, doc);
        new CommonAttribsResolutionStage().test(ctx);

        assertThat(doc.getDocContext().getCharset()).isEqualTo(UTF_8);
        assertThat(doc.getDocContext().getContentType()).isEqualTo(
                ContentType.HTML);

        assertThat(doc.getMetadata().getString(
                DocMetaConstants.CONTENT_ENCODING)).isEqualTo("UTF-8");
        assertThat(doc.getMetadata().getString(
                DocMetaConstants.CONTENT_TYPE)).isEqualTo("text/html");
        assertThat(doc.getMetadata().getString(
                DocMetaConstants.CONTENT_FAMILY)).isEqualTo("html");
    }
}
