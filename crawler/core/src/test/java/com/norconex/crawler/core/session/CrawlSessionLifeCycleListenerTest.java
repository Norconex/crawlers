/* Copyright 2022-2022 Norconex Inc.
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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class CrawlSessionLifeCycleListenerTest {

    static List<String> eventNames = Arrays.asList(
            CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN,
            CrawlSessionEvent.CRAWLSESSION_RUN_END,
            CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN,
            CrawlSessionEvent.CRAWLSESSION_STOP_END,
            CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN,
            CrawlSessionEvent.CRAWLSESSION_CLEAN_END,
            CrawlSessionEvent.CRAWLSESSION_ERROR
    );


    @Test
    void testAccept() {
        // expects count to be:
        //     7 -> number of of time onCrawlSessionEvent was invoked
        //   + 3 -> number of of time onCrawlSessionShutdown was invoked
        //   + 7 -> one time for each other methods
        //   ====
        //    17
        ListenerTester lt = new ListenerTester();
        eventNames.forEach(n -> lt.accept(
                CrawlSessionEvent.builder().name(n).source("blah").build()));

        assertThat(lt.count).isEqualTo(17);
        assertThatNoException().isThrownBy(() -> lt.accept(null));
    }

    static class ListenerTester extends CrawlSessionLifeCycleListener {
        private int count;

        private void assertOneOf(CrawlSessionEvent actual, String... expected) {
            assertThat(StringUtils.equalsAny(
                    actual.getName(), expected)).isTrue();
            count++;
        }

        @Override
        protected void onCrawlSessionEvent(CrawlSessionEvent event) {
            assertOneOf(event, eventNames.toArray(EMPTY_STRING_ARRAY));
        }
        @Override
        protected void onCrawlSessionShutdown(CrawlSessionEvent event) {
            assertOneOf(event,
                    CrawlSessionEvent.CRAWLSESSION_RUN_END,
                    CrawlSessionEvent.CRAWLSESSION_STOP_END,
                    CrawlSessionEvent.CRAWLSESSION_ERROR);
        }
        @Override
        protected void onCrawlSessionError(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_ERROR);
        }
        @Override
        protected void onCrawlSessionRunBegin(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN);
        }
        @Override
        protected void onCrawlSessionRunEnd(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_RUN_END);
        }
        @Override
        protected void onCrawlSessionStopBegin(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN);
        }
        @Override
        protected void onCrawlSessionStopEnd(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_STOP_END);
        }
        @Override
        protected void onCrawlSessionCleanBegin(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN);
        }
        @Override
        protected void onCrawlSessionCleanEnd(CrawlSessionEvent event) {
            assertOneOf(event, CrawlSessionEvent.CRAWLSESSION_CLEAN_END);
        }
    }
}
