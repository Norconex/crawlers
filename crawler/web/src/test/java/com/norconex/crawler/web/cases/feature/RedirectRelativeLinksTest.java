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

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebCrawlerConfig.ReferencedLinkType;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.importer.doc.DocMetadata;

/**
 * The final URL of a redirect should be stored so relative links in it
 * are relative to final URL, not the first.  Github issue #17.
 */
@MockServerSettings
class RedirectRelativeLinksTest {

    @TempDir
    private Path tempDir;

    @Test
    void testRedirectRelativeLinks(ClientAndServer client) throws IOException {
        var basePath = "/redirectRelativeLinks";
        var homePath = basePath + "/home.html";
        var finalPath = basePath + "/final/target.html";
        var finalUrl = serverUrl(client, finalPath);
        var page1Url = serverUrl(client, basePath + "/final/page1.html");
        var page2Url = serverUrl(client, basePath + "/final/page2.html");

        client
            .when(request(homePath))
            .respond(response()
                .withStatusCode(302)
                .withHeader("Location", finalUrl));
        client
            .when(request(finalPath))
            .respond(response().withBody("""
                <h1>Redirected test page</h1>
                The URL was redirected.
                The URLs on this page should be relative to
                %s and not %s.  The crawler should redirect and figure that
                out.
                <a href="page1.html">Page 1 (broken)</a>
                <a href="page2.html">Page 2 (broken)</a>
                """.formatted(finalPath, homePath)));

        var mem = WebTestUtil.runWithConfig(tempDir, cfg -> {
            cfg.setKeepReferencedLinks(Set.of(
                    ReferencedLinkType.INSCOPE,
                    ReferencedLinkType.MAXDEPTH));
            cfg.setStartReferences(List.of(serverUrl(client, homePath)));
            cfg.setMaxDepth(0);
        });

        assertThat(mem.getUpsertCount()).isOne();

        var doc = mem.getUpsertRequests().get(0);

        // The document retained reference should be the final
        assertThat(doc.getReference()).isEqualTo(finalUrl);

        // The only reference in metadata should be the final.
        assertThat(doc.getMetadata().getStrings(DocMetadata.REFERENCE))
            .containsExactly(finalUrl);

        // Exracted URLs should be relative to final URL.
        assertThat(doc.getMetadata().getStrings(WebDocMetadata.REFERENCED_URLS))
            .containsExactlyInAnyOrder(page1Url, page2Url);
    }
}
