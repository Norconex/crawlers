/* Copyright 2020-2023 Norconex Inc.
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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import com.norconex.committer.core.CommitterException;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;

/**
 * Tests that the "If-Modified-Since" is supported properly.
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-Modified-Since
 *
 * Tests the page 4 times:
 *
 *   Request 1: page last modified date is 5 days ago.
 *   Request 2: page last modified date is same as request 1.
 *   Request 3: page last modified date is now.
 *   Request 4: page last modified date is same as request 3 but with
 *              If-Modified-Since disabled.
 *
 * On the first and third attempt only shall we get documents committed.
 * Other attempts should be unmodified.
 */
//Related issue: https://github.com/Norconex/collector-http/issues/637
@MockServerSettings
class IfModifiedSinceTest {

    private String path = "/ifModifiedSince";

    private static final ZonedDateTime fiveDaysAgo = WebTestUtil.daysAgo(5);
    private static final ZonedDateTime today = WebTestUtil.daysAgo(0);

    private static ZonedDateTime serverDate = fiveDaysAgo;

    @Test
    void testIfModifiedSince(ClientAndServer client) throws CommitterException {

        client
            .when(request().withPath(path))
            .respond(HttpClassCallback.callback(Callback.class));


        var crawlSession = TestWebCrawlSession
                .forStartReferences(serverUrl(client, path))
                .crawlerSetup(cfg -> {
                    // disable checksums and E-Tag so they do not influence
                    // tests
                    cfg.setDocumentChecksummer(null);
                    cfg.setMetadataChecksummer(null);
                    ((GenericHttpFetcher) cfg.getFetchers().get(0))
                        .getConfig().setETagDisabled(true);
                })
                .crawlSession();
        var mem = WebTestUtil.getFirstMemoryCommitter(crawlSession);

        // First run is new
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        // Second run got the same date, so not modified
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isZero();
        mem.clean();

        // Third run got different date, so modified
        serverDate = today;
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        // Fourth run got same date, but we disable If-Modified-Since support,
        // so modified
        WebTestUtil.getFirstHttpFetcher(crawlSession)
                .getConfig().setIfModifiedSinceDisabled(true);
        crawlSession.start();
        assertThat(mem.getUpsertCount()).isOne();
        mem.clean();

        crawlSession.clean();
    }

    public static class Callback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest req) {
            var response = response().withHeader(
                    HttpHeaders.LAST_MODIFIED,
                    WebTestUtil.rfcFormat(serverDate));
            var dateStr = req.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE);
            if (StringUtils.isNotBlank(dateStr)) {
                var reqDate = ZonedDateTime.parse(
                        dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
                if (!reqDate.isBefore(serverDate)) {
                    return response.withStatusCode(
                            HttpStatusCode.NOT_MODIFIED_304.code());
                }
            }
            return response.withBody("Doc modified.");
        }
    }
}
