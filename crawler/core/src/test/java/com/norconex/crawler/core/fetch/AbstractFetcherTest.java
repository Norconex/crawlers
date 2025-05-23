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
package com.norconex.crawler.core.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.Xml;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.mocks.fetch.MockFetchRequest;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.session.CrawlContext;

class AbstractFetcherTest {

    @Test
    void testAbstractFetcher() {
        var f = new MockFetcher();
        f.getConfiguration().setReferenceFilters(
                List.of(
                        Configurable.configure(
                                new GenericReferenceFilter(),
                                cfg -> cfg.setValueMatcher(
                                        TextMatcher.basic("ref")))));
        assertThat(f.accept(new MockFetchRequest("ref"))).isTrue();
        assertThat(f.accept(new MockFetchRequest("potato"))).isFalse();
        assertThat(f.acceptRequest(new MockFetchRequest("potato"))).isTrue();
    }

    @CrawlTest(focus = Focus.CONTEXT)
    void testEvents(CrawlContext crawlCtx) {
        List<String> methodsCalled = new ArrayList<>();
        var f = new MockFetcher() {
            @Override
            protected void fetcherStartup(CrawlContext crawler) {
                methodsCalled.add("fetcherStartup");
            }

            @Override
            protected void fetcherShutdown(CrawlContext crawler) {
                methodsCalled.add("fetcherShutdown");
            }
        };
        f.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CRAWL_BEGIN)
                .source(crawlCtx)
                .build());
        f.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CRAWL_END)
                .source(crawlCtx)
                .build());

        assertThat(methodsCalled).containsExactly(
                "fetcherStartup",
                "fetcherShutdown");
    }

    @Test
    void testWriteRead() {
        var f = new MockFetcher();
        assertThatNoException().isThrownBy(
                () -> Xml.assertWriteRead(f, "fetcher"));
    }
}
