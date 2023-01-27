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
package com.norconex.crawler.core.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.MockQueueInitializer;
import com.norconex.crawler.core.TestUtil;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.MockDataStore;
import com.norconex.crawler.core.store.MockDataStoreEngine;
import com.norconex.crawler.core.store.impl.mvstore.MVStoreDataStoreEngine;

class CrawlerThreadTest {

    @TempDir
    private Path tempDir;

    @Test
    void testNormalRun() {
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> cfg.setNumThreads(2),
                "ref1", "ref2", "ref3");
        assertThat(mem.getUpsertCount()).isEqualTo(3);
    }

    @Test
    void testCrawlerError() {
        var exception = new MutableObject<Throwable>();
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> {
                    cfg.setNumThreads(2);
                    cfg.setDataStoreEngine(new MVStoreDataStoreEngine() {
                        @Override
                        public <T> DataStore<T> openStore(
                                String storeName, Class<? extends T> type) {
                            return new MockDataStore<>() {
                                @Override
                                public Optional<T> deleteFirst() {
                                    throw new UnsupportedOperationException(
                                            "TEST");
                                }
                            };
                        }
                    });
                    cfg.addEventListener(evt -> {
                        if (evt.is(CrawlerEvent.CRAWLER_ERROR)) {
                            exception.setValue(evt.getException());
                        }
                    });
                },
                "ref1", "ref2", "ref3");
        assertThat(mem.getUpsertCount()).isZero();
        assertThat(exception.getValue()).isInstanceOf(
                UnsupportedOperationException.class);
        assertThat(exception.getValue().getMessage()).isEqualTo("TEST");
    }

    @Test
    void testDocumenetError() {
        var exception = new MutableObject<Throwable>();
        var stopped = new MutableObject<Boolean>();
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> {
                    cfg.setNumThreads(2);
                    cfg.setStopOnExceptions(
                            List.of(IllegalStateException.class));
                    cfg.addEventListener(evt -> {
                        if (evt.is(CrawlerEvent.DOCUMENT_IMPORTED)) {
                            throw new IllegalStateException("DOC_ERROR");
                        }
                        if (evt.is(CrawlerEvent.REJECTED_ERROR)) {
                            exception.setValue(evt.getException());
                        }
                        if (evt.is(CrawlerEvent.CRAWLER_STOP_END)) {
                            stopped.setValue(true);
                        }
                    });
                },
                "ref1", "ref2", "ref3");
        assertThat(mem.getUpsertCount()).isLessThan(3);
        assertThat(exception.getValue()).isInstanceOf(
                IllegalStateException.class);
        assertThat(exception.getValue().getMessage()).isEqualTo("DOC_ERROR");
        assertThat(stopped.getValue()).isTrue();
    }

    @Test
    void testMaxDocReached() {
        var stopped = new MutableObject<Boolean>();
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> {
                    cfg.setNumThreads(2);
                    cfg.setMaxDocuments(1);
                    cfg.addEventListener(evt -> {
                        if (evt.is(CrawlerEvent.CRAWLER_STOP_END)) {
                            stopped.setValue(true);
                        }
                    });
                },
                "ref1", "ref2", "ref3");
        assertThat(mem.getUpsertCount()).isLessThan(3);
        assertThat(stopped.getValue()).isTrue();
    }

    @Test
    void testAsyncQueueInit() {
        String[] refs = { "ref1", "ref2", "ref3", "ref4", "ref5", "ref6" };
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> {
                    cfg.setNumThreads(2);
                },
                implBuilder -> {
                    var mqi = new MockQueueInitializer(refs);
                    mqi.setAsync(true);
                    mqi.setDelay(150);
                    implBuilder.queueInitializer(mqi);
                });
        assertThat(mem.getUpsertCount()).isEqualTo(6);
    }

    @Test
    void testActiveTimeout() {
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> {
                    cfg.setNumThreads(2);
                    // The mock datastore engine always return false
                    // for "isEmpty" so it means the "active" store and
                    // "queue" store both return false for isEmpty, causing
                    // it to loop forever.
                    cfg.setDataStoreEngine(new MockDataStoreEngine());
                    cfg.setIdleTimeout(Duration.ofMillis(100));
                },
                implBuilder -> {
                    var mqi = new MockQueueInitializer("ref1", "ref2", "ref3");
                    mqi.setAsync(true);
                    mqi.setDelay(500);
                    implBuilder.queueInitializer(mqi);
                });
        assertThat(mem.getUpsertCount()).isZero();
    }
}
