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

import static com.norconex.crawler.web.WebsiteMock.secureServerUrl;
import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.fetch.util.HstsResolver;

/**
 * Tests that a page will force fetching https when HSTS support is
 * in place for a site.
 */
//Related to https://github.com/Norconex/collector-http/issues/694
@MockServerSettings
class StrictTransportSecurityTest {
    @TempDir
    private Path tempDir;

    // CSV: clientSupport, serverSupport, expectsSecureUrl
    @CsvSource(textBlock = """
        true,  false, false
        true,  true,  true
        false, false, false
        false, true,  false
        """)
    @ParameterizedTest
    void testStrictTransportSecurity(
            boolean clientSupportsHSTS,
            boolean serverSupportsHSTS,
            boolean expectsSecureUrl,
            ClientAndServer client) {

        var basePath = "/strictTransportSecurity";
        var securePath = basePath + "/secure.html";
        var secureUrl = secureServerUrl(client, securePath);
        var securablePath = basePath + "/securable.html";
        var securableUrl = serverUrl(client, securablePath);

        client.reset();
        HstsResolver.clearCache();
        if (serverSupportsHSTS) {
            client
            .when(request().withMethod("HEAD"))
            .respond(response()
                    .withHeader(
                            "Strict-Transport-Security",
                            "max-age=16070400; includeSubDomains"));
        } else {
            client
                .when(request().withMethod("HEAD"))
                .respond(response());
        }
        client
            .when(request(securePath).withSecure(true))
            .respond(response()
                .withBody(
                        "Will this <a href=\"%s\">link</a> be secure?"
                            .formatted(securableUrl),
                        MediaType.HTML_UTF_8));

        client
            .when(request(securablePath).withSecure(true))
            .respond(response()
                .withBody("I am secure"));
        client
            .when(request(securablePath).withSecure(false))
            .respond(response()
                .withBody("I am NOT secure"));

        var mem = WebTestUtil.runWithConfig(tempDir, cfg -> {
            cfg.setStartReferences(List.of(secureUrl));
            cfg.setMaxDocuments(2);
            var fetcherCfg =
                    WebTestUtil.firstHttpFetcherConfig(cfg);
            fetcherCfg.setTrustAllSSLCertificates(true);
            if (!clientSupportsHSTS) {
                fetcherCfg.setHstsDisabled(true);
            }
            cfg.setPostImportLinks(TextMatcher.basic("secondURL"));
        });

        var expectedUrl = securableUrl;
        if (expectsSecureUrl) {
            expectedUrl = expectedUrl.replace("http://", "https://");
        }

        assertThat(mem.getUpsertRequests())
            .map(UpsertRequest::getReference)
            .filteredOn(ref -> !ref.equals(secureUrl))
            .containsExactly(expectedUrl);
    }
}
