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
package com.norconex.crawler.core.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.operations.filter.DocumentFilter;
import com.norconex.crawler.core.operations.filter.OnMatch;
import com.norconex.crawler.core.operations.filter.OnMatchFilter;
import com.norconex.crawler.core.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;
import com.norconex.importer.doc.Doc;

import lombok.AllArgsConstructor;
import lombok.Data;

class DocumentFiltersStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testDocumentFiltersStage() {
        var doc = CrawlDocStubs.crawlDoc("ref");
        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        var ctx = new ImporterPipelineContext(crawlerContext, doc);
        var stage = new DocumentFiltersStage();

        // no filters is equal to a match
        assertThat(stage.test(ctx)).isTrue();

        // test match
        crawlerContext.getConfiguration().setDocumentFilters(
                List.of(new TestFilter(OnMatch.INCLUDE, true)));
        assertThat(stage.test(ctx)).isTrue();

        // test no match
        crawlerContext.getConfiguration().setDocumentFilters(
                List.of(new TestFilter(OnMatch.INCLUDE, false)));
        assertThat(stage.test(ctx)).isFalse();

        // exclude
        crawlerContext.getConfiguration().setDocumentFilters(
                List.of(new TestFilter(OnMatch.EXCLUDE, false)));
        assertThat(stage.test(ctx)).isFalse();

    }

    @Data
    @AllArgsConstructor
    static class TestFilter implements DocumentFilter, OnMatchFilter {
        private OnMatch onMatch;
        private boolean accepts;

        @Override
        public boolean acceptDocument(Doc document) {
            return accepts;
        }
    }
}
