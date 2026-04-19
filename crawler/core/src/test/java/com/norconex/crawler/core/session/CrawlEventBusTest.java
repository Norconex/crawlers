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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.event.CrawlerEvent;

@Timeout(30)
class CrawlEventBusTest {

    @Test
    void fire_dispatchesToRegisteredListener() {
        var received = new ArrayList<Event>();
        var manager = new EventManager();
        manager.addListener(received::add);
        var bus = new CrawlEventBus(manager);

        var config = new CrawlConfig();
        config.setId("test");
        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_SESSION_BEGIN)
                .source(config)
                .build();

        bus.fire(event);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getName())
                .isEqualTo(CrawlerEvent.CRAWLER_SESSION_BEGIN);
    }

    @Test
    void fire_noListeners_doesNotThrow() {
        var manager = new EventManager();
        var bus = new CrawlEventBus(manager);
        var config = new CrawlConfig();
        config.setId("test");
        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_SESSION_END)
                .source(config)
                .build();

        // should not throw
        bus.fire(event);
    }

    @Test
    void fire_multipleListeners_allReceive() {
        List<String> names = new ArrayList<>();
        var manager = new EventManager();
        manager.addListener(e -> names.add("A:" + e.getName()));
        manager.addListener(e -> names.add("B:" + e.getName()));
        var bus = new CrawlEventBus(manager);

        var config = new CrawlConfig();
        config.setId("test");
        bus.fire(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CLEAN_BEGIN)
                .source(config)
                .build());

        assertThat(names).containsExactlyInAnyOrder(
                "A:CRAWLER_CLEAN_BEGIN", "B:CRAWLER_CLEAN_BEGIN");
    }
}
