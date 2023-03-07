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
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404;
import static org.mockserver.model.MediaType.HTML_UTF_8;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.matchers.Times;

import com.norconex.committer.core.CommitterException;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;

/**
 * Test detection of page deletion (404 - File Not Found).
 */
@MockServerSettings
class FileNotFoundDeletionTest {

    private static final String HOME_PATH = "/notFoundDelete";
    private static final String TOGGLE_PATH = "/notFoundDeleteToggle";

    @Test
    void testFileNotFoundDeletion(ClientAndServer client)
            throws CommitterException {

        client
            .when(request().withPath(HOME_PATH))
            .respond(response()
                .withBody("""
                    <p>This link leads to a page we toggle existence:</p>
                    <a href="%s">Link</a>
                    """.formatted(TOGGLE_PATH), HTML_UTF_8));

        var crawlSession = TestWebCrawlSession
                .prepare()
                .startUrls(serverUrl(client, HOME_PATH))
                .crawlSession();
        var mem = WebTestUtil.getFirstMemoryCommitter(crawlSession);

        // First run: 2 new docs
        whenPageFound(client);
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isEqualTo(2);
        assertThat(mem.getDeleteCount()).isZero();
        mem.clean();

        // Second run: 0 new doc (unmodified) and 1 delete (not found)
        whenPageNotFound(client);
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isZero();
        assertThat(mem.getDeleteCount()).isOne();
        mem.clean();

        // Third run: 1 new doc (1 unmodified + 1 found again) and zero delete
        whenPageFound(client);
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isOne();
        assertThat(mem.getDeleteCount()).isZero();
        mem.clean();
    }

    private void whenPageFound(ClientAndServer client) {
        client
            .when(request().withPath(TOGGLE_PATH), Times.once())
            .respond(response().withBody("Page found, move on.", HTML_UTF_8));
    }

    private void whenPageNotFound(ClientAndServer client) {
        client
            .when(request().withPath(TOGGLE_PATH), Times.once())
            .respond(response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase())
                .withBody("Page not found.", HTML_UTF_8));
    }
}
