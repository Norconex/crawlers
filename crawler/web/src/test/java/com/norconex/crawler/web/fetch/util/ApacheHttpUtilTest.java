/* Copyright 2023-2024 Norconex Inc.
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

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpAuthConfig;
import com.norconex.crawler.web.ledger.WebCrawlEntry;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

@Timeout(30)
class ApacheHttpUtilTest {

    @Test
    void testFormToRequest() throws URISyntaxException {
        var html = """
                <form
                  action="/login"
                  method="%s"
                  accept-charset="UTF-8"
                  %s
                >
                  Username: <input type="text=" name="THEusername"><br>
                  Password: <input type="password=" name="THEpassword"><br>
                  <input type="submit" value="Login"><br>
                </form>
                """;

        var authCfg = new HttpAuthConfig();
        authCfg.setFormSelector("form");
        authCfg.setCredentials(new Credentials("joe", "dalton"));
        authCfg.setFormUsernameField("THEusername");
        authCfg.setFormPasswordField("THEpassword");

        // POST, default enctype
        var doc = Jsoup.parse(
                MockWebsite
                        .htmlPage()
                        .body(html.formatted("POST", ""))
                        .build(),
                "http://blah.com");
        var req = (HttpPost) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(req).isNotNull();
        assertThat(req.getEntity()).isInstanceOf(UrlEncodedFormEntity.class);

        // POST, multipart/form-data
        doc = Jsoup.parse(
                MockWebsite
                        .htmlPage()
                        .body(
                                html.formatted(
                                        "POST",
                                        "encType=\"multipart/form-data\""))
                        .build(),
                "http://blah.com");
        req = (HttpPost) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(req).isNotNull();
        assertThat(req.getEntity().getClass().getSimpleName())
                .isEqualTo("MultipartFormEntity");

        // POST, text/plain
        doc = Jsoup.parse(
                MockWebsite
                        .htmlPage()
                        .body(html.formatted("POST", "encType=\"text/plain\""))
                        .build(),
                "http://blah.com");
        req = (HttpPost) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(req).isNotNull();
        assertThat(req.getEntity()).isInstanceOf(StringEntity.class);

        // GET
        doc = Jsoup.parse(
                MockWebsite
                        .htmlPage()
                        .body(html.formatted("GET", ""))
                        .build(),
                "http://blah.com");
        var get = (HttpGet) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(get).isNotNull();

        assertThat(HttpURL.toURI(get.getRequestUri()))
                .hasParameter("THEusername")
                .hasParameter("THEpassword");

        // HEAD
        doc = Jsoup.parse(
                MockWebsite
                        .htmlPage()
                        .body(html.formatted("HEAD", ""))
                        .build(),
                "http://blah.com");
        var head = (HttpGet) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(head).isNull();

        // No form
        assertThat(
                ApacheHttpUtil.formToRequest(
                        Jsoup.parse("<html>No form!</html>"), authCfg))
                                .isNull();
    }

    @Test
    void testApplyContentTypeAndCharset() {
        var doc = CrawlDocStubs.crawlDoc("http://example.com/");

        // Blank value → no-op (no exception)
        ApacheHttpUtil.applyContentTypeAndCharset("", doc);
        ApacheHttpUtil.applyContentTypeAndCharset(null, doc);

        // Valid content type with charset
        ApacheHttpUtil.applyContentTypeAndCharset("text/html; charset=UTF-8",
                doc);
        assertThat(doc.getContentType()).isEqualTo(ContentType.HTML);
        assertThat(doc.getCharset()).isEqualTo(StandardCharsets.UTF_8);

        // Content type without charset leaves charset unchanged
        var doc2 = CrawlDocStubs.crawlDoc("http://example.com/json");
        ApacheHttpUtil.applyContentTypeAndCharset("application/json", doc2);
        assertThat(doc2.getContentType())
                .isEqualTo(ContentType.valueOf("application/json"));

        // Null doc → no-op, no exception
        ApacheHttpUtil.applyContentTypeAndCharset("text/html", null);
    }

    @Test
    void testApplyResponseHeaders() {
        var doc = CrawlDocStubs.crawlDoc("http://example.com/");
        var entry = new WebCrawlEntry("http://example.com/", 0);

        var response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.CONTENT_TYPE,
                "text/plain; charset=ISO-8859-1");
        response.addHeader(HttpHeaders.ETAG, "\"etag-value\"");
        // Last-Modified in RFC 1123 format
        response.addHeader(HttpHeaders.LAST_MODIFIED,
                "Mon, 01 Jan 2024 00:00:00 GMT");
        response.addHeader("X-Custom-Header", "some-value");

        ApacheHttpUtil.applyResponseHeaders(response, "prefix-", doc, entry);

        assertThat(entry.getEtag()).isEqualTo("\"etag-value\"");
        assertThat(entry.getLastModified()).isNotNull();
        assertThat(doc.getContentType()).isEqualTo(ContentType.TEXT);
        assertThat(doc.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        // Custom header should be stored with prefix
        assertThat(doc.getMetadata().getString("prefix-X-Custom-Header"))
                .isEqualTo("some-value");
    }

    @Test
    void testApplyResponseHeadersWithNoPrefix() {
        var doc = CrawlDocStubs.crawlDoc("http://example.com/");
        var entry = new WebCrawlEntry("http://example.com/", 0);

        var response = new BasicHttpResponse(200);
        response.addHeader("Cache-Control", "no-cache");

        ApacheHttpUtil.applyResponseHeaders(response, null, doc, entry);
        assertThat(doc.getMetadata().getString("Cache-Control"))
                .isEqualTo("no-cache");
    }

    @Test
    void testApplyResponseHeadersWithBlankValue() {
        var doc = CrawlDocStubs.crawlDoc("http://example.com/");
        var entry = new WebCrawlEntry("http://example.com/", 0);

        var response = new BasicHttpResponse(200);
        response.addHeader("X-Empty", "");

        // Blank values should be skipped, no entry in metadata
        ApacheHttpUtil.applyResponseHeaders(response, null, doc, entry);
        assertThat(doc.getMetadata().containsKey("X-Empty")).isFalse();
    }

    @Test
    void testSetRequestIfModifiedSince_withProcessedAt() {
        var request = new HttpGet("http://example.com/");
        var prevEntry = new WebCrawlEntry("http://example.com/", 0);
        prevEntry.setProcessedAt(
                ZonedDateTime.parse("2024-01-15T10:30:00+00:00"));

        var docCtx = CrawlDocContext.builder()
                .doc(CrawlDocStubs.crawlDoc("http://example.com/"))
                .currentCrawlEntry(new WebCrawlEntry("http://example.com/", 0))
                .previousCrawlEntry(prevEntry)
                .build();

        ApacheHttpUtil.setRequestIfModifiedSince(request, docCtx);
        assertThat(request.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE))
                .isNotNull();
    }

    @Test
    void testSetRequestIfModifiedSince_withLastModified() {
        var request = new HttpGet("http://example.com/");
        var prevEntry = new WebCrawlEntry("http://example.com/", 0);
        prevEntry.setLastModified(
                ZonedDateTime.parse("2024-03-01T08:00:00+00:00"));
        prevEntry.setProcessedAt(
                ZonedDateTime.parse("2024-03-02T09:00:00+00:00"));

        var docCtx = CrawlDocContext.builder()
                .doc(CrawlDocStubs.crawlDoc("http://example.com/"))
                .currentCrawlEntry(new WebCrawlEntry("http://example.com/", 0))
                .previousCrawlEntry(prevEntry)
                .build();

        ApacheHttpUtil.setRequestIfModifiedSince(request, docCtx);
        // lastModified takes precedence over processedAt
        var headerValue = request.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE)
                .getValue();
        assertThat(headerValue).contains("2024");
    }

    @Test
    void testSetRequestIfModifiedSince_noPreviousEntry() {
        var request = new HttpGet("http://example.com/");

        // No previous entry → header should not be set
        ApacheHttpUtil.setRequestIfModifiedSince(request, null);
        assertThat(request.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE))
                .isNull();

        var docCtx = CrawlDocContext.builder()
                .doc(CrawlDocStubs.crawlDoc("http://example.com/"))
                .currentCrawlEntry(new WebCrawlEntry("http://example.com/", 0))
                .build();
        ApacheHttpUtil.setRequestIfModifiedSince(request, docCtx);
        assertThat(request.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE))
                .isNull();
    }

    @Test
    void testSetRequestIfNoneMatch_withEtag() {
        var request = new HttpGet("http://example.com/");
        var prevEntry = new WebCrawlEntry("http://example.com/", 0);
        prevEntry.setEtag("\"etag-abc\"");

        var docCtx = CrawlDocContext.builder()
                .doc(CrawlDocStubs.crawlDoc("http://example.com/"))
                .currentCrawlEntry(new WebCrawlEntry("http://example.com/", 0))
                .previousCrawlEntry(prevEntry)
                .build();

        ApacheHttpUtil.setRequestIfNoneMatch(request, docCtx);
        assertThat(request.getFirstHeader(HttpHeaders.IF_NONE_MATCH).getValue())
                .isEqualTo("\"etag-abc\"");
    }

    @Test
    void testSetRequestIfNoneMatch_noEtag() {
        var request = new HttpGet("http://example.com/");
        var prevEntry = new WebCrawlEntry("http://example.com/", 0);
        // ETag is null

        var docCtx = CrawlDocContext.builder()
                .doc(CrawlDocStubs.crawlDoc("http://example.com/"))
                .currentCrawlEntry(new WebCrawlEntry("http://example.com/", 0))
                .previousCrawlEntry(prevEntry)
                .build();

        ApacheHttpUtil.setRequestIfNoneMatch(request, docCtx);
        assertThat(request.getFirstHeader(HttpHeaders.IF_NONE_MATCH)).isNull();
    }

    @Test
    void testSetRequestIfNoneMatch_nullContext() {
        var request = new HttpGet("http://example.com/");
        ApacheHttpUtil.setRequestIfNoneMatch(request, null);
        assertThat(request.getFirstHeader(HttpHeaders.IF_NONE_MATCH)).isNull();
    }

    @Test
    void testCreateUriRequest_httpMethods() {
        var url = "http://example.com/page";

        assertThat(ApacheHttpUtil.createUriRequest(url, HttpMethod.GET))
                .isInstanceOf(HttpGet.class);
        assertThat(ApacheHttpUtil.createUriRequest(url, HttpMethod.POST))
                .isInstanceOf(HttpPost.class);
        assertThat(ApacheHttpUtil.createUriRequest(url, HttpMethod.HEAD))
                .isInstanceOf(HttpHead.class);
    }

    @Test
    void testCreateUriRequest_stringMethod() {
        var url = "http://example.com/page";

        assertThat(ApacheHttpUtil.createUriRequest(url, "GET"))
                .isInstanceOf(HttpGet.class);
        assertThat(ApacheHttpUtil.createUriRequest(url, "POST"))
                .isInstanceOf(HttpPost.class);
        assertThat(ApacheHttpUtil.createUriRequest(url, "HEAD"))
                .isInstanceOf(HttpHead.class);
        // Null/blank defaults to GET
        assertThat(ApacheHttpUtil.createUriRequest(url, (String) null))
                .isInstanceOf(HttpGet.class);
    }
}
