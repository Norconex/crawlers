/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.Stubber;
import com.norconex.crawler.core.crawler.CrawlerThread.ReferenceContext;
import com.norconex.importer.response.ImporterResponse;
import com.norconex.importer.response.ImporterStatus;

class ThreadActionUpsertTest {

    @TempDir
    private Path tempDir;

    @Test
    void testThreadActionUpsert() {
        var crawler = Stubber.crawler(tempDir);
        crawler.getCrawlerConfig().getImporterConfig().setResponseProcessors(
                List.of(resp -> {
                    resp.addNestedResponse(new ImporterResponse(
                            Stubber.crawlDoc("childResponse1")));
                    resp.addNestedResponse(new ImporterResponse(
                            Stubber.crawlDoc("childResponse2")));
                    return new ImporterStatus();
                }));
        // start just so we have the crawler setup properly to run our
        // tests
        crawler.start();


        var doc = Stubber.crawlDoc("ref");
        var ctx = new ReferenceContext();
        ctx.finalized(false);
        ctx.crawler(crawler);
        ctx.doc(doc);
        ctx.docRecord(doc.getDocRecord());

        ThreadActionUpsert.execute(ctx);

        assertThat(ctx.importerResponse()).isNotNull();
        assertThat(ctx.importerResponse().getNestedResponses()).hasSize(2);
    }
}
