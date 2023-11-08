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

import static com.norconex.commons.lang.config.Configurable.configure;
import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.filter.OnMatch;
import com.norconex.crawler.core.filter.impl.ExtensionReferenceFilter;
import com.norconex.crawler.web.TestResource;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.importer.handler.transformer.impl.URLExtractorTransformer;

/**
 * Test that links can be specified for crawling after importing.
 */
@MockServerSettings
class PostImportLinksTest {

    @Test
    void testPostImportLinksURL(ClientAndServer client) throws IOException {
        var path = "/postImportLinks";

        WebsiteMock.whenHtml(client, path, """
            <h1>Post import test page.</h1>
            URLs in <a href="/post-import-links.pdf">this link</a>
            should be queued for processing.
            """);
        WebsiteMock.whenPDF(
                client, "/post-import-links.pdf", TestResource.PDF_WITH_LINKS);

        var mem = TestWebCrawlSession
                .forStartReferences(serverUrl(client, path))
                .crawlerSetup(cfg -> {
                    cfg.setMaxDepth(1);
                    // Tell it which field will hold post-import URLs.
                    cfg.setPostImportLinks(
                            TextMatcher.basic("myPostImportURLs"));
                    cfg.setPostImportLinksKeep(true);
                    // Keep only the test PDF.
                    cfg.setDocumentFilters(List.of(
                            configure(new ExtensionReferenceFilter(), c -> c
                                .setExtensions(List.of("pdf"))
                                .setOnMatch(OnMatch.INCLUDE)
                            )));
                    // Create a field with post-import PDF URLs.
                    var tagger = new URLExtractorTransformer();
                    tagger.getConfiguration().setToField("myPostImportURLs");
                    cfg.getImporterConfig().setHandler(tagger);
                })
                .crawl();

        assertThat(mem.getUpsertCount()).isOne();

        var doc = mem.getUpsertRequests().get(0);

        // Page 2 exists as a link value and a link label, with different URLs,
        // so we expect 6 links back.
        assertThat(doc.getMetadata().getStrings("myPostImportURLs"))
            .containsExactlyInAnyOrder(
                    "http://www.example.com/page1.html",
                    "http://www.example.com/page2.html",
                    "https://www.example.com/page2.html",
                    "http://www.example.com/page3.html",
                    "https://www.example.com/page4.html",
                    "http://www.example.com/page5.html");
    }
}