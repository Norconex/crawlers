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

import static com.norconex.committer.core.CommitterEvent.COMMITTER_ACCEPT_YES;
import static com.norconex.committer.core.service.CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END;
import static com.norconex.crawler.core.event.CrawlerEvent.REJECTED_FILTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListenerConfig.OnMultiple;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;

@Timeout(30)
class StopCrawlerOnMaxEventListenerTest {

    private static final String UPSERTED = COMMITTER_SERVICE_UPSERT_END;
    private static final String ACCEPTED = COMMITTER_ACCEPT_YES;
    private static final String UPSERTED_OR_REJECTED =
            COMMITTER_SERVICE_UPSERT_END + "|" + REJECTED_FILTER;

    //Note, we test with 1 thread to ensure precise count in our tests
    private static final String CFG_TMPL = """
          numThreads: 1
          startReferences:
            - "1-mock:reject-1"
            - "2-mock:upsert-1"
            - "3-mock:reject-2"
            - "4-mock:upsert-2"
            - "5-mock:upsert-3"
            - "6-mock:reject-3"
            - "7-mock:upsert-4"
          documentFilters:
            - class: GenericReferenceFilter
              onMatch: EXCLUDE
              valueMatcher:
                method: WILDCARD
                pattern: "*mock:reject*"
          eventListeners:
            - class: StopCrawlerOnMaxEventListener
              eventMatcher:
                method: REGEX
                pattern: ${pattern}
              maximum: ${maximum}
              onMultiple: ${onMultiple}
      """;

    @CrawlTest(
        focus = Focus.CRAWL,
        config = CFG_TMPL,
        vars = { "pattern", UPSERTED, "maximum", "2", "onMultiple", "SUM" }
    )
    void testStopOnEventListener1(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(2);
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = CFG_TMPL,
        vars = {
                "pattern", "NEVER_ENCOUNTERED_SO_WONT_STOP",
                "maximum", "2",
                "onMultiple", "SUM"
        }
    )
    void testStopOnEventListener2(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(4);
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = CFG_TMPL,
        vars = {
                "pattern", UPSERTED + "|" + ACCEPTED,
                "maximum", "2",
                "onMultiple", "SUM"
        }
    )
    void testStopOnEventListener3(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(1);
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = CFG_TMPL,
        vars = {
                "pattern", UPSERTED_OR_REJECTED,
                "maximum", "2",
                "onMultiple", "ANY"
        }
    )
    void testStopOnEventListener4(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(1);
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = CFG_TMPL,
        vars = {
                "pattern", UPSERTED_OR_REJECTED,
                "maximum", "5",
                "onMultiple", "SUM"
        }
    )
    void testStopOnEventListener5(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(3);
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = CFG_TMPL,
        vars = {
                "pattern", UPSERTED_OR_REJECTED,
                "maximum", "3",
                "onMultiple", "ALL"
        }
    )
    void testStopOnEventListener6(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(3);
    }

    @Test
    void testWriteRead() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setEventMatcher(TextMatcher.basic("blah"))
                .setMaximum(10)
                .setOnMultiple(OnMultiple.SUM);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(listener));
    }
}
