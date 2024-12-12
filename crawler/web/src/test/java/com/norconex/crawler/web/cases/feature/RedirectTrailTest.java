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
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;

import org.apache.commons.lang3.math.NumberUtils;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;

/**
 * The tail of redirects should be kept as metadata so implementors
 * can know where documents came from.
 */
@MockServerSettings
class RedirectTrailTest {

    private static final String PATH = "/redirectTrail";

    @WebCrawlTest
    void testRedirectTrailTest(ClientAndServer client, WebCrawlerConfig cfg) {

        client
                .when(request(PATH))
                .respond(HttpClassCallback.callback(Callback.class));

        var baseUrl = serverUrl(client, PATH);

        cfg.setStartReferences(List.of(baseUrl + "?index=0"));
        cfg.setMaxDepth(0);
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getUpsertCount()).isOne();

        var doc = mem.getUpsertRequests().get(0);
        assertThat(doc.getMetadata().getStrings(WebDocMetadata.REDIRECT_TRAIL))
                .containsExactly(
                        baseUrl + "?index=0",
                        baseUrl + "?index=1",
                        baseUrl + "?index=2",
                        baseUrl + "?index=3",
                        baseUrl + "?index=4",
                        baseUrl + "?index=5");
    }

    public static class Callback implements ExpectationResponseCallback {
        private int maxRedirects = 5;

        @Override
        public HttpResponse handle(HttpRequest req) {
            var index = NumberUtils.toInt(
                    req.getFirstQueryStringParameter("index"));
            if (index <= maxRedirects) {
                return response()
                        .withStatusCode(302)
                        .withHeader(
                                "Location", serverUrl(
                                        req, PATH + "?index=" + (index + 1)));
            }
            return response().withBody("""
                    <h1>Multi-redirects test page</h1>
                    The URL was redirected %s times.
                    Check if redirect trail matches that.
                    """.formatted(index));
        }
    }
}
