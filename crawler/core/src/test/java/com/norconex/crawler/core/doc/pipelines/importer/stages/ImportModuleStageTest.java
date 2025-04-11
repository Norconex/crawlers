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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class ImportModuleStageTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testImportModuleStage(CrawlerContext crawlCtx) throws IOException {
        var doc = CrawlDocStubs.crawlDoc("ref", "tomato");
        crawlCtx.getConfiguration().getImporterConfig().setHandlers(
                List.of(hctx -> {
                    try {
                        hctx.output().asWriter().write("potato");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }));
        var ctx = new ImporterPipelineContext(crawlCtx, doc);
        var stage = new ImportModuleStage();
        stage.test(ctx);

        // no filters is equal to a match
        assertThat(IOUtils.toString(
                ctx.getDoc().getInputStream(), UTF_8).trim())
                        .isEqualTo("potato");
    }
}
