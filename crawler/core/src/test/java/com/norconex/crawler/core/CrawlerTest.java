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
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.mutable.MutableObject;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.CrawlerConfig.OrphansStrategy;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.WithCrawlerTest;
import com.norconex.crawler.core.mocks.MockFetcher;
import com.norconex.crawler.core.mocks.MockNoopDataStore;
import com.norconex.crawler.core.mocks.MockNoopDataStoreEngine;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.impl.mvstore.MVStoreDataStoreEngine;
import com.norconex.crawler.core.stubs.CrawlerStubs;

class CrawlerTest {

    @TempDir
    private Path tempDir;

    @WithCrawlerTest(
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
    void testCrawlerError() {
        var exception = new MutableObject<Throwable>();
        var mem = CrawlerTestUtil.runWithConfig(
                tempDir,
                cfg -> cfg.setStartReferences(List.of("ref1", "ref2", "ref3"))
                        .setDataStoreEngine(new MVStoreDataStoreEngine() {
                            @Override
                            public <T> DataStore<T> openStore(
                                    String storeName, Class<? extends T> type) {
                                return new MockNoopDataStore<>() {
                                    @Override
                                    public Optional<T> deleteFirst() {
                                        throw new UnsupportedOperationException(
                                                "TEST");
                                    }
                                };
                            }
                        })
                        .setNumThreads(2)
                        .addEventListener(evt -> {
                            if (evt.is(CrawlerEvent.CRAWLER_ERROR)) {
                                exception.setValue(evt.getException());
                            }
                        }));

        assertThat(mem.getUpsertCount()).isZero();
        assertThat(exception.getValue()).isInstanceOf(
                UnsupportedOperationException.class);
        assertThat(exception.getValue().getMessage()).isEqualTo("TEST");
    }

    @Test
    void testDocumenetError() {
        var exception = new MutableObject<Throwable>();
        var stopped = new MutableObject<Boolean>();
        var mem = CrawlerTestUtil.runWithConfig(
                tempDir,
                cfg -> cfg.setStartReferences(List.of("ref1", "ref2", "ref3"))
                        .setNumThreads(2)
                        .setStopOnExceptions(
                                List.of(IllegalStateException.class))
                        .addEventListener(evt -> {
                            if (evt.is(CrawlerEvent.DOCUMENT_IMPORTED)) {
                                throw new IllegalStateException("DOC_ERROR");
                            }
                            if (evt.is(CrawlerEvent.REJECTED_ERROR)) {
                                exception.setValue(evt.getException());
                            }
                            if (evt.is(CrawlerEvent.CRAWLER_STOP_END)) {
                                stopped.setValue(true);
                            }
                        }));

        assertThat(mem.getUpsertCount()).isLessThan(3);
        assertThat(exception.getValue()).isInstanceOf(
                IllegalStateException.class);
        assertThat(exception.getValue().getMessage()).isEqualTo("DOC_ERROR");
        assertThat(stopped.getValue()).isTrue();
    }

    @Test
    void testMaxDocReached() {
        var stopped = new MutableObject<Boolean>();
        var mem = CrawlerTestUtil.runWithConfig(
                tempDir,
                cfg -> cfg.setStartReferences(List.of("ref1", "ref2", "ref3"))
                        .setNumThreads(2)
                        .setMaxDocuments(1)
                        .addEventListener(evt -> {
                            if (evt.is(CrawlerEvent.CRAWLER_STOP_END)) {
                                stopped.setValue(true);
                            }
                        }));

        assertThat(mem.getUpsertCount()).isLessThan(3);
        assertThat(stopped.getValue()).isTrue();
    }

    @Test
    void testAsyncQueueInit() {
        var mem = CrawlerTestUtil.runWithConfig(
                tempDir,
                cfg -> cfg.setStartReferences(
                        List.of(
                                "ref1", "ref2", "ref3", "ref4", "ref5", "ref6"))
                        .setNumThreads(2)
                        .setStartReferencesAsync(true));
        assertThat(mem.getUpsertCount()).isEqualTo(6);
    }

    @Test
    void testActiveTimeout() {
        var mem = CrawlerTestUtil.runWithConfig(
                tempDir,
                cfg -> cfg.setStartReferences(List.of("ref1", "ref2", "ref3"))
                        .setStartReferencesAsync(true)
                        // The mock datastore engine always return false
                        // for "isEmpty" so it means the "active" store and
                        // "queue" store both return false for isEmpty, causing
                        // it to loop forever.
                        .setDataStoreEngine(new MockNoopDataStoreEngine())
                        .setNumThreads(2)
                        .setIdleTimeout(Duration.ofMillis(100)));
        assertThat(mem.getUpsertCount()).isZero();
    }

    @Test
    void testLifeCycle() {
        var builder = CrawlerStubs.memoryCrawlerBuilder(tempDir);
        builder.fetcherProvider(
                crawler -> new MockFetcher().setRandomDocContent(true));
        builder.configuration()
                .setStartReferences(
                        List.of(
                                "mock:ref1", "mock:ref2", "mock:ref3",
                                "mock:ref4"))
                .setWorkDir(tempDir);
        var crawler1 = builder.build();
        var mem = CrawlerTestUtil.firstCommitter(crawler1);

        // Start
        crawler1.start();

        // All 4 docs must be committed and not be found in cache
        assertThat(mem.getAllRequests())
                .allMatch(req -> req.getReference().startsWith("mock:ref"))
                .allMatch(
                        req -> !req.getMetadata().getBoolean("mock.alsoCached"))
                .hasSize(4);

        // Export
        var exportDir = tempDir.resolve("exportdir");
        crawler1.exportDataStore(exportDir);

        // Clean
        crawler1.clean();

        // Import
        var exportFile = exportDir.resolve(CrawlerStubs.CRAWLER_ID + ".zip");
        crawler1.importDataStore(exportFile);

        // New session with 1 new 2 modified, and 1 orphan
        builder = CrawlerStubs.memoryCrawlerBuilder(tempDir);
        builder.fetcherProvider(
                crawler -> new MockFetcher().setRandomDocContent(true));
        builder.configuration()
                .setStartReferences(
                        List.of(
                                "mock:ref2", "mock:ref3", "mock:ref4",
                                "mock:ref5"))
                .setWorkDir(tempDir);
        var crawler2 = builder.build();
        mem = CrawlerTestUtil.firstCommitter(crawler2);

        // Start
        crawler2.start();

        // 5 docs must be committed:
        //    1 new
        //    3 modified (also cached)
        //    1 orphan (also cached)
        assertThat(mem.getAllRequests())
                .allMatch(req -> req.getReference().startsWith("mock:ref"))
                .hasSize(5)
                .areExactly(
                        4,
                        new Condition<>(
                                req -> req.getMetadata()
                                        .getBoolean("mock.alsoCached"),
                                ""))
                .areExactly(
                        1,
                        new Condition<>(
                                req -> req.getMetadata().getBoolean(
                                        "collector.is-crawl-new"),
                                ""))
                .map(CommitterRequest::getReference)
                // ref1 is last because orphans are processed last
                .containsExactly(
                        "mock:ref2", "mock:ref3", "mock:ref4",
                        "mock:ref5",
                        "mock:ref1");
    }

    @Test
    void testErrors() {
        assertThatExceptionOfType(CrawlerException.class).isThrownBy(
                () -> CrawlerTestUtil.runWithConfig(
                        tempDir, cfg -> cfg.setId(null).setWorkDir(tempDir)));
    }

    @Test
    void testOrphanDeletion() {
        var builder = CrawlerStubs.memoryCrawlerBuilder(tempDir);
        builder.fetcherProvider(
                crawler -> new MockFetcher().setRandomDocContent(true));
        builder.configuration()
                .setStartReferences(
                        List.of(
                                "mock:ref1", "mock:ref2", "mock:ref3",
                                "mock:ref4"));
        var crawler = builder.build();

        crawler.start();

        // New session with 1 new 2 modified, and 1 orphan
        crawler = CrawlerStubs.memoryCrawler(
                tempDir,
                cfg -> cfg.setStartReferences(
                        List.of(
                                "mock:ref2", "mock:ref3", "mock:ref4",
                                "mock:ref5"))
                        .setOrphansStrategy(OrphansStrategy.DELETE));

        crawler.start();

        var mem = CrawlerTestUtil.firstCommitter(crawler);

        assertThat(mem.getAllRequests())
                .allMatch(req -> req.getReference().startsWith("mock:ref"))
                .hasSize(5);

        assertThat(mem.getUpsertCount()).isEqualTo(4);
        assertThat(mem.getDeleteCount()).isEqualTo(1);
        assertThat(mem.getDeleteRequests().get(0).getReference())
                .isEqualTo("mock:ref1");
    }
}
