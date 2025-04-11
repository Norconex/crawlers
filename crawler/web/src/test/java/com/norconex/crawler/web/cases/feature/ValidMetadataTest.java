/* Copyright 2019-2025 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.HttpHeaders;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.UpsertRequest;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.importer.doc.DocMetaConstants;

/**
 * Test that metadata is extracted properly.
 */
@MockServerSettings
class ValidMetadataTest {

    @WebCrawlTest
    void testValidMetadata(ClientAndServer client, WebCrawlerConfig cfg) {

        MockWebsite.whenInfiniteDepth(client);

        cfg.setStartReferences(
                List.of(MockWebsite.serverUrl(client, "/validMetadata/0000")));
        cfg.setMaxDepth(10);

        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        // 0-depth + 10 others == 11 expected files
        assertThat(mem.getRequestCount()).isEqualTo(11);

        for (UpsertRequest doc : mem.getUpsertRequests()) {
            var meta = doc.getMetadata();
            assertThat(meta.getStrings(HttpHeaders.CONTENT_TYPE))
                    .containsExactly("text/html; charset=UTF-8");
            assertThat(meta.getStrings(DocMetaConstants.CONTENT_TYPE))
                    .containsExactly("text/html");
            assertThat(meta.getStrings(DocMetaConstants.CONTENT_ENCODING))
                    .containsExactly(StandardCharsets.UTF_8.toString());
        }
    }
}
