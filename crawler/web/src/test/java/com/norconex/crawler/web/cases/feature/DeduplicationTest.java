/* Copyright 2021-2024 Norconex Inc.
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.committer.core.UpsertRequest;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;

/**
 * Test detection of duplicate files within crawling session.
 */
@MockServerSettings
class DeduplicationTest {

    @WebCrawlTest
    void testDeduplication(ClientAndServer client, WebCrawlerConfig cfg) {
        var homePath = "/dedup";
        var noDuplPath = "/dedup/1-noDupl";
        var metaDuplPath = "/dedup/2-meta";
        var contentDuplPath = "/dedup/3-content";

        //--- Web site ---

        // 2001-01-01T01:01:01 GMT
        var staticDate = lastModified(978310861000L);

        // @formatter:off
        MockWebsite.whenHtml(
            client,
            homePath,
            """
            <p>The 3 links below point to pages that are:</p>
            <ul>
              <li><a href="%s">Link 1: Not a duplicate.</a></li>
              <li><a href="%s">Link 2: Link 1 dupl. by meta checksum.</a></li>
              <li>
                <a href="%s">Link 3: Link 1 dupl. by content checksum.</a>
              </li>
            </ul>
            """
            .formatted(noDuplPath, metaDuplPath, contentDuplPath));

        client
            .when(request().withPath(noDuplPath))
            .respond(response()
                .withHeader("Last-Modified", staticDate)
                .withBody(
                        "A page with same content as another one.",
                        MediaType.HTML_UTF_8));

        client
            .when(request().withPath(metaDuplPath))
            .respond(response()
                .withHeader("Last-Modified", staticDate)
                .withBody(
                        "Same Last-Modified HTTP response value as \"noDupl\"",
                        MediaType.HTML_UTF_8));

        client
            .when(request().withPath(contentDuplPath))
            .respond(response()
                .withHeader(
                        "Last-Modified",
                        lastModified(System.currentTimeMillis()))
                .withBody(
                        "A page with same content as another one.",
                        MediaType.HTML_UTF_8));
        // @formatter:on

        // Relying on these crawler defaults:
        //    - LastModifiedMetadataChecksummer
        //    - Md5DocumentChecksummer
        cfg.setStartReferences(List.of(serverUrl(client, homePath)));
        cfg.setMetadataDeduplicate(true);
        cfg.setDocumentDeduplicate(true);
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getUpsertRequests())
                .map(UpsertRequest::getReference)
                .containsExactlyInAnyOrder(
                        serverUrl(client, homePath),
                        serverUrl(client, noDuplPath));
    }

    private String lastModified(long epoch) {
        return DateTimeFormatter.RFC_1123_DATE_TIME
                .withZone(ZoneId.of("GMT"))
                .format(Instant.ofEpochMilli(epoch));
    }
}
