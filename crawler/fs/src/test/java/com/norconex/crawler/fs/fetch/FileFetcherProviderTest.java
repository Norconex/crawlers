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
package com.norconex.crawler.fs.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.fs.FsCrawler;
import com.norconex.crawler.fs.fetch.impl.hdfs.HdfsFetcher;
import com.norconex.crawler.fs.fetch.impl.local.LocalFetcher;
import com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher;
import com.norconex.crawler.fs.stubs.CrawlerConfigStubs;

class FileFetcherProviderTest {

    @TempDir
    private Path tempDir;

    @Test
    void test() {
        var crawlerCfg = CrawlerConfigStubs
                .memoryCrawlerConfig(tempDir);
        var p = new FileFetcherProvider();

        var crawler = FsCrawler.create(crawlerCfg);

        assertThat(p.apply(crawler.getCrawlerContext()).getFetchers())
                .containsExactly(new LocalFetcher()); // default fetcher

        crawlerCfg.setFetchers(List.of(new HdfsFetcher(), new SmbFetcher()));
        crawler = FsCrawler.create(crawlerCfg);
        assertThat(p.apply(crawler.getCrawlerContext()).getFetchers())
                .containsExactly(new HdfsFetcher(), new SmbFetcher());
    }
}
