/* Copyright 2025-2026 Norconex Inc.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;

@Timeout(30)
class CrawlerEventTest {

    @Test
    void sessionConstantsExist() {
        assertThat(CrawlerEvent.CRAWLER_SESSION_BEGIN)
                .isEqualTo("CRAWLER_SESSION_BEGIN");
        assertThat(CrawlerEvent.CRAWLER_SESSION_END)
                .isEqualTo("CRAWLER_SESSION_END");
    }

    @Test
    void commandConstantsExist() {
        assertThat(CrawlerEvent.CRAWLER_COMMAND_BEGIN)
                .isEqualTo("CRAWLER_COMMAND_BEGIN");
        assertThat(CrawlerEvent.CRAWLER_COMMAND_END)
                .isEqualTo("CRAWLER_COMMAND_END");
    }

    @Test
    void documentProcessingConstantsExist() {
        assertThat(CrawlerEvent.DOCUMENT_PROCESSING_BEGIN)
                .isEqualTo("DOCUMENT_PROCESSING_BEGIN");
        assertThat(CrawlerEvent.DOCUMENT_PROCESSING_END)
                .isEqualTo("DOCUMENT_PROCESSING_END");
    }

    @Test
    void documentFinalizingConstantsExist() {
        assertThat(CrawlerEvent.DOCUMENT_FINALIZING_BEGIN)
                .isEqualTo("DOCUMENT_FINALIZING_BEGIN");
        assertThat(CrawlerEvent.DOCUMENT_FINALIZING_END)
                .isEqualTo("DOCUMENT_FINALIZING_END");
    }

    @Test
    void commandClass_setViaBuilder() {
        var config = new CrawlConfig();
        config.setId("test");
        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_COMMAND_BEGIN)
                .source(config)
                .commandClass(CrawlCommand.class)
                .build();

        assertThat(event.getCommandClass()).isEqualTo(CrawlCommand.class);
    }

    @Test
    void commandClass_defaultNull_forNonCommandEvent() {
        var config = new CrawlConfig();
        config.setId("test");
        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_SESSION_BEGIN)
                .source(config)
                .build();

        assertThat(event.getCommandClass()).isNull();
    }

    @Test
    void getName_returnsEventName() {
        var config = new CrawlConfig();
        config.setId("test");
        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CRAWL_BEGIN)
                .source(config)
                .build();

        assertThat(event.getName()).isEqualTo(CrawlerEvent.CRAWLER_CRAWL_BEGIN);
    }

    @Test
    void toString_containsExpectedInfo() {
        var config = new CrawlConfig();
        config.setId("myConfig");
        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_SESSION_BEGIN)
                .source(config)
                .build();

        assertThat(event.toString()).isNotBlank();
    }
}
