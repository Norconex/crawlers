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
package com.norconex.crawler.fs.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.fs.FsTestUtil;
import com.norconex.crawler.fs.TestFsCrawlSession;
import com.norconex.crawler.fs.crawler.impl.FsCrawlerImplFactory;
import com.norconex.crawler.fs.fetch.impl.hdfs.HdfsFetcher;
import com.norconex.crawler.fs.fetch.impl.local.LocalFetcher;
import com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher;

class FileFetcherProviderTest {

    @Test
    void test() {
        var sess = TestFsCrawlSession.forStartPaths().crawlSession();
        var crawlerCfg = FsTestUtil.getFirstCrawlerConfig(sess);
        var p = new FileFetcherProvider();

        var crawler = Crawler.builder()
                .crawlerConfig(crawlerCfg)
                .crawlSession(sess)
                .crawlerImpl(FsCrawlerImplFactory.create())
                .build();

        assertThat(p.apply(crawler).getFetchers())
            .containsExactly(new LocalFetcher()); // default fetcher

        crawlerCfg.setFetchers(List.of(new HdfsFetcher(), new SmbFetcher()));
        crawler = Crawler.builder()
                .crawlerConfig(crawlerCfg)
                .crawlSession(sess)
                .crawlerImpl(FsCrawlerImplFactory.create())
                .build();
        assertThat(p.apply(crawler).getFetchers())
            .containsExactly(new HdfsFetcher(), new SmbFetcher());
    }
}
