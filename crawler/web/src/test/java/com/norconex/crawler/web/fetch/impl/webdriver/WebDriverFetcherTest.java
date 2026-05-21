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
package com.norconex.crawler.web.fetch.impl.webdriver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.WebFetchRequest;
import com.norconex.crawler.web.mocks.MockWebDriver;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

@Timeout(30)
class WebDriverFetcherTest {

    @Test
    void testUnsupportedHttpMethod() throws FetchException {
        var response = new WebDriverFetcher().fetch(
                new WebFetchRequest(
                        CrawlDocStubs.crawlDocHtml("http://example.com"),
                        HttpMethod.HEAD));
        assertThat(response.getReasonPhrase()).contains("To obtain headers");
        assertThat(response.getProcessingOutcome()).isEqualTo(
                ProcessingOutcome.UNSUPPORTED);
    }

    @Test
    void testFetchPostMethodReturnsUnsupportedWithoutHeadHint()
            throws FetchException {
        var response = new WebDriverFetcher().fetch(
                new WebFetchRequest(
                        CrawlDocStubs.crawlDocHtml("http://example.com"),
                        HttpMethod.POST));
        assertThat(response.getProcessingOutcome())
                .isEqualTo(ProcessingOutcome.UNSUPPORTED);
        assertThat(response.getReasonPhrase())
                .doesNotContain("To obtain headers");
    }

    @Test
    void testAcceptRequestGetReturnsTrue() {
        var fetcher = new WebDriverFetcher();
        assertThat(fetcher.acceptRequest(new WebFetchRequest(
                CrawlDocStubs.crawlDocHtml("http://example.com"),
                HttpMethod.GET))).isTrue();
    }

    @Test
    void testAcceptRequestPostReturnsFalse() {
        var fetcher = new WebDriverFetcher();
        assertThat(fetcher.acceptRequest(new WebFetchRequest(
                CrawlDocStubs.crawlDocHtml("http://example.com"),
                HttpMethod.POST))).isFalse();
    }

    @Test
    void testAcceptRequestHeadReturnsFalse() {
        var fetcher = new WebDriverFetcher();
        assertThat(fetcher.acceptRequest(new WebFetchRequest(
                CrawlDocStubs.crawlDocHtml("http://example.com"),
                HttpMethod.HEAD))).isFalse();
    }

    @Test
    void testFetcherShutdownWithoutStartupDoesNotThrow() {
        assertThatNoException()
                .isThrownBy(() -> new WebDriverFetcher().fetcherShutdown(null));
    }

    @Test
    void testFetchDocumentContent_basicNoOptions() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html>test</html>");

        var fetcher = new WebDriverFetcher();
        var result = fetcher.fetchDocumentContent(driver, "http://example.com");

        assertThat(result).isNotNull();
        assertThat(result.readAllBytes())
                .isEqualTo("<html>test</html>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testFetchDocumentContent_withEarlyPageScript() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setEarlyPageScript("window.scrollTo(0,0);");

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");

        assertThat(result).isNotNull();
        verify(driver).executeScript("window.scrollTo(0,0);");
    }

    @Test
    void testFetchDocumentContent_withLatePageScript() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setLatePageScript("console.log('done');");

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");

        assertThat(result).isNotNull();
        verify(driver).executeScript("console.log('done');");
    }

    @Test
    void testFetchDocumentContent_withWindowSize() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setWindowSize(new Dimension(1280, 800));

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchDocumentContent_withPageLoadTimeout() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setPageLoadTimeout(Duration.ofSeconds(10));

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchDocumentContent_withImplicitlyWait() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setImplicitlyWait(Duration.ofMillis(100));

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchDocumentContent_withScriptTimeout() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setScriptTimeout(Duration.ofMillis(100));

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchDocumentContent_withThreadWait() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("<html/>");

        var fetcher = new WebDriverFetcher();
        fetcher.getConfiguration().setThreadWait(Duration.ofMillis(1));

        var result = fetcher.fetchDocumentContent(driver, "http://example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchDocumentContent_emptyPageSource() throws IOException {
        var driver = mock(MockWebDriver.class, RETURNS_DEEP_STUBS);
        when(driver.getPageSource()).thenReturn("");

        var fetcher = new WebDriverFetcher();
        var result = fetcher.fetchDocumentContent(driver, "http://example.com");

        assertThat(result).isNotNull();
        assertThat(result.readAllBytes()).isEmpty();
    }
}
