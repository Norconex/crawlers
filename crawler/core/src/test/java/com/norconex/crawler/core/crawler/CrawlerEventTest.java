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
package com.norconex.crawler.core.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.crawler.CrawlerEvent.CrawlerEventBuilder;

class CrawlerEventTest {

    private static Crawler crawler;

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) {
        crawler = CoreStubber.crawler(tempDir);
    }

    @Test
    void testCrawlerEvent() {
        var event = event(b -> {});

        assertThat(event.isCrawlerShutdown()).isFalse();
        assertThat(event.getSubject()).hasToString("somesubject");
        assertThat(event.getSource()).hasToString("MockCrawler[test-crawler]");
        assertThat(
                event.getCrawlDocRecord().getReference()).isEqualTo("someref");
        assertThat(event).hasToString("someref - somemessage");

        event = event(b -> b.crawlDocRecord(null));
        assertThat(event).hasToString("somemessage");

        event = event(b -> b.message(null));
        assertThat(event).hasToString("someref - somesubject");

        event = event(b -> b.message(null).subject(null));
        assertThat(event).hasToString("someref - MockCrawler[test-crawler]");
    }

    private CrawlerEvent event(Consumer<CrawlerEventBuilder<?, ?>>  c) {
        CrawlerEventBuilder<?, ?> b = CrawlerEvent.builder()
            .crawlDocRecord(CoreStubber.crawlDocRecord("someref"))
            .exception(new CrawlerException("someexception"))
            .message("somemessage")
            .name(CrawlerEvent.CRAWLER_RUN_BEGIN)
            .source(crawler)
            .subject("somesubject");
        c.accept(b);
        return b.build();
    }
}
