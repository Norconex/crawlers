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
package com.norconex.crawler.core.doc.pipelines;

import static com.norconex.commons.lang.config.Configurable.configure;
import static com.norconex.commons.lang.text.TextMatcher.basic;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.fetch.FetchUtil;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class OnMatchFiltersResolverTest {

    @TempDir
    private Path tempDir;

    @CrawlTest(focus = Focus.CONTEXT)
    void testIsRejectedByMetadataFilters(CrawlContext crawlCtx) {
        var doc = CrawlDocStubs.crawlDocWithCache("ref", "content");

        // match - include
        crawlCtx.getCrawlConfig()
                .setMetadataFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(basic("ref"))
                                .setOnMatch(OnMatch.INCLUDE))));
        var ctx1 = new ImporterPipelineContext(crawlCtx, doc);
        crawlCtx.getDocPipelines().getImporterPipeline().apply(ctx1);

        assertThat(ctx1.getDoc().getDocContext().getState())
                .isNotSameAs(CrawlDocStatus.REJECTED);

        // match - exclude
        crawlCtx.getCrawlConfig()
                .setMetadataFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(basic("ref"))
                                .setOnMatch(OnMatch.EXCLUDE))));
        var ctx2 = new ImporterPipelineContext(crawlCtx, doc);
        crawlCtx.getDocPipelines().getImporterPipeline().apply(ctx2);
        assertThat(ctx2.getDoc().getDocContext().getState())
                .isSameAs(CrawlDocStatus.REJECTED);

        // no match - include
        crawlCtx.getCrawlConfig()
                .setMetadataFilters(List.of(configure(
                        new GenericReferenceFilter(), cfg -> cfg
                                .setValueMatcher(basic("noref"))
                                .setOnMatch(OnMatch.INCLUDE))));
        var ctx3 = new ImporterPipelineContext(crawlCtx, doc);
        crawlCtx.getDocPipelines().getImporterPipeline().apply(ctx3);
        assertThat(ctx3.getDoc().getDocContext().getState())
                .isSameAs(CrawlDocStatus.REJECTED);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            #Originally in good state, currently doing metadata:
            NEW, REQUIRED, REQUIRED, METADATA, false
            NEW, OPTIONAL, REQUIRED, METADATA, true
            NEW, DISABLED, REQUIRED, METADATA, false
            NEW, REQUIRED, OPTIONAL, METADATA, false
            NEW, OPTIONAL, OPTIONAL, METADATA, true
            NEW, DISABLED, OPTIONAL, METADATA, false
            NEW, REQUIRED, DISABLED, METADATA, false
            NEW, OPTIONAL, DISABLED, METADATA, false
            NEW, DISABLED, DISABLED, METADATA, false
            #Originally in good state, currently doing document:
            NEW, REQUIRED, REQUIRED, DOCUMENT, false
            NEW, OPTIONAL, REQUIRED, DOCUMENT, false
            NEW, DISABLED, REQUIRED, DOCUMENT, false
            NEW, REQUIRED, OPTIONAL, DOCUMENT, true
            NEW, OPTIONAL, OPTIONAL, DOCUMENT, true
            NEW, DISABLED, OPTIONAL, DOCUMENT, false
            NEW, REQUIRED, DISABLED, DOCUMENT, false
            NEW, OPTIONAL, DISABLED, DOCUMENT, false
            NEW, DISABLED, DISABLED, DOCUMENT, false
            #Originally in bad state, currently doing metadata:
            BAD_STATUS, REQUIRED, REQUIRED, METADATA, false
            BAD_STATUS, OPTIONAL, REQUIRED, METADATA, true
            BAD_STATUS, DISABLED, REQUIRED, METADATA, false
            BAD_STATUS, REQUIRED, OPTIONAL, METADATA, false
            BAD_STATUS, OPTIONAL, OPTIONAL, METADATA, true
            BAD_STATUS, DISABLED, OPTIONAL, METADATA, false
            BAD_STATUS, REQUIRED, DISABLED, METADATA, false
            BAD_STATUS, OPTIONAL, DISABLED, METADATA, false
            BAD_STATUS, DISABLED, DISABLED, METADATA, false
            #Originally in bad state, currently doing document:
            BAD_STATUS, REQUIRED, REQUIRED, DOCUMENT, false
            BAD_STATUS, OPTIONAL, REQUIRED, DOCUMENT, false
            BAD_STATUS, DISABLED, REQUIRED, DOCUMENT, false
            BAD_STATUS, REQUIRED, OPTIONAL, DOCUMENT, false
            BAD_STATUS, OPTIONAL, OPTIONAL, DOCUMENT, false
            BAD_STATUS, DISABLED, OPTIONAL, DOCUMENT, false
            BAD_STATUS, REQUIRED, DISABLED, DOCUMENT, false
            BAD_STATUS, OPTIONAL, DISABLED, DOCUMENT, false
            BAD_STATUS, DISABLED, DISABLED, DOCUMENT, false
            """)
    void testShouldAbortOnBadStatus(
            CrawlDocStatus originalDocState,
            FetchDirectiveSupport metaSupport,
            FetchDirectiveSupport docSupport,
            FetchDirective currentDirective,
            boolean expected) {
        CrawlDocStubs.crawlDocWithCache("ref", "content");
        new MockCrawlerBuilder(tempDir).withCrawlContext(crawlCtx -> {
            var cfg = crawlCtx.getCrawlConfig();
            cfg.setMetadataFetchSupport(metaSupport);
            cfg.setDocumentFetchSupport(docSupport);

            assertThat(FetchUtil.shouldContinueOnBadStatus(
                    crawlCtx,
                    originalDocState,
                    currentDirective)).isEqualTo(expected);
            return null;
        });
    }
}
