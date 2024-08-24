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
        protected void onCrawlerShutdown(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerShutdown");
        }
        @Override
        protected void onCrawlerInitBegin(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerInitBegin");
        }
        @Override
        protected void onCrawlerInitEnd(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerInitEnd");
        }
        @Override
        protected void onCrawlerRunBegin(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerRunBegin");
        }
        @Override
        protected void onCrawlerRunEnd(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerRunEnd");
        }
        @Override
        protected void onCrawlerRunThreadBegin(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerRunThreadBegin");
        }
        @Override
        protected void onCrawlerRunThreadEnd(CrawlerEvent event) {
            method.setValue(method.getValue() + "+onCrawlerRunThreadEnd");
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
        assertThat(m(CrawlerEvent.CRAWLER_INIT_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerInitBegin");
        assertThat(m(CrawlerEvent.CRAWLER_INIT_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerInitEnd");
        assertThat(m(CrawlerEvent.CRAWLER_RUN_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerRunBegin");
        assertThat(m(CrawlerEvent.CRAWLER_RUN_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerRunEnd+onCrawlerShutdown");
        assertThat(m(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerRunThreadBegin");
        assertThat(m(CrawlerEvent.CRAWLER_RUN_THREAD_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerRunThreadEnd");
        assertThat(m(CrawlerEvent.CRAWLER_STOP_BEGIN)).isEqualTo(
                "onCrawlerEvent+onCrawlerStopBegin");
        assertThat(m(CrawlerEvent.CRAWLER_STOP_END)).isEqualTo(
                "onCrawlerEvent+onCrawlerStopEnd+onCrawlerShutdown");
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
