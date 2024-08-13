/* Copyright 2021-2023 Norconex Inc.
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
import static org.mockserver.model.Header.optionalHeader;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpStatusCode;

import com.norconex.committer.core.CommitterException;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;

/**
 * Tests that the ETag "If-None-Match" is supported properly.
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-None-Match
 *
 * Crawled 4 times:
 *
 *  - 1. Clean crawl, gets and cache the Etag, 1 doc committed
 *  - 2. Server side not modified, so should be unmodified, 0 doc committed
 *  - 3. Server side modified, so 1 doc committed.
 *  - 4. Server side not modified, but etag is disabled on fetcher, so it
 *       thinks it's new/modified, 1 doc committed.
 */
//Test for https://github.com/Norconex/collector-http/issues/182
@MockServerSettings
class IfNoneMatchTest {

    private String path = "/ifNoneMatch";

    @Test
    void testIfNoneMatch(ClientAndServer client) throws CommitterException {

        var crawlSession = TestWebCrawlSession
                .forStartReferences(serverUrl(client, path))
                .crawlerSetup(cfg -> {
                    // disable checksums so they do not influence tests
                    cfg.setDocumentChecksummer(null);
                    cfg.setMetadataChecksummer(null);
                })
                .crawlSession();
        var mem = WebTestUtil.getFirstMemoryCommitter(crawlSession);

        // First run is new
        whenETag(client, "etag-A");
        crawlSession.getService().start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        // Second run got the same ETag, so not modified
        whenETag(client, "etag-A");
        crawlSession.getService().start();
        assertThat(mem.getUpsertCount()).isZero();
        mem.clean();

        // Third run got different Etag, so modified
        whenETag(client, "etag-B");
        crawlSession.getService().start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        // Fourth run got same Etag, but we disable E-Tag support, so modified
        whenETag(client, "etag-B");
        WebTestUtil.getFirstHttpFetcher(crawlSession)
                .getConfiguration().setETagDisabled(true);
        crawlSession.getService().start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        crawlSession.getService().clean();
    }

    private void whenETag(ClientAndServer client, String serverEtag) {
        client.reset();

        // When matching server Etag
        client
            .when(
                request()
                    .withPath(path)
                    .withHeader(HttpHeaders.IF_NONE_MATCH, serverEtag),
                Times.once())
            .respond(response()
                .withHeader(HttpHeaders.ETAG, serverEtag)
                .withStatusCode(HttpStatusCode.NOT_MODIFIED_304.code())
            );

        // When NOT matching server Etag
        client
            .when(request()
                .withPath(path)
                .withHeader(
                        optionalHeader(
                                HttpHeaders.IF_NONE_MATCH, "!" + serverEtag)),
                Times.once())
            .respond(response()
                .withHeader(HttpHeaders.ETAG, serverEtag)
                .withBody("Doc modified.")
            );
    }
}
