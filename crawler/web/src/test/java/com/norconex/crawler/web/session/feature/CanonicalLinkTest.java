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

import java.nio.file.Path;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.crawler.web.WebStubber;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.crawler.WebCrawlerEvent;

/**
 * Test (non)canonical link detection.
 */
@MockServerSettings
class CanonicalLinkTest {

    final MutableInt canCount = new MutableInt();

    @Test
    void testCanonicalLink(ClientAndServer client, @TempDir Path tempDir) {
        var canonicalPath = "/canonical";
        var canonicalUrl = serverUrl(client, canonicalPath);
        var httpHeaderPath = "/httpHeader";
        var linkRelPath = "/linkRel";

        var commonBody = """
            <h1>Handling of (non)canonical URLs</h1>
            <p>The links below are pointing to pages that should be
            considered copies of this page when accessed without URL
            parameters.</p>
            <ul>
              <li><a href="%s?type=httpheader">HTTP Header</a></li>
              <li><a href="%s?type=linkrel">link rel</a></li>
            </ul>
            """.formatted(
                    serverUrl(client, httpHeaderPath),
                    serverUrl(client, linkRelPath));

        WebsiteMock.whenHtml(
                client, canonicalPath, "<p>Canonical page</p>" + commonBody);

        client
            .when(request()
                .withPath(httpHeaderPath))
            .respond(response()
                .withHeader("Link", "<%s>; rel=\"canonical\""
                        .formatted(canonicalUrl))
                .withBody(WebStubber
                    .htmlPage()
                    .body("<p>Canonical URL in HTTP header.</p>" + commonBody)
                    .build(),
                        MediaType.HTML_UTF_8));

        client
            .when(request()
                .withPath(linkRelPath))
            .respond(response()
                .withBody(WebStubber
                    .htmlPage()
                    .head("<link rel=\"canonical\" href=\"%s\" />"
                            .formatted(canonicalUrl))
                    .body("<p>Canonical URL in HTML &lt;head&gt;.</p>"
                            + commonBody)
                    .build(),
                        MediaType.HTML_UTF_8));

        var crawlSession = WebStubber.crawlSession(tempDir, canonicalUrl);
        var cfg = WebTestUtil.getFirstCrawlerConfig(crawlSession);
        canCount.setValue(0);
        cfg.setIgnoreCanonicalLinks(false);
        cfg.addEventListener(e -> {
            if (e.is(WebCrawlerEvent.REJECTED_NONCANONICAL)) {
                canCount.increment();
            }
        });
        crawlSession.start();

        var mem = WebTestUtil.getFirstMemoryCommitter(crawlSession);
        assertThat(mem.getUpsertRequests()).hasSize(1);
        assertThat(canCount.intValue()).isEqualTo(2);
    }
}
