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
package com.norconex.crawler.fs.fetch.impl.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.fs.FsTestUtil;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractFileFetcherTest;
import com.norconex.crawler.fs.stubs.CrawlDocStubs;

class LocalFetcherTest extends AbstractFileFetcherTest {

    public static LocalFetcher fetcherClient() {
        return new LocalFetcher();
    }

    @Override
    protected Fetcher fetcher() {
        return fetcherClient();
    }

    @Override
    protected String getStartPath() {
        return StringUtils.removeEnd(
                Path.of(FsTestUtil.TEST_FS_PATH)
                        .toAbsolutePath().toUri().toString(),
                "/");
    }

    @Test
    void testAcceptRequestOfNetworkPathDrive() {
        assertThat(new LocalFetcher()
                .acceptRequest(mockRequest("Z:/\\\\path"))).isTrue();
    }

    private FileFetchRequest mockRequest(String ref) {
        return new FileFetchRequest(
                CrawlDocStubs.crawlDoc(ref, "content"),
                FetchDirective.DOCUMENT);
    }
}
