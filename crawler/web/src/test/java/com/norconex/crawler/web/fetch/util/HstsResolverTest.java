/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.web.fetch.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.norconex.crawler.web.ledger.WebCrawlEntry;

class HstsResolverTest {

    @AfterEach
    void clearCache() {
        HstsResolver.clearCache();
    }

    @Test
    void testClearCache() throws IOException {
        // Populate cache with one entry by resolving an https URL
        // using a mock HttpClient that returns no HSTS header
        var httpClient = noHstsClient();
        var entry = new WebCrawlEntry("https://example.com/page", 0);
        HstsResolver.resolve(httpClient, entry);

        // Now clear and verify http URL is NOT upgraded (cache is gone)
        HstsResolver.clearCache();
        var httpEntry = new WebCrawlEntry("http://example.com/other", 0);
        HstsResolver.resolve(httpClient, httpEntry);
        assertThat(httpEntry.getReference())
                .as("URL should remain http:// after cache is cleared")
                .startsWith("http://");
    }

    @Test
    void testResolveHttpUrlNotInCache_urlUnchanged() throws IOException {
        // http URL with domain not in cache → URL stays as-is
        var httpClient = noHstsClient();
        var entry = new WebCrawlEntry("http://notcached.example.com/page", 0);
        HstsResolver.resolve(httpClient, entry);

        assertThat(entry.getReference())
                .isEqualTo("http://notcached.example.com/page");
        assertThat(entry.getReferenceTrail()).isEmpty();
    }

    @Test
    void testResolveHttpsUrl_cachedAsNoSupport() throws IOException {
        // https URL makes the resolver call httpClient to check for HSTS;
        // if the response has no HSTS header, the domain is cached as NO.
        var httpClient = noHstsClient();
        var httpsEntry = new WebCrawlEntry("https://example.com/secure", 0);
        HstsResolver.resolve(httpClient, httpsEntry);

        // https URL is never changed by the resolver
        assertThat(httpsEntry.getReference())
                .isEqualTo("https://example.com/secure");

        // Subsequent http URL on the same domain should NOT be upgraded
        var httpEntry = new WebCrawlEntry("http://example.com/plain", 0);
        HstsResolver.resolve(httpClient, httpEntry);
        assertThat(httpEntry.getReference())
                .as("No HSTS → http URL should remain unchanged")
                .isEqualTo("http://example.com/plain");
    }

    @Test
    void testResolveHttpsUrl_cachedAsIncludeSubdomains() throws IOException {
        // https URL makes the resolver call httpClient; simulate HSTS with
        // includeSubDomains so subsequent http URLs on that domain are upgraded.
        var httpClient = hstsIncludeSubdomainsClient();

        // Trigger cache population via an https root-domain URL
        var httpsEntry = new WebCrawlEntry("https://example.com/secure", 0);
        HstsResolver.resolve(httpClient, httpsEntry);

        // http URL on the root domain should be upgraded to https
        var httpRootEntry = new WebCrawlEntry("http://example.com/page", 0);
        HstsResolver.resolve(httpClient, httpRootEntry);
        assertThat(httpRootEntry.getReference())
                .as("Root domain http URL should be upgraded to https (HSTS INCLUDE_SUBDOMAINS)")
                .startsWith("https://");

        // Cache for a subdomain is populated when its https URL is visited first
        var httpsSubEntry = new WebCrawlEntry("https://sub.example.com/a", 0);
        HstsResolver.resolve(httpClient, httpsSubEntry);

        // Now the http subdomain URL should also be upgraded
        var httpSubEntry = new WebCrawlEntry("http://sub.example.com/page", 0);
        HstsResolver.resolve(httpClient, httpSubEntry);
        assertThat(httpSubEntry.getReference())
                .as("Subdomain http URL should be upgraded once subdomain https was visited")
                .startsWith("https://");
    }

    @Test
    void testResolveHttpsUrl_cachedAsDomainOnly() throws IOException {
        // Domain-only HSTS: root domain http → upgrade; subdomain http → no upgrade
        var httpClient = hstsDomainOnlyClient();

        var httpsEntry = new WebCrawlEntry("https://example.com/secure", 0);
        HstsResolver.resolve(httpClient, httpsEntry);

        // http URL on the root domain SHOULD be upgraded
        var rootEntry = new WebCrawlEntry("http://example.com/page", 0);
        HstsResolver.resolve(httpClient, rootEntry);
        assertThat(rootEntry.getReference())
                .as("Root domain http URL should be upgraded to https (HSTS DOMAIN_ONLY)")
                .startsWith("https://");

        // http URL on a subdomain should NOT be upgraded (domain only)
        var subEntry = new WebCrawlEntry("http://sub.example.com/page", 0);
        HstsResolver.resolve(httpClient, subEntry);
        assertThat(subEntry.getReference())
                .as("Subdomain http URL should NOT be upgraded (HSTS DOMAIN_ONLY)")
                .isEqualTo("http://sub.example.com/page");
    }

    @Test
    void testResolveHttpsUrl_httpClientThrowsIoException() throws IOException {
        // When httpClient throws IOException, the domain is treated as no HSTS support
        var httpClient = mock(HttpClient.class);
        when(httpClient.execute(
                ArgumentMatchers.<ClassicHttpRequest>any(),
                ArgumentMatchers.<HttpClientResponseHandler<Header>>any()))
                        .thenThrow(new IOException("network error"));

        var httpsEntry = new WebCrawlEntry("https://example.com/secure", 0);
        HstsResolver.resolve(httpClient, httpsEntry);

        // http URL should NOT be upgraded (IOException → treated as NO support)
        var httpEntry = new WebCrawlEntry("http://example.com/page", 0);
        HstsResolver.resolve(httpClient, httpEntry);
        assertThat(httpEntry.getReference())
                .as("IOException in HSTS check → http URL must remain unchanged")
                .isEqualTo("http://example.com/page");
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static HttpClient noHstsClient() throws IOException {
        var httpClient = mock(HttpClient.class);
        when(httpClient.execute(
                ArgumentMatchers.<ClassicHttpRequest>any(),
                ArgumentMatchers.<HttpClientResponseHandler<Header>>any()))
                        .thenReturn(null);
        return httpClient;
    }

    private static HttpClient hstsIncludeSubdomainsClient() throws IOException {
        var header = mock(Header.class);
        when(header.getValue())
                .thenReturn("max-age=31536000; includeSubDomains");
        var httpClient = mock(HttpClient.class);
        when(httpClient.execute(
                ArgumentMatchers.<ClassicHttpRequest>any(),
                ArgumentMatchers.<HttpClientResponseHandler<Header>>any()))
                        .thenReturn(header);
        return httpClient;
    }

    private static HttpClient hstsDomainOnlyClient() throws IOException {
        var header = mock(Header.class);
        when(header.getValue()).thenReturn("max-age=31536000");
        var httpClient = mock(HttpClient.class);
        when(httpClient.execute(
                ArgumentMatchers.<ClassicHttpRequest>any(),
                ArgumentMatchers.<HttpClientResponseHandler<Header>>any()))
                        .thenReturn(header);
        return httpClient;
    }
}
