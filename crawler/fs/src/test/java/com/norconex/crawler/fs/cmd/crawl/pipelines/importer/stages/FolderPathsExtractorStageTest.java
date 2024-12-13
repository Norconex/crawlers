/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.fs.cmd.crawl.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.mock.MockFsCrawlerBuilder;

import lombok.NonNull;
import lombok.SneakyThrows;

class FolderPathsExtractorStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testFetchExceptionWrapped() {
        var doc = new CrawlDoc(new FsCrawlDocContext() {
            private static final long serialVersionUID = 1L;
            private int cnt;

            @Override
            @SneakyThrows
            public @NonNull String getReference() {
                if (cnt++ > 0) {
                    return "someFolder";
                }
                throw new FetchException("blah");
            }

            @Override
            public boolean isFolder() {
                return true;
            }
        });

        var ctx = new ImporterPipelineContext(
                new MockFsCrawlerBuilder(tempDir).crawlerContext(), doc);

        assertThatExceptionOfType(CrawlerException.class)
                .isThrownBy(() -> //NOSONAR
                new FolderPathsExtractorStage(
                        FetchDirective.DOCUMENT).executeStage(ctx))
                .withMessageContaining("Could not fetch child paths of:");
    }
}
