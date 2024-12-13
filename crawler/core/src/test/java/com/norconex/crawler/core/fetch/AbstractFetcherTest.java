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
package com.norconex.crawler.core.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.Xml;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.crawl.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.mocks.fetch.MockFetchRequest;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

class AbstractFetcherTest {

    @TempDir
    private Path tempDir;

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

    @Test
    void testEvents() {
        var crawlerContext = new MockCrawlerBuilder(tempDir).crawlerContext();
        List<String> methodsCalled = new ArrayList<>();
        var f = new MockFetcher() {
            @Override
            protected void fetcherStartup(CrawlerContext crawler) {
                methodsCalled.add("fetcherStartup");
            }

            @Override
            protected void fetcherShutdown(CrawlerContext crawler) {
                methodsCalled.add("fetcherShutdown");
            }

            @Override
            protected void fetcherThreadBegin(CrawlerContext crawler) {
                methodsCalled.add("fetcherThreadBegin");
            }

            @Override
            protected void fetcherThreadEnd(CrawlerContext crawler) {
                methodsCalled.add("fetcherThreadEnd");
            }
        };
        f.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CONTEXT_INIT_END)
                .source(crawlerContext)
                .build());
        f.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN)
                .source(crawlerContext)
                .build());
        f.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)
                .source(crawlerContext)
                .subject(Thread.currentThread())
                .build());
        f.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_RUN_THREAD_END)
                .source(crawlerContext)
                .subject(Thread.currentThread())
                .build());
        assertThat(methodsCalled).containsExactly(
                "fetcherStartup",
                "fetcherShutdown",
                "fetcherThreadBegin",
                "fetcherThreadEnd");
    }

    @Test
    void testWriteRead() {
        var f = new MockFetcher();
        assertThatNoException().isThrownBy(
                () -> Xml.assertWriteRead(f, "fetcher"));
    }
}
