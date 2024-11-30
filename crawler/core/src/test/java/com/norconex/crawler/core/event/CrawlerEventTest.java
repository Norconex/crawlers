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
package com.norconex.crawler.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cli.CliException;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.mocks.crawler.MockCrawler;

class CrawlerEventTest {

    @CrawlTest
    void testCrawlerEvent(CrawlerContext ctx) {
        var event = event(ctx, b -> {});

        //        assertThat(event.isCrawlerShutdown()).isFalse();
        assertThat(event.getSubject()).hasToString("somesubject");
        assertThat(event.getSource()).hasToString(MockCrawler.CRAWLER_ID);
        assertThat(event.getDocContext().getReference()).isEqualTo("someref");
        assertThat(event).hasToString("someref - somemessage");

        event = event(ctx, b -> b.docContext(null));
        assertThat(event).hasToString("somemessage");

        event = event(ctx, b -> b.message(null));
        assertThat(event).hasToString("someref - somesubject");

        event = event(ctx, b -> b.message(null).subject(null));
        assertThat(event).hasToString("someref - " + MockCrawler.CRAWLER_ID);
    }

    private CrawlerEvent event(
            CrawlerContext crawlerContext,
            Consumer<CrawlerEvent.CrawlerEventBuilder<?, ?>> c) {
        CrawlerEvent.CrawlerEventBuilder<?, ?> b = CrawlerEvent.builder()
                .docContext(new CrawlDocContext("someref"))
                .exception(new CliException("someexception"))
                .message("somemessage")
                .name(CrawlerEvent.CRAWLER_CRAWL_BEGIN)
                .source(crawlerContext)
                .subject("somesubject");
        c.accept(b);
        return b.build();
    }
}
