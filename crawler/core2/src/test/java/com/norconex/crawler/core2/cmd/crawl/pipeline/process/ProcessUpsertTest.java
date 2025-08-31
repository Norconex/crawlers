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
package com.norconex.crawler.core2.cmd.crawl.pipeline.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;
import com.norconex.crawler.core2.stubs.DocStubber;
import com.norconex.importer.response.ImporterResponse;
import com.norconex.importer.response.ImporterResponseProcessor;

public class ProcessUpsertTest {

    public static class TestResponseProcessor
            implements ImporterResponseProcessor {
        @Override
        public void processImporterResponse(ImporterResponse resp) {
            resp.setNestedResponses(List.of(
                    new ImporterResponse(DocStubber.doc("childResponse1")),
                    new ImporterResponse(DocStubber.doc("childResponse2"))));
        }
    }

    public static class TestConfigModifier implements Consumer<CrawlConfig> {
        @Override
        public void accept(CrawlConfig cfg) {
            cfg.getImporterConfig().setResponseProcessors(
                    List.of(new TestResponseProcessor()));
        }
    }

    @CrawlTest(
        focus = Focus.SESSION,
        configModifier = TestConfigModifier.class
    )
    void testThreadActionUpsert(CrawlSession session) {

        var docContext = CrawlDocContextStubber.fresh("ref");
        var ctx = new ProcessContext();
        ctx.finalized(false);
        ctx.crawlSession(session);
        ctx.docContext(docContext);

        ProcessUpsert.execute(ctx);

        assertThat(ctx.importerResponse()).isNotNull();
        assertThat(ctx.importerResponse().getNestedResponses()).hasSize(2);
    }
}
