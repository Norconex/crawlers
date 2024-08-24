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

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebTestUtil;

/**
 * Test that the user agent is sent properly to web servers with the
 * default HTTP Fetcher.
 */
@MockServerSettings
class UserAgentTest {
    @TempDir
    private Path tempDir;

    @Test
    void testUserAgent(ClientAndServer client) {

        var path = "/userAgent";
        client
            .when(request(path))
            .respond(req -> response()
                    .withBody("The user agent is: "
                            + req.getFirstHeader("User-Agent")));

        var mem = WebTestUtil.runWithConfig(tempDir, cfg -> {
            cfg.setStartReferences(List.of(serverUrl(client, path)));
            WebTestUtil.firstHttpFetcherConfig(cfg).setUserAgent("Smith");
        });

        assertThat(mem.getUpsertRequests())
            .map(WebTestUtil::docText)
            .map(StringUtils::trim)
            .containsExactly("The user agent is: Smith");
    }
}
