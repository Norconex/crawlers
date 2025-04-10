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

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractor;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;

/**
 * Content of &lt;script&gt; tags must be stripped by GenericLinkExtractor
 * but src must be followed.
 */
// Test case for https://github.com/Norconex/collector-http/issues/232
@MockServerSettings
class ScriptTagsTest {

    @WebCrawlTest
    void testScriptTags(ClientAndServer client, WebCrawlerConfig cfg)
            throws CommitterException {

        var homePath = "/scriptTags/index.html";
        var scriptPath = "/scriptTags/script.js";

        MockWebsite.whenHtml(client, homePath, """
                <h1>Page with a script tag</h1>
                <script src="%s">
                    THIS_MUST_BE_STRIPPED, but src URL must be crawled
                </script>
                <script>THIS_MUST_BE_STRIPPED</script>
                View the source to see &lt;script&gt; tags
                """.formatted(scriptPath));

        MockWebsite.whenHtml(client, scriptPath, """
                <h1>The Script page</h1>
                This must be crawled.
                """);

        cfg.setStartReferences(List.of(serverUrl(client, homePath)));
        var le = new HtmlLinkExtractor();
        le.getConfiguration().addLinkTag("script", "src");
        cfg.setLinkExtractors(List.of(le));
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getUpsertCount()).isEqualTo(2);

        for (UpsertRequest doc : mem.getUpsertRequests()) {
            var content = WebTestUtil.docText(doc);
            if (doc.getReference().endsWith(homePath)) {
                // first page
                assertThat(content).contains("View the source");
                assertThat(content).doesNotContain("THIS_MUST_BE_STRIPPED");
            } else {
                // second page
                assertThat(content).contains("This must be crawled");
            }
        }
    }
}
