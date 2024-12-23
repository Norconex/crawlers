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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;

/**
 * Test that the user agent is sent properly to web servers with the
 * default HTTP Fetcher.
 */
@MockServerSettings
class UserAgentTest {
    @WebCrawlTest
    void testUserAgent(ClientAndServer client, WebCrawlerConfig cfg) {

        var path = "/userAgent";
        client
                .when(request(path))
                .respond(req -> response()
                        .withBody("The user agent is: "
                                + req.getFirstHeader("User-Agent")));

        cfg.setStartReferences(
                List.of(MockWebsite.serverUrl(client, path)));
        WebTestUtil.firstHttpFetcherConfig(cfg).setUserAgent("Smith");
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getUpsertRequests())
                .map(WebTestUtil::docText)
                .map(StringUtils::trim)
                .containsExactly("The user agent is: Smith");
    }
}
