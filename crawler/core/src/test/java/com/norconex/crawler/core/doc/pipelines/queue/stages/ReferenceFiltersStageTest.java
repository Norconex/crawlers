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
package com.norconex.crawler.core.doc.pipelines.queue.stages;

import static com.norconex.commons.lang.config.Configurable.configure;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;

class ReferenceFiltersStageTest {

    @CrawlTest(focus = Focus.CONTEXT)
    void testReferenceFiltersStage(CrawlContext crawlCtx) {
        var docRecord = new CrawlDocContext("ref");
        var stage = new ReferenceFiltersStage();

        // match - include
        crawlCtx.getCrawlConfig()
                .setReferenceFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(TextMatcher.basic("ref"))
                                .setOnMatch(OnMatch.INCLUDE))));
        var ctx1 = new QueuePipelineContext(crawlCtx, docRecord);
        assertThat(stage.test(ctx1)).isTrue();

        // match - exclude
        crawlCtx.getCrawlConfig()
                .setReferenceFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(TextMatcher.basic("ref"))
                                .setOnMatch(OnMatch.EXCLUDE))));
        var ctx2 = new QueuePipelineContext(crawlCtx, docRecord);
        assertThat(stage.test(ctx2)).isFalse();

        // no match - include
        stage = new ReferenceFiltersStage("blah");
        crawlCtx.getCrawlConfig()
                .setReferenceFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(TextMatcher.basic("noref"))
                                .setOnMatch(OnMatch.INCLUDE))));
        var ctx3 = new QueuePipelineContext(crawlCtx, docRecord);
        assertThat(stage.test(ctx3)).isFalse();
    }
}
