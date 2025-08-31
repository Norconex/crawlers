/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.core2.doc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.ledger.CrawlEntry;
import com.norconex.crawler.core2.ledger.ProcessingStatus;
import com.norconex.crawler.core2.mocks.crawler.MockCrawlerBuilder;

class CrawlDocLedgerTest {

    @TempDir
    private Path tempDir;

    @CrawlTest(focus = Focus.SESSION)
    void testCleanCrawl(CrawlSession session) {
        var ledger = session.getCrawlContext().getCrawlEntryLedger();
        assertThatNoException().isThrownBy(() -> {
            ledger.forEachPrevious(entry -> {});
            ledger.forEachProcessed(entry -> {});
            ledger.forEachQueued(entry -> {});
        });
    }

    @Test
    void testPersistence() {

        new MockCrawlerBuilder(tempDir)
                .build()
                .withCrawlSession(session -> {
                    var ledger1 =
                            session.getCrawlContext().getCrawlEntryLedger();
                    ledger1.queue(new CrawlEntry("ref:queue1"));
                    ledger1.queue(new CrawlEntry("ref:queue2"));

                    var entry = new CrawlEntry("ref:processed1");
                    entry.setProcessingStatus(ProcessingStatus.PROCESSED);
                    ledger1.updateEntry(entry);

                    entry = new CrawlEntry("ref:processed2");
                    entry.setProcessingStatus(ProcessingStatus.PROCESSED);
                    ledger1.updateEntry(entry);

                    entry = new CrawlEntry("ref:processed3");
                    entry.setProcessingStatus(ProcessingStatus.PROCESSED);
                    ledger1.updateEntry(entry);
                });

        // simulate resume

        new MockCrawlerBuilder(tempDir)
                .build()
                .withCrawlSession(session -> {
                    var ledger2 =
                            session.getCrawlContext().getCrawlEntryLedger();
                    assertThat(ledger2.getQueueCount())
                            .isEqualTo(2);
                    assertThat(ledger2.getProcessedCount())
                            .isEqualTo(3);
                });
    }
}
