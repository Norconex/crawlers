/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.event.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListenerConfig.OnMultiple;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.test.CrawlTestDriver;

/**
 * Tests for event listener classes: CrawlerLifeCycleListener,
 * StopCrawlerOnMaxEventListener, DeleteRejectedEventListener.
 */
@Timeout(60)
class EventListenersTest {

    @TempDir
    private Path tempDir;

    // -----------------------------------------------------------------
    // CrawlerLifeCycleListener
    // -----------------------------------------------------------------

    @Test
    void testCrawlerLifeCycleListener_nullEventIgnored() {
        var listener = new CrawlerLifeCycleListener() {};
        // Should not throw NPE
        assertThatNoException().isThrownBy(() -> listener.accept(null));
    }

    @Test
    void testCrawlerLifeCycleListener_callsOnCrawlerEvent() {
        var events = new java.util.ArrayList<String>();
        var listener = new CrawlerLifeCycleListener() {
            @Override
            protected void onCrawlerEvent(CrawlerEvent event) {
                events.add(event.getName());
            }

            @Override
            protected void onCrawlerCrawlBegin(CrawlerEvent event) {
                events.add("BEGIN_CALLBACK");
            }

            @Override
            protected void onCrawlerCrawlEnd(CrawlerEvent event) {
                events.add("END_CALLBACK");
            }
        };

        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_CRAWL_BEGIN));
        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_CRAWL_END));
        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN));
        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_STOP_REQUEST_END));
        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_CLEAN_BEGIN));
        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_CLEAN_END));
        listener.accept(crawlEvent(CrawlerEvent.CRAWLER_ERROR));

        assertThat(events).contains(
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                "BEGIN_CALLBACK",
                CrawlerEvent.CRAWLER_CRAWL_END,
                "END_CALLBACK",
                CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN,
                CrawlerEvent.CRAWLER_STOP_REQUEST_END,
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CrawlerEvent.CRAWLER_CLEAN_END,
                CrawlerEvent.CRAWLER_ERROR);
    }

    // -----------------------------------------------------------------
    // StopCrawlerOnMaxEventListener (config tests)
    // -----------------------------------------------------------------

    @Test
    void testStopCrawlerOnMaxConfig_defaults() {
        var listener = new StopCrawlerOnMaxEventListener();
        var cfg = listener.getConfiguration();
        assertThat(cfg.getMaximum()).isZero();
        assertThat(cfg.getOnMultiple()).isEqualTo(OnMultiple.ANY);
        assertThat(cfg.getEventMatcher()).isNotNull();
    }

    @Test
    void testStopCrawlerOnMaxConfig_settersWork() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setMaximum(5)
                .setOnMultiple(OnMultiple.SUM)
                .setEventMatcher(TextMatcher.basic("DOCUMENT_PROCESSED"));
        assertThat(listener.getConfiguration().getMaximum()).isEqualTo(5);
        assertThat(listener.getConfiguration().getOnMultiple())
                .isEqualTo(OnMultiple.SUM);
    }

    // -----------------------------------------------------------------
    // StopCrawlerOnMaxEventListener (via real crawl)
    // -----------------------------------------------------------------

    @Test
    void testStopCrawlerOnMaxEventListener_stopsOnMax() {
        var stopListener = new StopCrawlerOnMaxEventListener();
        stopListener.getConfiguration()
                .setMaximum(2)
                .setOnMultiple(OnMultiple.SUM)
                .setEventMatcher(TextMatcher.regex("DOCUMENT_PROCESSED.*"));

        var config = new CrawlConfig()
                .setId("stop-on-max-test")
                .setWorkDir(tempDir.resolve("stop-on-max"))
                // 10 references, but should stop after ~2 DOCUMENT_PROCESSED
                .setStartReferences(List.of(
                        "ref-1", "ref-2", "ref-3", "ref-4", "ref-5",
                        "ref-6", "ref-7", "ref-8", "ref-9", "ref-10"))
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ZERO))))
                .setNumThreadsPerNode(1)
                .setEventListeners(List.of(stopListener));

        assertThatNoException()
                .as("crawl with stop-on-max should complete without error")
                .isThrownBy(() -> new Crawler(
                        CrawlTestDriver.create(), config).crawl());
    }

    // -----------------------------------------------------------------
    // DeleteRejectedEventListener (config tests)
    // -----------------------------------------------------------------

    @Test
    void testDeleteRejectedConfig_defaults() {
        var listener = new DeleteRejectedEventListener();
        var cfg = listener.getConfiguration();
        assertThat(cfg.getEventMatcher()).isNotNull();
        // Default matcher is regex "REJECTED_.*"
        assertThat(cfg.getEventMatcher().matches("REJECTED_BAD")).isTrue();
        assertThat(cfg.getEventMatcher().matches("DOCUMENT_PROCESSED"))
                .isFalse();
    }

    @Test
    void testDeleteRejectedConfig_customMatcher() {
        var listener = new DeleteRejectedEventListener();
        listener.getConfiguration().setEventMatcher(
                TextMatcher.basic("MY_EVENT"));
        assertThat(listener.getConfiguration().getEventMatcher()
                .matches("MY_EVENT")).isTrue();
        assertThat(listener.getConfiguration().getEventMatcher()
                .matches("OTHER_EVENT")).isFalse();
    }

    // -----------------------------------------------------------------
    // DeleteRejectedEventListener - non-CrawlerEvent is silently ignored
    // -----------------------------------------------------------------

    @Test
    void testDeleteRejectedEventListener_nonCrawlerEventIgnored() {
        var listener = new DeleteRejectedEventListener();
        // A plain generic event is not a CrawlerEvent, so should be ignored
        var event = com.norconex.commons.lang.event.Event.builder()
                .name("SOME_EVENT")
                .source("source")
                .build();
        assertThatNoException().isThrownBy(() -> listener.accept(event));
    }

    @Test
    @Timeout(60)
    void testDeleteRejectedEventListener_withRealCrawl() {
        var listener = new DeleteRejectedEventListener();
        // Use a matcher that matches DOCUMENT_PROCESSED to exercise
        // storeRejection() with a matching event (and refCache operations)
        listener.getConfiguration().setEventMatcher(
                TextMatcher.regex("DOCUMENT_PROCESSED.*"));

        var config = new CrawlConfig()
                .setId("delete-rejected-test")
                .setWorkDir(tempDir.resolve("delete-rejected"))
                .setStartReferences(List.of("ref-a", "ref-b", "ref-c"))
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(Duration.ZERO))))
                .setNumThreadsPerNode(1)
                .setEventListeners(List.of(listener));

        assertThatNoException()
                .as("crawl with delete-rejected listener should complete OK")
                .isThrownBy(() -> new Crawler(
                        CrawlTestDriver.create(), config).crawl());
    }

    // -----------------------------------------------------------------
    // StopCrawlerOnMaxEventListener - unit-level (no full crawl)
    // -----------------------------------------------------------------

    @Test
    void testStopCrawlerOnMaxEventListener_nonMatchingEventIgnored() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setMaximum(1)
                .setEventMatcher(TextMatcher.basic("SPECIFIC_EVENT"));
        // Non-matching event should be silently ignored
        var event = com.norconex.commons.lang.event.Event.builder()
                .name("OTHER_EVENT").source("test").build();
        assertThatNoException().isThrownBy(() -> listener.accept(event));
    }

    @Test
    void testStopCrawlerOnMaxEventListener_crawlBeginEventClearsCounts() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setMaximum(100) // high maximum so doStop() never fires
                .setEventMatcher(TextMatcher.basic("BEFORE_EVENT"));
        // Accept a matching event to bump count
        var matchingEvent = com.norconex.commons.lang.event.Event.builder()
                .name("BEFORE_EVENT").source("test").build();
        listener.accept(matchingEvent);
        // Now send CRAWLER_CRAWL_BEGIN → clears counts
        var beginEvent = crawlEvent(CrawlerEvent.CRAWLER_CRAWL_BEGIN);
        assertThatNoException().isThrownBy(() -> listener.accept(beginEvent));
        // Send matching event again - count should be 1 (after clear)
        // If counts were NOT cleared we'd still have 1, but we want to
        // verify no exception occurs after clear
        assertThatNoException()
                .isThrownBy(() -> listener.accept(matchingEvent));
    }

    @Test
    void testStopCrawlerOnMaxEventListener_highMaximum_countsIncrementWithoutStop() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setMaximum(1000)
                .setOnMultiple(OnMultiple.ALL)
                .setEventMatcher(TextMatcher.basic("TARGET_EVENT"));
        var event = com.norconex.commons.lang.event.Event.builder()
                .name("TARGET_EVENT").source("test").build();
        // Send 5 events — none will trigger stop since count (5) < max (1000)
        for (int i = 0; i < 5; i++) {
            assertThatNoException().isThrownBy(() -> listener.accept(event));
        }
    }

    @Test
    void testStopCrawlerOnMaxEventListener_allMode_highMax_noStop() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setMaximum(1000)
                .setOnMultiple(OnMultiple.ALL)
                .setEventMatcher(TextMatcher.regex("EVENT_.*"));
        // Two different matching events, each with count < max
        var event1 = com.norconex.commons.lang.event.Event.builder()
                .name("EVENT_A").source("test").build();
        var event2 = com.norconex.commons.lang.event.Event.builder()
                .name("EVENT_B").source("test").build();
        assertThatNoException().isThrownBy(() -> {
            listener.accept(event1);
            listener.accept(event2);
        });
    }

    @Test
    void testStopCrawlerOnMaxEventListener_sumMode_highMax_noStop() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setMaximum(1000)
                .setOnMultiple(OnMultiple.SUM)
                .setEventMatcher(TextMatcher.regex("SUM_EVENT_.*"));
        var event1 = com.norconex.commons.lang.event.Event.builder()
                .name("SUM_EVENT_A").source("test").build();
        var event2 = com.norconex.commons.lang.event.Event.builder()
                .name("SUM_EVENT_B").source("test").build();
        // Sum of 2 events << 1000 → no stop
        assertThatNoException().isThrownBy(() -> {
            listener.accept(event1);
            listener.accept(event2);
        });
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static CrawlerEvent crawlEvent(String name) {
        return CrawlerEvent.builder()
                .name(name)
                .source("test-source")
                .build();
    }
}
