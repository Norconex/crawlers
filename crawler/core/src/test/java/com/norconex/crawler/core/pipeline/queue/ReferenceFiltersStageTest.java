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
package com.norconex.crawler.core.pipeline.queue;

import static com.norconex.commons.lang.text.TextMatcher.basic;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.Stubber;
import com.norconex.crawler.core.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.importer.handler.filter.OnMatch;

class ReferenceFiltersStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testReferenceFiltersStage() {
        var crawler = Stubber.crawler(tempDir);
        var docRecord = Stubber.crawlDocRecord("ref");
        var stage = new ReferenceFiltersStage();

        // match - include
        crawler.getCrawlerConfig().setReferenceFilters(List.of(
                new GenericReferenceFilter(basic("ref"), OnMatch.INCLUDE)));
        var ctx1 = new DocRecordPipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx1)).isTrue();

        // match - exclude
        crawler.getCrawlerConfig().setReferenceFilters(List.of(
                new GenericReferenceFilter(basic("ref"), OnMatch.EXCLUDE)));
        var ctx2 = new DocRecordPipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx2)).isFalse();

        // no match - include
        stage = new ReferenceFiltersStage("blah");
        crawler.getCrawlerConfig().setReferenceFilters(List.of(
                new GenericReferenceFilter(basic("noref"), OnMatch.INCLUDE)));
        var ctx3 = new DocRecordPipelineContext(crawler, docRecord);
        assertThat(stage.test(ctx3)).isFalse();
    }
}
