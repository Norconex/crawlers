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

import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.ImportModuleStage;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;

class ImportModuleStageTest {

    @CrawlTest(focus = Focus.SESSION)
    void testImportModuleStage(CrawlSession session) throws IOException {
        var docCtx = CrawlDocContextStubber.fresh("ref", "tomato");
        session.getCrawlContext().getCrawlConfig().getImporterConfig()
                .setHandlers(
                        List.of(hctx -> {
                            try {
                                hctx.output().asWriter().write("potato");
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            return true;
                        }));
        var ctx = new ImporterPipelineContext(session, docCtx);
        var stage = new ImportModuleStage();
        stage.test(ctx);

        // no filters is equal to a match
        assertThat(IOUtils.toString(
                ctx.getDocContext().getDoc().getInputStream(), UTF_8).trim())
                        .isEqualTo("potato");
    }
}
