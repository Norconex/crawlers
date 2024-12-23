/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.feature;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;

/**
 * Test that MaxDepth setting is respected.
 */
@MockServerSettings
class MaxDepthTest {

    @WebCrawlTest
    void testMaxDepth(ClientAndServer client, WebCrawlerConfig cfg) {
        MockWebsite.whenInfiniteDepth(client);

        cfg.setStartReferences(
                List.of(serverUrl(client, "/maxDepth/0000")));
        cfg.setMaxDepth(10);
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        // 0-depth + 10 others == 11 expected files
        assertThat(mem.getRequestCount()).isEqualTo(11);
    }
}
