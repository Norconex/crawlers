/* Copyright 2024-2026 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.playwright;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Playwright;
import com.norconex.crawler.web.WebCrawlConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;

/**
 * Integration tests for {@link PlaywrightFetcher} using a real browser
 * against an embedded MockServer.
 *
 * <p>Tests use the {@code "chrome"} channel, which tells Playwright to launch
 * the system-installed Google Chrome rather than any downloaded browser
 * binary. No {@code playwright install} step is required.</p>
 *
 * <p>Tests are automatically skipped when Chrome is not found on the host
 * (so developer machines without Chrome are unaffected). Both local machines
 * with Chrome and GitHub Actions runners (which include Chrome) will execute
 * these tests.</p>
 */
@MockServerSettings
@Timeout(60)
class PlaywrightFetcherIT {

    /** Playwright channel that maps to system-installed Chrome. */
    private static final String CHROME_CHANNEL = "chrome";

    /**
     * Skip the entire class if Chrome cannot be launched. This avoids a
     * hard failure on machines where Chrome is not installed.
     */
    @BeforeAll
    static void assumeChromeAvailable() {
        try (var pw = Playwright.create()) {
            pw.chromium()
                    .launch(new LaunchOptions()
                            .setChannel(CHROME_CHANNEL)
                            .setHeadless(true))
                    .close();
        } catch (Exception e) {
            assumeTrue(false,
                    "Chrome not available on this system "
                            + "(channel=\"chrome\") — skipping IT tests. "
                            + "Cause: " + e.getMessage());
        }
    }

    /**
     * Reset MockServer expectations before each test so that catch-all
     * handlers registered by one test (e.g. {@code whenBoundedDepth}) cannot
     * leak into subsequent tests.
     */
    @BeforeEach
    void resetMockServer(ClientAndServer client) {
        client.reset();
    }

    private static PlaywrightFetcher chromeFetcher() {
        var f = new PlaywrightFetcher();
        f.getConfiguration()
                .setBrowser(PlaywrightBrowser.CHROMIUM)
                .setChannel(CHROME_CHANNEL)
                .setHeadless(true);
        return f;
    }

    /**
     * Verifies that PlaywrightFetcher can fetch a simple HTML page and the
     * content is captured by the committer.
     */
    @WebCrawlTest
    void testFetchSimplePage(ClientAndServer client, WebCrawlConfig cfg)
            throws IOException {
        var pagePath = "/playwright/simple";
        MockWebsite.whenHtml(client, pagePath, "<p>Hello from Playwright!</p>");

        cfg.setFetchers(List.of(chromeFetcher()));
        cfg.setStartReferences(List.of(serverUrl(client, pagePath)));

        var captures = WebCrawlTestCapturer.crawlAndCapture(cfg);
        assertThat(captures.getCommitter().getUpsertRequests()).hasSize(1);
        assertThat(new String(
                captures.getCommitter().getUpsertRequests()
                        .get(0).getContent().readAllBytes(),
                StandardCharsets.UTF_8))
                        .contains("Hello from Playwright!");
    }

    /**
     * Verifies that PlaywrightFetcher captures HTTP response headers natively.
     */
    @WebCrawlTest
    void testHttpHeadersAreCaptured(ClientAndServer client,
            WebCrawlConfig cfg) {
        var pagePath = "/playwright/headers";
        MockWebsite.whenHtml(client, pagePath, "<p>Headers test</p>");

        cfg.setFetchers(List.of(chromeFetcher()));
        cfg.setStartReferences(List.of(serverUrl(client, pagePath)));

        var captures = WebCrawlTestCapturer.crawlAndCapture(cfg);
        assertThat(captures.getCommitter().getUpsertRequests()).hasSize(1);
        // MockServer always returns content-type; verify header capture works
        var meta = captures.getCommitter().getUpsertRequests()
                .get(0).getMetadata();
        assertThat(meta.getStrings("Content-Type")).isNotEmpty();
    }

    /**
     * Verifies that Playwright follows links and crawls multiple pages.
     */
    @WebCrawlTest
    void testCrawlLinkedPages(ClientAndServer client, WebCrawlConfig cfg) {
        MockWebsite.whenBoundedDepth(client, 2);

        cfg.setFetchers(List.of(chromeFetcher()));
        cfg.setStartReferences(List.of(serverUrl(client, "/0")));
        cfg.setMaxDepth(2);

        var captures = WebCrawlTestCapturer.crawlAndCapture(cfg);
        assertThat(captures.getCommitter().getUpsertRequests())
                .hasSizeGreaterThan(1);
    }

    /**
     * Verifies that {@code latePageScript} modifies DOM content before
     * the rendered HTML is captured.
     */
    @WebCrawlTest
    void testPageScriptExecution(ClientAndServer client, WebCrawlConfig cfg)
            throws IOException {
        var pagePath = "/playwright/scripted";
        MockWebsite.whenHtml(client, pagePath,
                "<div id='target'>original</div>");

        var fetcher = new PlaywrightFetcher();
        fetcher.getConfiguration()
                .setBrowser(PlaywrightBrowser.CHROMIUM)
                .setChannel(CHROME_CHANNEL)
                .setHeadless(true)
                .setLatePageScript(
                        "document.getElementById('target')"
                                + ".textContent = 'modified by script';");
        cfg.setFetchers(List.of(fetcher));
        cfg.setStartReferences(List.of(serverUrl(client, pagePath)));

        var captures = WebCrawlTestCapturer.crawlAndCapture(cfg);
        assertThat(captures.getCommitter().getUpsertRequests()).hasSize(1);
        assertThat(new String(
                captures.getCommitter().getUpsertRequests()
                        .get(0).getContent().readAllBytes(),
                StandardCharsets.UTF_8))
                        .contains("modified by script");
    }
}
