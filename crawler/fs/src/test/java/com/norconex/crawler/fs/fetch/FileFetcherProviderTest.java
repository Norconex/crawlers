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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileFetcherProviderTest {

    @TempDir
    private Path tempDir;

    @Test
    void test() {
        throw new UnsupportedOperationException("Implement me");
        /*
        var crawlerCfg = CrawlerConfigStubs
                .memoryCrawlerConfig(tempDir);
        var p = new FileFetcherProvider();
        
        var crawler = FsCrawler.create(crawlerCfg);
        
        assertThat(p.apply(crawler.getContext()).getFetchers())
                .containsExactly(new LocalFetcher()); // default fetcher
        
        crawlerCfg.setFetchers(List.of(new HdfsFetcher(), new SmbFetcher()));
        crawler = FsCrawler.create(crawlerCfg);
        assertThat(p.apply(crawler.getContext()).getFetchers())
                .containsExactly(new HdfsFetcher(), new SmbFetcher());
                */
    }
}
