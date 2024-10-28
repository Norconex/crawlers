/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.junit.CrawlerTest;
import com.norconex.crawler.core.stubs.CrawlDocStubs;
import com.norconex.importer.response.ImporterResponse;

class DocProcessorUpsertTest {

    public static class ConfigModifier implements Consumer<CrawlerConfig> {
        @Override
        public void accept(CrawlerConfig cfg) {
            cfg.getImporterConfig().setResponseProcessors(
                    List.of(resp -> resp.setNestedResponses(List.of(
                            new ImporterResponse(CrawlDocStubs.crawlDoc(
                                    "childResponse1")),
                            new ImporterResponse(CrawlDocStubs.crawlDoc(
                                    "childResponse2"))))));
        }
    }

    @CrawlerTest(configModifier = ConfigModifier.class)
    void testThreadActionUpsert(CrawlerContext crawler) {

        var doc = CrawlDocStubs.crawlDoc("ref");
        var ctx = new DocProcessorContext();
        ctx.finalized(false);
        ctx.crawlerContext(crawler);
        ctx.doc(doc);
        ctx.docContext(doc.getDocContext());

        DocProcessorUpsert.execute(ctx);

        assertThat(ctx.importerResponse()).isNotNull();
        assertThat(ctx.importerResponse().getNestedResponses()).hasSize(2);
    }
}
