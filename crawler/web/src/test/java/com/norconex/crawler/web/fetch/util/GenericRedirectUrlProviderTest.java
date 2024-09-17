/* Copyright 2015-2024 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;

class GenericRedirectUrlProviderTest {

    @Test
    void testWriteRead() {
        var p = new GenericRedirectUrlProvider();
        p.getConfiguration().setFallbackCharset(StandardCharsets.UTF_8);
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(p));
    }

    @Test
    void testNoOriginalUrl() {
        var provider = new GenericRedirectUrlProvider();
        var req = SimpleHttpRequest.create("GET", "http://example.com");
        var resp = SimpleHttpResponse.create(200, "content");
        var ctx = new BasicHttpContext();
        ctx.setAttribute(HttpCoreContext.HTTP_REQUEST, req);

        assertThat(provider.provideRedirectURL(req, resp, ctx)).isNull();
    }

    @Test
    void testRelativeRedirect() {
        var provider = new GenericRedirectUrlProvider();
        var req = SimpleHttpRequest.create("GET", "http://xyz.com/original");
        var resp = SimpleHttpResponse.create(200, "content");
        resp.setHeader(HttpHeaders.LOCATION, "/redirected");
        var ctx = new BasicHttpContext();
        ctx.setAttribute(HttpCoreContext.HTTP_REQUEST, req);

        assertThat(provider.provideRedirectURL(req, resp, ctx)).isEqualTo(
                "http://xyz.com/redirected");
    }

    @Test
    void testNonAscii() {
        var provider = new GenericRedirectUrlProvider();
        var req = SimpleHttpRequest.create("GET", "http://xyz.com/original");
        var resp = SimpleHttpResponse.create(200, "content");
        resp.setHeader(HttpHeaders.LOCATION, new String("/redirigé"
                .getBytes(), StandardCharsets.ISO_8859_1));
        resp.setHeader(
                HttpHeaders.CONTENT_TYPE, "text/html;charset=ISO-8859-1");
        var ctx = new BasicHttpContext();
        ctx.setAttribute(HttpCoreContext.HTTP_REQUEST, req);

        assertThat(provider.provideRedirectURL(req, resp, ctx)).isEqualTo(
                "http://xyz.com/redirigé");
    }

    @Test
    void testFallbackCharset() {
        var provider = new GenericRedirectUrlProvider();
        var req = SimpleHttpRequest.create("GET", "http://xyz.com/original");
        var resp = SimpleHttpResponse.create(200, "content");
        resp.setHeader(HttpHeaders.LOCATION, "/redirigé");
        resp.setHeader(
                HttpHeaders.CONTENT_TYPE, "text/html;charset=BAD_CHARSET");
        var ctx = new BasicHttpContext();
        ctx.setAttribute(HttpCoreContext.HTTP_REQUEST, req);

        assertThat(provider.provideRedirectURL(req, resp, ctx)).isEqualTo(
                "http://xyz.com/redirigé");
    }
}
