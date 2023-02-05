/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.core.crawler.Crawler;

class CrawlSessionEventTest {

    @Test
    void testCrawlSessionEvent() {
        var sessConfig = new CrawlSessionConfig();
        sessConfig.setId("test-crawl-session");

        var crawlSession = CrawlSession.builder()
                .crawlerFactory((sess, cfg) -> Crawler.builder().build())
                .crawlSessionConfig(sessConfig)
//                .eventManager(null)
                .build();

        // crawl session events that are shutdown events
        for (String evName : Arrays.asList(
                CrawlSessionEvent.CRAWLSESSION_RUN_END,
                CrawlSessionEvent.CRAWLSESSION_STOP_END,
                CrawlSessionEvent.CRAWLSESSION_ERROR)) {
            CrawlSessionEvent evt = CrawlSessionEvent.builder()
                    .name(evName)
                    .source(crawlSession)
                    .build();
                assertThat(evt.isCrawlSessionShutdown()).isTrue();
                assertThat(CrawlSessionEvent
                        .isCrawlSessionShutdown(evt)).isTrue();
        }

        // crawl session events that are NOT shutdown events
        for (String evName : Arrays.asList(
                CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN,
                CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN,
                CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN,
                CrawlSessionEvent.CRAWLSESSION_CLEAN_END)) {
            CrawlSessionEvent evt = CrawlSessionEvent.builder()
                .name(evName)
                .source(crawlSession)
                .build();
            assertThat(evt.isCrawlSessionShutdown()).isFalse();
            assertThat(CrawlSessionEvent.isCrawlSessionShutdown(evt)).isFalse();
        }


        // Non-crawl-session events that have shutdown event names
        for (String evName : Arrays.asList(
                CrawlSessionEvent.CRAWLSESSION_RUN_END,
                CrawlSessionEvent.CRAWLSESSION_STOP_END)) {
            Event evt = Event.builder().name(evName).source("blah").build();
            assertThat(CrawlSessionEvent.isCrawlSessionShutdown(evt)).isTrue();
        }

        // Non-crawl-session events that do NOT have shutdown event names
        for (String evName : Arrays.asList(
                CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN,
                CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN)) {
            Event evt = Event.builder().name(evName).source("blah").build();
            assertThat(CrawlSessionEvent.isCrawlSessionShutdown(evt)).isFalse();
        }

        // Misc.
        CrawlSessionEvent evt = CrawlSessionEvent.builder()
                .name(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)
                .source(crawlSession)
                .build();
        assertThat(evt.getSource()).isInstanceOf(CrawlSession.class);
        assertThat(CrawlSessionEvent.isCrawlSessionShutdown(evt)).isFalse();
        assertThat(CrawlSessionEvent.isCrawlSessionShutdown(null)).isFalse();
        assertThat(evt.is(CrawlSessionEvent.builder()
                .name(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)
                .source(crawlSession)
                .build())).isTrue();
        assertThat(evt.is((Event) null)).isFalse();
        assertThat(evt.toString()).contains("CRAWLSESSION_RUN_BEGIN - "
                + "test-crawl-session");
    }
}
