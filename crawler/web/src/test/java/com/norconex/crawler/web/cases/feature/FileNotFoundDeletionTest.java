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
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404;
import static org.mockserver.model.MediaType.HTML_UTF_8;

import java.util.List;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.matchers.Times;

import com.norconex.committer.core.CommitterException;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;

/**
 * Test detection of page deletion (404 - File Not Found).
 */
@MockServerSettings
class FileNotFoundDeletionTest {

    private static final String HOME_PATH = "/notFoundDelete";
    private static final String TOGGLE_PATH = "/notFoundDeleteToggle";

    @WebCrawlTest
    void testFileNotFoundDeletion(
            ClientAndServer client, WebCrawlerConfig cfg)
            throws CommitterException {

        // @formatter:off
        client
            .when(request().withPath(HOME_PATH))
            .respond(response()
                .withBody(
                    """
                    <p>This link leads to a page we toggle existence:</p>
                    <a href="%s">Link</a>
                    """.formatted(TOGGLE_PATH),
                    HTML_UTF_8));
        // @formatter:on

        cfg.setStartReferences(List.of(serverUrl(client, HOME_PATH)));

        // First run: 2 new docs
        whenPageFound(client);
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();
        assertThat(mem.getUpsertCount()).isEqualTo(2);
        assertThat(mem.getDeleteCount()).isZero();
        mem.clean();

        // Second run: 0 new doc (unmodified) and 1 delete (not found)
        whenPageNotFound(client);
        mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();
        assertThat(mem.getUpsertCount()).isZero();
        assertThat(mem.getDeleteCount()).isOne();
        mem.clean();

        // Third run: 1 new doc (1 unmodified + 1 resurrected) and zero delete
        whenPageFound(client);
        mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();
        assertThat(mem.getUpsertCount()).isOne();
        assertThat(mem.getDeleteCount()).isZero();
        mem.clean();
    }

    private void whenPageFound(ClientAndServer client) {
        client
                .when(request().withPath(TOGGLE_PATH), Times.once())
                .respond(
                        response().withBody(
                                "Page found, move on.",
                                HTML_UTF_8));
    }

    private void whenPageNotFound(ClientAndServer client) {
        client
                .when(request().withPath(TOGGLE_PATH), Times.once())
                .respond(
                        response()
                                .withStatusCode(NOT_FOUND_404.code())
                                .withReasonPhrase(NOT_FOUND_404.reasonPhrase())
                                .withBody("Page not found.", HTML_UTF_8));
    }
}
