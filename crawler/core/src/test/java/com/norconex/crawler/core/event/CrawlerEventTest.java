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
package com.norconex.crawler.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import com.norconex.crawler.core._DELETE.CrawlTest;
import com.norconex.crawler.core._DELETE.CrawlTest.Focus;
import com.norconex.crawler.core.cli.CliException;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.session.CrawlSession;

class CrawlerEventTest {

    @CrawlTest(focus = Focus.SESSION)
    void testCrawlerEvent(CrawlSession session) {
        var event = event(session, b -> {});
        assertThat(event.getSource()).hasToString("somesubject");
        assertThat(event.getCrawlSession().toString())
                .contains("crawlerId=" + MockCrawlerBuilder.CRAWLER_ID);
        assertThat(event.getCrawlEntry().getReference()).isEqualTo("someref");
        assertThat(event).hasToString("someref - somemessage");

        event = event(session, b -> b.crawlEntry(null));
        assertThat(event).hasToString("somemessage");

        event = event(session, b -> b.message(null));
        assertThat(event).hasToString("someref - somesubject");

        event = event(session, b -> b.message(null).source(session));
        assertThat(event.toString())
                .contains("someref",
                        "crawlerId=" + MockCrawlerBuilder.CRAWLER_ID);
    }

    private CrawlerEvent event(
            CrawlSession session,
            Consumer<CrawlerEvent.CrawlerEventBuilder<?, ?>> c) {
        CrawlerEvent.CrawlerEventBuilder<?, ?> b = CrawlerEvent.builder()
                .crawlEntry(new CrawlEntry("someref"))
                .exception(new CliException("someexception"))
                .message("somemessage")
                .name(CrawlerEvent.CRAWLER_CRAWL_BEGIN)
                .source("somesubject")
                .crawlSession(session);
        c.accept(b);
        return b.build();
    }
}
