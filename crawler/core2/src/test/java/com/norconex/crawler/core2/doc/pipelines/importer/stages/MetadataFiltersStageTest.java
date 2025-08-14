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
package com.norconex.crawler.core2.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core2.context.CrawlContext;
import com.norconex.crawler.core2.doc.operations.filter.OnMatch;
import com.norconex.crawler.core2.doc.operations.filter.impl.GenericMetadataFilter;
import com.norconex.crawler.core2.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core2.fetch.FetchDirective;
import com.norconex.crawler.core2.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;

class MetadataFiltersStageTest {

    @CrawlTest(focus = Focus.SESSION)
    void testMetadataFiltersStage(
            CrawlSession session, CrawlContext crawlCtx) {
        var docCtx = CrawlDocContextStubber.fresh(
                "ref", "content", "myfield", "somevalue");
        crawlCtx.getCrawlConfig().setMetadataFetchSupport(
                FetchDirectiveSupport.REQUIRED);

        // Filter not matching
        crawlCtx.getCrawlConfig()
                .setMetadataFilters(List.of(Configurable.configure(
                        new GenericMetadataFilter(),
                        cfg -> cfg
                                .setFieldMatcher(TextMatcher.basic("blah"))
                                .setValueMatcher(TextMatcher.basic("blah"))
                                .setOnMatch(OnMatch.EXCLUDE))));
        var ctx = new ImporterPipelineContext(session, docCtx);
        docCtx.getCurrentCrawlEntry()
                .setProcessingOutcome(ProcessingOutcome.NEW);
        new MetadataFiltersStage(FetchDirective.METADATA).test(ctx);
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.NEW);

        // Filter matching
        crawlCtx.getCrawlConfig()
                .setMetadataFilters(List.of(Configurable.configure(
                        new GenericMetadataFilter(),
                        cfg -> cfg
                                .setFieldMatcher(TextMatcher.basic("myfield"))
                                .setValueMatcher(TextMatcher.basic("somevalue"))
                                .setOnMatch(OnMatch.EXCLUDE))));
        ctx = new ImporterPipelineContext(session, docCtx);
        docCtx.getCurrentCrawlEntry()
                .setProcessingOutcome(ProcessingOutcome.NEW);
        new MetadataFiltersStage(FetchDirective.METADATA).test(ctx);
        assertThat(docCtx.getCurrentCrawlEntry().getProcessingOutcome())
                .isSameAs(ProcessingOutcome.REJECTED);
    }
}
