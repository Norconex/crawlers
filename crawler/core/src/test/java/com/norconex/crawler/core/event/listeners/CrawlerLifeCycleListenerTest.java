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
package com.norconex.crawler.core.event.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.event.CrawlerEvent;

class CrawlerLifeCycleListenerTest {

    MutableObject<String> method = new MutableObject<>();
    CrawlerLifeCycleListener listener = new CrawlerLifeCycleListener() {
        @Override
        protected void onCrawlerEvent(CrawlerEvent event) {
            method.setValue("onCrawlerEvent");
        }

        @Override
        protected void onCrawlerCrawlBegin(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerCrawlBegin");
        }

        @Override
        protected void onCrawlerCrawlEnd(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerCrawlEnd");
        }

        @Override
        protected void onCrawlerStopBegin(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerStopBegin");
        }

        @Override
        protected void onCrawlerStopEnd(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerStopEnd");
        }

        @Override
        protected void onCrawlerCleanBegin(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerCleanBegin");
        }

        @Override
        protected void onCrawlerCleanEnd(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerCleanEnd");
        }
    };

    @Test
    void testCrawlerLifeCycleListener() {
        assertThat(m(CrawlerEvent.CRAWLER_CRAWL_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerCrawlBegin");
        assertThat(m(CrawlerEvent.CRAWLER_CRAWL_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerCrawlEnd");
        assertThat(m(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerStopBegin");
        assertThat(m(CrawlerEvent.CRAWLER_STOP_REQUEST_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerStopEnd");
        assertThat(m(CrawlerEvent.CRAWLER_CLEAN_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerCleanBegin");
        assertThat(m(CrawlerEvent.CRAWLER_CLEAN_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerCleanEnd");

        // null
        method.setValue(null);
        listener.accept(null);
        assertThat(method.getValue()).isNull();

    }

    private String m(String eventName) {
        listener.accept(CrawlerEvent.builder()
                .name(eventName)
                .source("source")
                .build());
        return method.getValue();
    }
}
