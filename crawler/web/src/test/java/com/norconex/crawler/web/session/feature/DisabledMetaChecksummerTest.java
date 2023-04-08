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

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.committer.core.CommitterException;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;

/**
 * Tests that the page does not produce any Committer addition on subsequent
 * run when the content does not change, despite the metadata changing, when
 * the metadata checksummer is disabled.
*/
//Test for https://github.com/Norconex/collector-http/issues/544
@MockServerSettings
class DisabledMetaChecksummerTest {

    private final String path = "/disabledMetaChecksummer";

    @Test
    void testDisabledMetaChecksummer(ClientAndServer client)
            throws CommitterException {

        var crawlSession = TestWebCrawlSession
                .forStartReferences(serverUrl(client, path))
                .crawlerSetup(cfg -> {
                    cfg.setMetadataChecksummer(null);
                })
                .crawlSession();
        var mem = WebTestUtil.getFirstMemoryCommitter(crawlSession);

        // first time, 1 new doc
        whenMetaChanges(client, 10, "abc");
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        // second time, meta changes but not body
        whenMetaChanges(client, 8, "def");
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isZero();
        mem.clean();

        // third time, meta changes but not body
        whenMetaChanges(client, 4, "ghi");
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isZero();
        mem.clean();

        crawlSession.clean();
    }

    private void whenMetaChanges(
            ClientAndServer client, int daysAgo, String salt) {
        client.reset();
        client
            .when(request(path))
            .respond(
                response()
                    .withHeader("Last-Modified",
                            WebTestUtil.daysAgoRFC(daysAgo))
                    .withBody(WebsiteMock
                        .htmlPage()
                        .head("<meta name=\"salt\" content=\"" + salt + "\">")
                        .body("Content never changes.")
                        .build(), MediaType.HTML_UTF_8));
    }
}
