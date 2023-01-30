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
package com.norconex.crawler.core.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.TestUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionEvent;

class AbstractFetcherTest {

    @TempDir
    private Path tempDir;

    @Test
    void testAbstractFetcher() {
        var f = new MockFetcher();
        f.setReferenceFilters(
                new GenericReferenceFilter(TextMatcher.basic("ref")));
        assertThat(f.accept(new MockFetchRequest("ref"))).isTrue();
        assertThat(f.accept(new MockFetchRequest("potato"))).isFalse();
        assertThat(f.acceptRequest(new MockFetchRequest("potato"))).isTrue();


    }
    @Test
    void testEvents() {
        List<String> methodsCalled = new ArrayList<>();
        var f = new MockFetcher() {
            @Override
            protected void fetcherStartup(CrawlSession collector) {
                methodsCalled.add("fetcherStartup");
            }
            @Override
            protected void fetcherShutdown(CrawlSession collector) {
                methodsCalled.add("fetcherShutdown");
            }
            @Override
            protected void fetcherThreadBegin(Crawler crawler) {
                methodsCalled.add("fetcherThreadBegin");
            }
            @Override
            protected void fetcherThreadEnd(Crawler crawler) {
                methodsCalled.add("fetcherThreadEnd");
            }
        };
        TestUtil.withInitializedSession(tempDir, session -> {
            f.accept(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)
                    .source(session)
                    .build());
            f.accept(CrawlSessionEvent.builder()
                    .name(CrawlSessionEvent.CRAWLSESSION_RUN_END)
                    .source(session)
                    .build());
            f.accept(CrawlerEvent.builder()
                    .name(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)
                    .source(TestUtil.getFirstCrawler(session))
                    .subject(Thread.currentThread())
                    .build());
            f.accept(CrawlerEvent.builder()
                    .name(CrawlerEvent.CRAWLER_RUN_THREAD_END)
                    .source(TestUtil.getFirstCrawler(session))
                    .subject(Thread.currentThread())
                    .build());
        });
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
                () -> XML.assertWriteRead(f, "fetcher"));
    }
}
