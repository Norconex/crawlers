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
package com.norconex.crawler.core.crawler.event.impl;

import static com.norconex.committer.core.CommitterEvent.COMMITTER_ACCEPT_YES;
import static com.norconex.committer.core.service.CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END;
import static com.norconex.crawler.core.crawler.CrawlerEvent.REJECTED_IMPORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.Stubber;
import com.norconex.crawler.core.TestUtil;
import com.norconex.crawler.core.crawler.event.impl.StopCrawlerOnMaxEventListener.OnMultiple;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.impl.ReferenceFilter;

class StopCrawlerOnMaxEventListenerTest {

    private static final String UPSERTED = COMMITTER_SERVICE_UPSERT_END;
    private static final String ACCEPTED = COMMITTER_ACCEPT_YES;
    private static final String UPSERTED_OR_REJECTED =
            COMMITTER_SERVICE_UPSERT_END + "|" + REJECTED_IMPORT;

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @CsvSource({
        "SUM, " + UPSERTED + ", 2, 2",                   // 1
        "SUM, NEVER_ENCOUNTERED_SO_WONT_STOP, 2, 4",     // 2
        "SUM, " + UPSERTED + "|" + ACCEPTED + ", 2, 1",  // 3
        "ANY, " + UPSERTED_OR_REJECTED + ", 2, 1",       // 4
        "SUM, " + UPSERTED_OR_REJECTED + ", 5, 3",       // 5
        "ALL, " + UPSERTED_OR_REJECTED + ", 3, 3",       // 6
    })
    void testStopCrawlerOnMaxEventListener(
            OnMultiple onMultiple,
            String eventMatch,
            int maximum,
            int expectedUpserts) {
        // prefixing with number to ensure they are retreived in same order
        //MAYBE: ensure crawl store behave like a FIFO queue?
        var crawlSession = Stubber.crawlSession(tempDir,
                "1-mock:reject-1",
                "2-mock:upsert-1",
                "3-mock:reject-2",
                "4-mock:upsert-2",
                "5-mock:upsert-3",
                "6-mock:reject-3",
                "7-mock:upsert-4");
        var crawlerCfg = TestUtil.getFirstCrawlerConfig(crawlSession);

        var listener = new StopCrawlerOnMaxEventListener();
        listener.setEventMatcher(TextMatcher.regex(eventMatch));
        listener.setMaximum(maximum);
        listener.setOnMultiple(onMultiple);
        crawlerCfg.addEventListener(listener);

        var filter = new ReferenceFilter(TextMatcher.wildcard("*mock:reject*"));
        filter.setOnMatch(OnMatch.EXCLUDE);

        crawlerCfg.getImporterConfig().setPreParseConsumer(
                HandlerConsumer.fromHandlers(filter));

        crawlSession.start();

        var mem = TestUtil.getFirstMemoryCommitter(crawlSession);

        assertThat(mem.getUpsertCount()).isEqualTo(expectedUpserts);
    }

    @Test
    void testWriteRead() {
        var listener = new StopCrawlerOnMaxEventListener();
        listener.setEventMatcher(TextMatcher.basic("blah"));
        listener.setMaximum(10);
        listener.setOnMultiple(OnMultiple.SUM);
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(listener, "listener"));
    }
}
