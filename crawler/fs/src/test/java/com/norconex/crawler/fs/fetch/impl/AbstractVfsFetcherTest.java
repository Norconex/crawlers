/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.fs.fetch.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.apache.commons.io.input.BrokenInputStream;
import org.apache.commons.vfs2.FileSystemOptions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.stubs.CrawlDocStubs;
import com.norconex.importer.doc.DocContext;

import lombok.Data;
import lombok.NoArgsConstructor;

class AbstractVfsFetcherTest {

    @Test
    void testNullFetcherShutdown() {
        assertThatNoException().isThrownBy(() -> {
            new MockVfsFetcher().fetcherShutdown(null);
        });
    }

    @Test
    void testNoInitFetchMustThrow() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy( //NOSONAR
                () -> {
                    new MockVfsFetcher().fetch(mockRequest());
                });
    }

    @Test
    void testBadRequestMustThrow() {
        var fetcher = new MockVfsFetcher();
        fetcher.fetcherStartup(null);
        assertThatExceptionOfType(FetchException.class).isThrownBy( //NOSONAR
                () -> {
                    fetcher.fetch(mockFailingRequest());
                }).withMessageContaining("Could not fetch reference:");
    }

    @Test
    void testFetchChildPathsWithBadParentMustThrow() {
        var fetcher = new MockVfsFetcher();
        fetcher.fetcherStartup(null);
        assertThatExceptionOfType(FetchException.class).isThrownBy( //NOSONAR
                () -> {
                    fetcher.fetchChildPaths("i/dont/exist");
                }).withMessageContaining("Could not fetch child paths of:");
    }

    @Data
    @NoArgsConstructor
    class MockVfsFetcher extends AbstractVfsFetcher<BaseFetcherConfig> {

        private final BaseFetcherConfig configuration = new BaseFetcherConfig();

        @Override
        protected void applyFileSystemOptions(FileSystemOptions opts) {
            // NOOP
        }

        @Override
        public BaseFetcherConfig getConfiguration() {
            return configuration;
        }
    }

    private FileFetchRequest mockRequest() {
        return new FileFetchRequest(
                CrawlDocStubs.crawlDoc("ref", "content"),
                FetchDirective.DOCUMENT);
    }

    private FileFetchRequest mockFailingRequest() {
        return new FileFetchRequest(
                new CrawlDoc(new DocContext("ref"),
                        CachedInputStream.cache(BrokenInputStream.INSTANCE)),
                FetchDirective.DOCUMENT);
    }

}
