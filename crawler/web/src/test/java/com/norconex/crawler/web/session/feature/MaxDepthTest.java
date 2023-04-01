/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.session.feature;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebsiteMock;

/**
 * Test that MaxDepth setting is respected.
 */
@MockServerSettings
class MaxDepthTest {

    @Test
    void testMaxDepth(ClientAndServer client) throws IOException {
        WebsiteMock.whenInfinitDepth(client);

        var mem = TestWebCrawlSession
                .forStartReferences(serverUrl(client, "/maxDepth/0000"))
                .crawlerSetup(cfg -> {
                    cfg.setMaxDepth(10);
                })
                .crawl();

        // 0-depth + 10 others == 11 expected files
        assertThat(mem.getRequestCount()).isEqualTo(11);
    }
}
