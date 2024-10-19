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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.UpsertRequest;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.stubs.CrawlerStubs;

/**
 * Test that "javascript:" URLs are not extracted.
 */
// Related issue: https://github.com/Norconex/collector-http/issues/540
@MockServerSettings
class JavaScriptProtocolURLTest {

    @TempDir
    private Path tempDir;

    @Test
    void testJavaScriptProtocolURL(ClientAndServer client) {
        var firstPath = "/jsUrl";
        var secondPath = "/jsUrl/target";

        WebsiteMock.whenHtml(client, firstPath, """
                <h1>Page with a Javascript URL</h1>
                <a href="javascript:some_function('some_arg', 'another_arg');">
                  Must be skipped
                </a>
                <a href="javascript&#x3a;abcd_Comments&#x28;true&#x29;&#x3b;">
                  Must also be skipped
                </a>
                <a href="%s">Must be followed</a>
                This page must be crawled (1 of 2)
                """.formatted(secondPath));

        WebsiteMock.whenHtml(client, secondPath, """
                <h1>Page with a Javascript URL</h1>
                Page must be crawled (2 of 2)
                """);

        var crawler = CrawlerStubs.memoryCrawler(tempDir, cfg -> {
            cfg.setStartReferences(List.of(serverUrl(client, firstPath)));
        });
        var mem = WebTestUtil.firstCommitter(crawler);

        assertThatNoException().isThrownBy(() -> {
            crawler.crawl();
        });

        assertThat(mem.getUpsertRequests())
                .map(UpsertRequest::getReference)
                .containsExactlyInAnyOrder(
                        serverUrl(client, firstPath),
                        serverUrl(client, secondPath));

        crawler.clean();
    }
}
