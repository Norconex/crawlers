/* Copyright 2018-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.httpclient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.util.GenericRedirectUrlProvider;
import com.norconex.crawler.web.fetch.util.RedirectUrlProvider;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Generic HTTP Fetcher configuration.
 */
@Data
@Accessors(chain = true)
public class HttpClientFetcherConfig extends BaseFetcherConfig {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_REDIRECT = 50;
    public static final int DEFAULT_MAX_CONNECTIONS = 200;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 20;
    public static final Duration DEFAULT_MAX_IDLE_TIME = Duration.ofSeconds(10);

    public static final List<Integer> DEFAULT_VALID_STATUS_CODES =
            CollectionUtil.unmodifiableList(HttpStatus.SC_OK);
    public static final List<Integer> DEFAULT_NOT_FOUND_STATUS_CODES =
            CollectionUtil.unmodifiableList(HttpStatus.SC_NOT_FOUND);

    public enum CookieSpec {
        RELAXED, STRICT, IGNORE
    }

    /**
     * HTTP status codes considered "valid". Defaults to 200.
     */
    private final List<Integer> validStatusCodes =
            new ArrayList<>(DEFAULT_VALID_STATUS_CODES);

    /**
     * HTTP status codes considered "not found". Defaults to 404.
     */
    private final List<Integer> notFoundStatusCodes =
            new ArrayList<>(DEFAULT_NOT_FOUND_STATUS_CODES);
    /**
     * Optional prefix prepended to captured HTTP response fields. A
     * {@code null} value (default) won't add any prefix.
     */
    private String headersPrefix;

    /**
     * Whether content type is detected instead of relying on
     * returned <code>Content-Type</code> HTTP response header.
     */
    private boolean forceContentTypeDetection;

    /**
     * Whether character encoding is detected instead of relying on
     * the charset sometimes found in the <code>Content-Type</code> HTTP
     * response header.
     */
    private boolean forceCharsetDetection;

    /**
     * Authentication configuration for sites requiring it. Default
     * is {@code null}.
     */
    private HttpAuthConfig authentication;

    /**
     * Cookie specification to use when fetching documents. Default is relaxed.
     */
    private CookieSpec cookieSpec = CookieSpec.RELAXED;

    /**
     * An optional HTTP proxy.
     */
    private final ProxySettings proxySettings = new ProxySettings();

    /**
     * The connection timeout for a connection to be established.
     * Default is {@link #DEFAULT_TIMEOUT}.
     */
    private Duration connectionTimeout = DEFAULT_TIMEOUT;

    /**
     * Gets the maximum period of inactivity between two consecutive data
     * packets.
     * Default is {@link #DEFAULT_TIMEOUT}.
     */
    private Duration socketTimeout = DEFAULT_TIMEOUT;

    /**
     * Gets the timeout when requesting a connection.
     * Default is {@link #DEFAULT_TIMEOUT}.
     */
    private Duration connectionRequestTimeout = DEFAULT_TIMEOUT;

    /**
     * The local address, which may be useful when working with multiple
     * network interfaces.
     */
    private String localAddress;

    /**
     * Whether 'Expect: 100-continue' handshake is enabled.
     * See {@link RequestConfig#isExpectContinueEnabled()}
     */
    private boolean expectContinueEnabled;

    /**
     * The maximum number of redirects to be followed.  This can help
     * prevent infinite loops.  A value of zero effectively disables
     * redirects.  Default is {@link #DEFAULT_MAX_REDIRECT}.
     */
    private int maxRedirects = DEFAULT_MAX_REDIRECT;

    /**
     * The maximum number of connections that can be created.  Typically,
     * you would have at least the same amount as threads.
     * Default is {@link #DEFAULT_MAX_CONNECTIONS}.
     */
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;

    /**
     * The maximum number of connections that can be used per route.
     * Default is {@link #DEFAULT_MAX_CONNECTIONS_PER_ROUTE}.
     */
    private int maxConnectionsPerRoute = DEFAULT_MAX_CONNECTIONS_PER_ROUTE;

    /**
     * Sets the period of time after which to evict idle
     * connections from the connection pool.
     * Default is {@link #DEFAULT_MAX_IDLE_TIME}.
     */
    private Duration maxConnectionIdleTime = DEFAULT_MAX_IDLE_TIME;

    /**
     * Sets the period of time a connection must be inactive
     * to be checked in case it became stalled. Default is 0 (not pro-actively
     * checked).
     */
    private Duration maxConnectionInactiveTime;

    /**
     * Headers to send with every HTTP request.
     */
    private final Map<String, String> requestHeaders = new HashMap<>();

    /**
     * Whether adding the <code>If-Modified-Since</code> HTTP request
     * header is disabled.
     * Servers supporting this header will only return the requested document
     * if it was last modified since the supplied date.
     */
    private boolean ifModifiedSinceDisabled;

    /**
     * Whether adding "ETag" <code>If-None-Match</code>
     * HTTP request header is disabled.
     * Servers supporting this header will only return the requested document
     * if the ETag value has changed, indicating a more recent version is
     * available.
     */
    private boolean eTagDisabled;

    /**
     * The user-agent used when identifying the crawler to targeted web sites.
     * <b>It is highly recommended to always identify yourself.</b>
     */
    private String userAgent;

    /**
     * The redirect URL provider.
     * Defaults to {@link GenericRedirectUrlProvider}.
     */
    private RedirectUrlProvider redirectUrlProvider =
            new GenericRedirectUrlProvider();

    /**
     * List of supported HTTP methods.
     */
    private final List<HttpMethod> httpMethods = new ArrayList<>(
            Arrays.asList(
                    HttpMethod.GET, HttpMethod.HEAD));

    // Security settings

    /**
     * Sets whether to trust all SSL certificate (affects only "https"
     * connections).  This is typically a bad idea if you care to avoid
     * "man-in-the-middle" attacks. Try to install a SSL
     * certificate locally to ensure a proper certificate exchange instead.
     * @since 1.3.0
     */
    private boolean trustAllSSLCertificates;

    /**
     * Sets whether Server Name Indication (SNI) is disabled.
     */
    private boolean sniDisabled;

    /**
     * Supported security protocols.
     */
    private final List<String> sslProtocols = new ArrayList<>();

    /**
     * Gets whether the forcing of non secure URLs to secure ones is disabled,
     * according to the URL domain <code>Strict-Transport-Security</code> policy
     * (obtained from HTTP response header).
     */
    private boolean hstsDisabled;

    /**
     * Sets valid HTTP response status codes.
     * @return valid status codes
     */
    public List<Integer> getValidStatusCodes() {
        return Collections.unmodifiableList(validStatusCodes);
    }

    /**
     * Gets valid HTTP response status codes.
     * @param validStatusCodes valid status codes
     * @return this
     */
    public HttpClientFetcherConfig setValidStatusCodes(
            List<Integer> validStatusCodes) {
        CollectionUtil.setAll(this.validStatusCodes, validStatusCodes);
        return this;
    }

    /**
     * Gets HTTP status codes to be considered as "Not found" state.
     * Default is 404.
     * @return "Not found" codes
     */
    public List<Integer> getNotFoundStatusCodes() {
        return Collections.unmodifiableList(notFoundStatusCodes);
    }

    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @return this
     */
    public final HttpClientFetcherConfig setNotFoundStatusCodes(
            List<Integer> notFoundStatusCodes) {
        CollectionUtil.setAll(this.notFoundStatusCodes, notFoundStatusCodes);
        return this;
    }

    /**
     * Sets a default HTTP request header every HTTP connection should have.
     * Those are in addition to any default request headers Apache HttpClient
     * may already provide.
     * @param name HTTP request header name
     * @param value HTTP request header value
     * @return this
     */
    public HttpClientFetcherConfig setRequestHeader(
            String name, String value) {
        requestHeaders.put(name, value);
        return this;
    }

    /**
     * Sets a default HTTP request headers every HTTP connection should have.
     * Those are in addition to any default request headers Apache HttpClient
     * may already provide.
     * @param headers map of header names and values
     * @return this
     */
    public HttpClientFetcherConfig setRequestHeaders(
            Map<String, String> headers) {
        CollectionUtil.setAll(requestHeaders, headers);
        return this;
    }

    /**
     * Gets the HTTP request header value matching the given name, previously
     * set with {@link #setRequestHeader(String, String)}.
     * @param name HTTP request header name
     * @return HTTP request header value or {@code null} if
     *         no match is found
     */
    public String getRequestHeader(String name) {
        return requestHeaders.get(name);
    }

    /**
     * Gets all HTTP request header names for headers previously set
     * with {@link #setRequestHeader(String, String)}. If no request headers
     * are set, it returns an empty array.
     * @return HTTP request header names
     */
    @JsonIgnore
    public List<String> getRequestHeaderNames() {
        return Collections.unmodifiableList(
                new ArrayList<>(requestHeaders.keySet()));
    }

    /**
     * Remove the request header matching the given name.
     * @param name name of HTTP request header to remove
     * @return the previous value associated with the name, or {@code null}
     *         if there was no request header for the name.
     */
    public String removeRequestHeader(String name) {
        return requestHeaders.remove(name);
    }

    public ProxySettings getProxySettings() {
        return proxySettings;
    }

    public HttpClientFetcherConfig setProxySettings(ProxySettings proxy) {
        proxySettings.copyFrom(proxy);
        return this;
    }

    /**
     * Gets the supported SSL/TLS protocols.  Default is {@code null},
     * which means it will use those provided/configured by your Java
     * platform.
     * @return SSL/TLS protocols
     */
    public List<String> getSslProtocols() {
        return Collections.unmodifiableList(sslProtocols);
    }

    /**
     * Sets the supported SSL/TLS protocols, such as SSLv3, TLSv1, TLSv1.1,
     * and TLSv1.2.  Note that specifying a protocol not supported by
     * your underlying Java platform will not work.
     * @param sslProtocols SSL/TLS protocols supported
     * @return this
     */
    public HttpClientFetcherConfig setSslProtocols(
            List<String> sslProtocols) {
        CollectionUtil.setAll(this.sslProtocols, sslProtocols);
        return this;
    }

    /**
     * Gets the list of HTTP methods to be accepted by this fetcher.
     * Defaults are {@link HttpMethod#GET} and {@link HttpMethod#HEAD}.
     * @return HTTP methods
     */
    public List<HttpMethod> getHttpMethods() {
        return Collections.unmodifiableList(httpMethods);
    }

    /**
     * Sets the list of HTTP methods to be accepted by this fetcher.
     * Defaults are {@link HttpMethod#GET} and {@link HttpMethod#HEAD}.
     * @param httpMethods HTTP methods
     * @return this
     */
    public HttpClientFetcherConfig setHttpMethods(
            List<HttpMethod> httpMethods) {
        CollectionUtil.setAll(this.httpMethods, httpMethods);
        return this;
    }
}
