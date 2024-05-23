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
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;

/**
 * Test proper handling of page timeouts.
 */
// Test for https://github.com/Norconex/collector-http/issues/316
@MockServerSettings
class TimeoutTest {

    private final String basePath = "/timeout";
    private final String homePath = basePath + "/index.html";

    @Test
    void testTimeout(ClientAndServer client) throws CommitterException {

        var crawlSession = TestWebCrawlSession
                .forStartReferences(serverUrl(client, homePath))
                .crawlerSetup(cfg -> {
                    var f = new GenericHttpFetcher();
                    f.getConfiguration().setConnectionTimeout(ofSeconds(1));
                    f.getConfiguration().setSocketTimeout(ofSeconds(1));
                    f.getConfiguration().setConnectionRequestTimeout(
                            ofSeconds(1));
                    cfg.setFetchers(List.of(f));
                })
                .crawlSession();
        var mem = WebTestUtil.getFirstMemoryCommitter(crawlSession);

        whenDelayed(client, 0);
        crawlSession.start();
        assertThat(mem.getUpsertRequests())
            .map(UpsertRequest::getReference)
            .containsExactlyInAnyOrder(
                    serverUrl(client, homePath),
                    serverUrl(client, basePath + "/child1.html"),
                    serverUrl(client, basePath + "/child2.html"));
        mem.clean();

        // First page should be skipped (timeout) but not children... even
        // if we could not go through parent to get to them
        whenDelayed(client, 2000);
        crawlSession.start();
        assertThat(mem.getUpsertRequests())
            .map(UpsertRequest::getReference)
            .containsExactlyInAnyOrder(
                    serverUrl(client, basePath + "/child1.html"),
                    serverUrl(client, basePath + "/child2.html"));
        mem.clean();

        crawlSession.clean();
    }

    private void whenDelayed(ClientAndServer client, long delay) {
        client.reset();
        client
            .when(request(homePath))
            .respond(response()
                .withDelay(TimeUnit.MILLISECONDS, delay)
                .withBody("""
                    <p>This page takes %s milliseconds to return to test
                    timeouts, the 2 links below should return right away and
                    have a modified content each time accessed:
                    <ul>
                      <li><a href="child1.html">Timeout child page 1</a></li>
                      <li><a href="child2.html">Timeout child page 2</a></li>
                    </ul>
                    """));

        client
            .when(request("!" + homePath))
            .respond(response()
                .withBody("""
                    <h1>Random test child page</h1>
                    <p>This page content is never the same.</p>
                    <p>Salt: %s</p>"
                    """.formatted(delay)
                ));
    }
}
