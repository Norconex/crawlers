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
package com.norconex.crawler.fs.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.fs.fetch.impl.hdfs.HdfsFetcher;
import com.norconex.crawler.fs.fetch.impl.local.LocalFetcher;
import com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher;
import com.norconex.crawler.fs.mock.MockFsCrawlerBuilder;
import com.norconex.crawler.fs.stubs.CrawlDocStubs;
import com.norconex.crawler.fs.stubs.CrawlerConfigStubs;

class FileFetcherProviderTest {

    @TempDir
    private Path tempDir;

    @Test
    void testApply() {
        var crawlerCfg = CrawlerConfigStubs.memoryCrawlerConfig(tempDir);
        var p = new FileFetcherProvider();

        new MockFsCrawlerBuilder(tempDir)
                .config(crawlerCfg)
                .withCrawlerContext(ctx -> {
                    assertThat(p.apply(ctx).getFetchers())
                            // default fetcher
                            .containsExactly(new LocalFetcher());
                    crawlerCfg.setFetchers(
                            List.of(new HdfsFetcher(), new SmbFetcher()));
                    assertThat(p.apply(ctx).getFetchers())
                            .containsExactly(new HdfsFetcher(),
                                    new SmbFetcher());
                    return null;
                });

    }

    @Test
    void testError() throws FetchException {
        var request = new FileFetchRequest(
                CrawlDocStubs.crawlDoc(tempDir.resolve("na").toString()),
                FetchDirective.DOCUMENT);

        var fetcher = mock(LocalFetcher.class);
        when(fetcher.accept(request)).thenReturn(true);
        when(fetcher.fetch(request)).thenThrow(
                new RuntimeException("simulated error"));

        var crawlerCfg = CrawlerConfigStubs.memoryCrawlerConfig(tempDir);
        crawlerCfg.setFetchers(List.of(fetcher));

        var p = new FileFetcherProvider();
        new MockFsCrawlerBuilder(tempDir)
                .config(crawlerCfg)
                .withInitializedCrawlerContext(crawlCtx -> {
                    var multiFetcher = p.apply(crawlCtx);
                    var response = multiFetcher.fetch(request);
                    assertThat(response.getException()).isNotNull();
                    return null;
                });
    }

}
