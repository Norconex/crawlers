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
package com.norconex.crawler.core.pipelines.queue.stages;

import static com.norconex.commons.lang.config.Configurable.configure;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.operations.filter.OnMatch;
import com.norconex.crawler.core.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.pipelines.queue.QueuePipelineContext;

class ReferenceFiltersStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testReferenceFiltersStage() {
        var crawler = new MockCrawlerBuilder(tempDir).crawlerContext();
        var docRecord = new CrawlDocContext("ref");
        var stage = new ReferenceFiltersStage();

        // match - include
        crawler
                .getConfiguration()
                .setReferenceFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(TextMatcher.basic("ref"))
                                .setOnMatch(OnMatch.INCLUDE))));
        var ctx1 = new QueuePipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx1)).isTrue();

        // match - exclude
        crawler
                .getConfiguration()
                .setReferenceFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(TextMatcher.basic("ref"))
                                .setOnMatch(OnMatch.EXCLUDE))));
        var ctx2 = new QueuePipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx2)).isFalse();

        // no match - include
        stage = new ReferenceFiltersStage("blah");
        crawler
                .getConfiguration()
                .setReferenceFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(TextMatcher.basic("noref"))
                                .setOnMatch(OnMatch.INCLUDE))));
        var ctx3 = new QueuePipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx3)).isFalse();
    }
}
