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

import static com.norconex.crawler.web.WebsiteMock.htmlPage;
import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.importer.doc.DocMetadata;

/**
 * Test proper charset detection when the declared one does not match
 * actual one.
 */
// Related ticket: https://github.com/Norconex/importer/issues/41
@MockServerSettings
class ContentTypeCharsetTest {

    @Test
    void testContentTypeCharset(ClientAndServer client) {

        var urlPath = "/contentTypeCharset";

        //--- Web site ---

        client
            .when(
                request()
                    .withPath(urlPath)
            )
            .respond(
                response()
                    .withContentType(new MediaType("application", "javascript")
                            .withCharset("Big5"))
                    .withBody(htmlPage()
                            .title("ContentType + Charset ☺☻")
                            .body("""
                            	This page returns the Content-Type as
                            	"application/javascript; charset=Big5"
                            	while in reality it is
                            	"text/html; charset=UTF-8".
                            	Éléphant à noël. ☺☻
                            	""")
                            .build()
                            .getBytes(UTF_8))
            );

        //--- First run without detect ---

        var mem1 = TestWebCrawlSession
                .prepare()
                .startUrls(serverUrl(client, urlPath))
                .crawl();

        assertThat(mem1.getUpsertRequests()).hasSize(1);
        var doc1 = mem1.getUpsertRequests().get(0);
        assertThat(doc1.getMetadata().getString(DocMetadata.CONTENT_TYPE))
            .isEqualTo("application/javascript");
        assertThat(doc1.getMetadata().getString(DocMetadata.CONTENT_ENCODING))
            .isEqualTo("Big5");

        //--- Second run with detect ---

        var mem2 = TestWebCrawlSession
                .prepare()
                .crawlerSetup(cfg -> {
                    var fetcher = new GenericHttpFetcher();
                    fetcher.getConfig().setForceContentTypeDetection(true);
                    fetcher.getConfig().setForceCharsetDetection(true);
                    cfg.setHttpFetchers(fetcher);
                })
                .startUrls(serverUrl(client, urlPath))
                .crawl();

        assertThat(mem2.getUpsertRequests()).hasSize(1);
        var doc2 = mem2.getUpsertRequests().get(0);
        assertThat(doc2.getMetadata().getString(DocMetadata.CONTENT_TYPE))
            .isEqualTo("text/html");
        assertThat(doc2.getMetadata().getString(DocMetadata.CONTENT_ENCODING))
            .isEqualTo(StandardCharsets.UTF_8.toString());
    }
}
