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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.operations.canon.impl.GenericCanonicalLinkDetector;

/**
 * The tail of redirects should be kept as metadata so implementors
 * can know where documents came from. This test runs twice. First starts with
 * with canonical to redirect, then redirect to canonical.
 */
@MockServerSettings
class CanonicalRedirectLoopTest {

    private static final String CANONICAL_PATH = "/canonical";
    private static final String REDIRECT_PATH = "/redirect";

    @WebCrawlTest
    void testStartWithCanonical(
            ClientAndServer client, WebCrawlerConfig cfg) throws IOException {
        testLoop(CANONICAL_PATH, client, cfg);
    }

    @WebCrawlTest
    void testStartWithRedirect(
            ClientAndServer client, WebCrawlerConfig cfg) throws IOException {
        testLoop(REDIRECT_PATH, client, cfg);
    }

    void testLoop(
            String startUrlPath,
            ClientAndServer client,
            WebCrawlerConfig config) throws IOException {

        //--- Web site ---

        // @formatter:off
        client
            .when(request()
                .withPath(CANONICAL_PATH))
            .respond(response()
                .withHeader("Link", "<%s>; rel=\"canonical\""
                        .formatted(serverUrl(client, REDIRECT_PATH)))
                .withBody("""
                    <h1>Canonical-redirect circular reference.</h1>
                    <p>This page has a canonical URL in the HTTP header
                    that points to a page that redirects back to this
                    one (loop). The crawler should be smart enough
                    to pick one and not enter in an infinite loop.</p>"""));

        client
            .when(request()
                .withPath(REDIRECT_PATH))
            .respond(response()
                .withStatusCode(302)
                .withHeader("Location", serverUrl(client, CANONICAL_PATH)));
        // @formatter:on

        //--- Crawler Setup/Run ---

        config.setStartReferences(List.of(serverUrl(client, startUrlPath)));
        WebTestUtil.ignoreAllIgnorables(config);
        config.setCanonicalLinkDetector(new GenericCanonicalLinkDetector());

        var captures = WebCrawlTestCapturer.crawlAndCapture(config);

        //--- Assertions ---
        assertThat(captures.getCommitter().getUpsertRequests()).hasSize(1);

        var doc = captures.getCommitter().getUpsertRequests().get(0);
        var content = IOUtils.toString(doc.getContent(), UTF_8);

        assertThat(content).contains("Canonical-redirect circular reference");
        assertThat(doc.getReference()).contains(CANONICAL_PATH);

        assertThat(doc.getMetadata().getStrings(WebDocMetadata.REDIRECT_TRAIL))
                .containsExactly(serverUrl(client, REDIRECT_PATH));
    }
}
