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
package com.norconex.crawler.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTestCapturer;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.mocks.grid.MockFailingGridConnector;

// Misc tests not fitting anywhere else
class CrawlerTest {

    @TempDir
    private Path tempDir;

    @CrawlTest(
        run = true,
        config = """
            numThreads: 2
            startReferences: [ref1,ref2,ref3]
            """
    )
    void testNormalRun(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(3);
    }

    @Test
    void testCrawlerException() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> { //NOSONAR
                    new MockCrawlerBuilder(tempDir)
                            .configModifier(cfg -> cfg.setGridConnector(
                                    new MockFailingGridConnector()))
                            .crawler()
                            .crawl();
                }).withMessage("IN_TEST");
    }

    @Test
    void testDocumentError() throws Exception {
        var exception = new MutableObject<Throwable>();

        var crawler = new MockCrawlerBuilder(tempDir)
                .configModifier(cfg -> cfg
                        .setStartReferences(List.of("ref1", "ref2", "ref3"))
                        .setNumThreads(2)
                        .addEventListener(evt -> {
                            if (evt.is(CrawlerEvent.DOCUMENT_IMPORTED)) {
                                throw new IllegalStateException("DOC_ERROR");
                            }
                            if (evt.is(CrawlerEvent.REJECTED_ERROR)) {
                                exception.setValue(evt.getException());
                            }
                        })
                        .setStopOnExceptions(
                                List.of(IllegalStateException.class)))
                .crawler();

        var captures = CrawlTestCapturer.capture(crawler, Crawler::crawl);

        var mem = captures.getCommitter();

        assertThat(mem.getUpsertCount()).isLessThan(3);
        assertThat(exception.getValue()).isInstanceOf(
                IllegalStateException.class);
        assertThat(exception.getValue().getMessage())
                .isEqualTo("DOC_ERROR");
    }

    @CrawlTest(
        run = true,
        config = """
                numThreads: 2
                startReferences: [ref1,ref2,ref3]
                maxDocuments: 1
                """
    )
    void testMaxDocReached(MemoryCommitter mem) throws Exception {
        assertThat(mem.getUpsertCount()).isLessThan(3);
    }

    @CrawlTest(
        run = true,
        config = """
                numThreads: 2
                startReferences: [ref1,ref2,ref3,ref4,ref5,ref6]
                startReferencesAsync: true
                """
    )
    void testAsyncQueueInit(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(6);
    }
}
