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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.CrawlerTestUtil;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListenerConfig.OnMultiple;
import com.norconex.crawler.core.stubs.CrawlerConfigStubs;
import com.norconex.crawler.core.stubs.CrawlerStubs;

class StopCrawlerOnMaxEventListenerTest {

    private static final String UPSERTED = COMMITTER_SERVICE_UPSERT_END;
    private static final String ACCEPTED = COMMITTER_ACCEPT_YES;
    private static final String UPSERTED_OR_REJECTED =
            COMMITTER_SERVICE_UPSERT_END + "|" + REJECTED_FILTER;

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @CsvSource(
        {
                "SUM, " + UPSERTED + ", 2, 2", // 1
                "SUM, NEVER_ENCOUNTERED_SO_WONT_STOP, 2, 4", // 2
                "SUM, " + UPSERTED + "|" + ACCEPTED + ", 2, 1", // 3
                "ANY, " + UPSERTED_OR_REJECTED + ", 2, 1", // 4
                "SUM, " + UPSERTED_OR_REJECTED + ", 5, 3", // 5
                "ALL, " + UPSERTED_OR_REJECTED + ", 3, 3", // 6
        }
    )
    void testStopCrawlerOnMaxEventListener(
            OnMultiple onMultiple,
            String eventMatch,
            int maximum,
            int expectedUpserts) {
        // prefixing with number to ensure they are retrieved in same order
        //MAYBE: ensure crawl store behave like a FIFO queue?

        var crawlerConfig = CrawlerConfigStubs
                .memoryCrawlerConfig(tempDir)
                .setStartReferences(
                        List.of(
                                "1-mock:reject-1",
                                "2-mock:upsert-1",
                                "3-mock:reject-2",
                                "4-mock:upsert-2",
                                "5-mock:upsert-3",
                                "6-mock:reject-3",
                                "7-mock:upsert-4"));

        var listener = new StopCrawlerOnMaxEventListener();
        listener.getConfiguration()
                .setEventMatcher(TextMatcher.regex(eventMatch))
                .setMaximum(maximum)
                .setOnMultiple(onMultiple);
        crawlerConfig.addEventListener(listener);

        var filter = new GenericReferenceFilter();
        filter.getConfiguration()
                .setValueMatcher(TextMatcher.wildcard("*mock:reject*"))
                .setOnMatch(OnMatch.EXCLUDE);
        crawlerConfig.setDocumentFilters(List.of(filter));

        var crawler = CrawlerStubs
                .memoryCrawlerBuilder(tempDir)
                .configuration(crawlerConfig)
                .build();

        crawler.start();

        var mem = CrawlerTestUtil.firstCommitter(crawler);

        assertThat(mem.getUpsertCount()).isEqualTo(expectedUpserts);
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
