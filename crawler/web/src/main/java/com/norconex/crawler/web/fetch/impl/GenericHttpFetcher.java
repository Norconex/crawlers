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
package com.norconex.crawler.web.fetch.impl;

import static com.norconex.crawler.web.fetch.HttpMethod.GET;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.hc.core5.util.TimeValue.ofMilliseconds;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizerConfig.Normalization;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.util.ApacheHttpUtil;
import com.norconex.crawler.web.fetch.util.ApacheRedirectCaptureStrategy;
import com.norconex.crawler.web.fetch.util.HstsResolver;
import com.norconex.importer.charset.CharsetDetector;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Default implementation of {@link HttpFetcher}, based on Apache HttpClient.
 * </p>
 *
 * <p>
 * The "validStatusCodes" and "notFoundStatusCodes" configuration options
 * expect a comma-separated list of HTTP response codes.  If a code is added
 * to both, the valid list takes precedence.
 * </p>
 *
 * <h3>Accepted HTTP methods</h3>
 * <p>
 * By default this fetcher accepts HTTP GET and HEAD requests.  You can limit
 * it to only process one method with
 * {@link GenericHttpFetcherConfig#setHttpMethods(List)}.
 * </p>
 *
 * <h3>Content type and character encoding</h3>
 * <p>
 * The default way for the Web Crawler to identify the content type
 * and character encoding of a document is to rely on the
 * "<a href="https://www.w3.org/Protocols/rfc1341/4_Content-Type.html">Content-Type</a>"
 * HTTP response header.  Web servers can sometimes return invalid
 * or missing content type and character encoding information.
 * You can optionally decide not to trust web servers HTTP responses and have
 * the collector perform its own content type and encoding detection.
 * Such detection can be enabled with
 * {@link GenericHttpFetcherConfig#setForceContentTypeDetection(boolean)}
 * and {@link GenericHttpFetcherConfig#setForceCharsetDetection(boolean)}.
 * </p>
 *
 * <h3>HSTS Support</h3>
 * <p>
 * Upon first encountering a secure site, this fetcher will check whether the
 * site root domain has the "Strict-Transport-Security" (HSTS) policy support
 * part of its HTTP response headers. That information gets cached for future
 * requests. If the site supports HSTS, any non-secure URLs encountered
 * on the same domain will be automatically converted to "https" (including
 * sub-domains if HSTS indicates as such).
 * </p>
 * <p>
 * If you want to convert non-secure URLs secure ones regardless of website
 * HSTS support, use {@link Normalization#SECURE_SCHEME} instead.
 * To disable HSTS support, use
 * {@link GenericHttpFetcherConfig#setHstsDisabled(boolean)}.
 * </p>
 *
 * <h3>Pro-active change detection</h3>
 * <p>
 * This fetcher takes advantage of the
 * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag">
 * ETag</a> and
 * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-Modified-Since">
 * If-Modified-Since</a> HTTP specifications.
 * </p>
 * <p>
 * On subsequent crawls, HTTP requests will include previously cached
 * <code>ETag</code> and <code>If-Modified-Since</code> values to tell
 * supporting servers we only want to download a document if it was modified
 * since our last request.
 * To disable support for pro-active change detection, you can use
 * {@link GenericHttpFetcherConfig#setIfModifiedSinceDisabled(boolean)} and
 * {@link GenericHttpFetcherConfig#setETagDisabled(boolean)}.
 * </p>
 * <p>
 * These settings have no effect for web servers not supporting them.
 * </p>
 *
 * @since 3.0.0 (Merged from GenericDocumentFetcher and
 *        GenericHttpClientFactory)
 */
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class GenericHttpFetcher
        extends AbstractFetcher<
                HttpFetchRequest, HttpFetchResponse, GenericHttpFetcherConfig>
        implements HttpFetcher {

    private static final int FTP_PORT = 80;

    static final SchemePortResolver SCHEME_PORT_RESOLVER = host -> {
        Args.notNull(host, "HTTP host");
        final var port = host.getPort();
        if (port > 0) {
            return port;
        }
        final var name = host.getSchemeName();
        if ("ftp".equalsIgnoreCase(name)) {
            return FTP_PORT;
        }
        return DefaultSchemePortResolver.INSTANCE.resolve(host);
    };

    @Getter
    @EqualsAndHashCode.Include
    @ToString.Include
    private final GenericHttpFetcherConfig configuration =
            new GenericHttpFetcherConfig();

    private HttpClient httpClient;
    private final AuthCache authCache = new BasicAuthCache();
    private Object userToken;

    @Override
    public HttpFetchResponse fetch(HttpFetchRequest fetchRequest)
            throws FetchException {
        var doc = fetchRequest.getDoc();
        var httpMethod = fetchRequest.getMethod();

        if (httpClient == null) {
            throw new IllegalStateException(
                    "GenericHttpFetcher was not "
                            + "initialized ('httpClient' not set).");
        }

        HttpUriRequestBase request = null;
        try {

            //--- HSTS Policy --------------------------------------------------
            if (!configuration.isHstsDisabled()) {
                HstsResolver.resolve(
                        httpClient, (WebCrawlDocContext) doc.getDocContext());
            }

            //--- Prepare the request ------------------------------------------

            LOG.debug("Fetching: {}", doc.getReference());

            var method = ofNullable(httpMethod).orElse(GET);
            request = ApacheHttpUtil.createUriRequest(
                    doc.getReference(), method);

            var ctx = HttpClientContext.create();
            // auth cache
            ctx.setAuthCache(authCache);
            // user token
            if (userToken != null) {
                ctx.setUserToken(userToken);
            }

            if (!configuration.isETagDisabled()) {
                ApacheHttpUtil.setRequestIfNoneMatch(request, doc);
            }
            if (!configuration.isIfModifiedSinceDisabled()) {
                ApacheHttpUtil.setRequestIfModifiedSince(request, doc);
            }

            // Execute the method.
            return httpClient.execute(request, ctx, response -> {
                //--- Process the response -------------------------------------

                var statusCode = response.getCode();
                var reason = response.getReasonPhrase();

                LOG.debug(
                        "Fetch status for: \"{}\": {} - {}",
                        doc.getReference(), statusCode, reason);

                var responseBuilder = GenericHttpFetchResponse.builder()
                        .statusCode(statusCode)
                        .reasonPhrase(reason)
                        .userAgent(configuration.getUserAgent())
                        .redirectTarget(
                                ApacheRedirectCaptureStrategy
                                        .getRedirectTarget(ctx));

                //--- Extract headers ---
                ApacheHttpUtil.applyResponseHeaders(
                        response, configuration.getHeadersPrefix(), doc);

                //--- Extract body ---
                if (HttpMethod.GET.is(method) || HttpMethod.POST.is(method)) {
                    if (ApacheHttpUtil.applyResponseContent(response, doc)) {
                        performDetection(doc);
                    } else {
                        LOG.debug(
                                "No content returned for: {}",
                                doc.getReference());
                    }
                }

                //--- VALID http response handling -----------------------------
                if (configuration.getValidStatusCodes().contains(statusCode)) {
                    userToken = ctx.getUserToken();

                    return responseBuilder
                            .resolutionStatus(DocResolutionStatus.NEW)
                            .build();
                }

                // UNMODIFIED
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    return responseBuilder
                            .resolutionStatus(DocResolutionStatus.UNMODIFIED)
                            .build();
                }

                //--- INVALID http response handling ---------------------------

                // NOT_FOUND
                if (configuration.getNotFoundStatusCodes()
                        .contains(statusCode)) {
                    return responseBuilder
                            .resolutionStatus(DocResolutionStatus.NOT_FOUND)
                            .build();
                }

                // BAD_STATUS
                LOG.debug(
                        "Unsupported HTTP Response: {}",
                        response.getReasonPhrase());
                return responseBuilder
                        .resolutionStatus(DocResolutionStatus.BAD_STATUS)
                        .build();
            });

        } catch (Exception e) {
            analyseException(e);
            //MAYBE set exception on response instead?
            throw new FetchException(
                    "Could not fetch document: " + doc.getReference(), e);
        }
    }

    @Override
    protected boolean acceptRequest(@NonNull HttpFetchRequest fetchRequest) {
        return configuration.getHttpMethods().contains(
                fetchRequest.getMethod());
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    protected void fetcherStartup(CrawlerContext crawler) {
        httpClient = createHttpClient();
        var userAgent = configuration.getUserAgent();
        if (StringUtils.isBlank(userAgent)) {
            LOG.info("User-Agent: <None specified>");
            LOG.debug("""
                    It is recommended you identify yourself to web sites\s\
                    by specifying a user agent\s\
                    (https://en.wikipedia.org/wiki/User_agent)""");
        } else {
            LOG.info("User-Agent: {}", userAgent);
        }

        if (configuration.getAuthentication() != null
                && HttpAuthMethod.FORM == configuration.getAuthentication()
                        .getMethod()) {
            authenticateUsingForm(httpClient);
        }
    }

    @Override
    protected void fetcherShutdown(CrawlerContext c) {
        if (httpClient instanceof CloseableHttpClient hc) {
            try {
                hc.close();
            } catch (IOException e) {
                LOG.error("Cannot close HttpClient.", e);
            }
        }
    }

    public String getUserAgent() {
        return configuration.getUserAgent();
    }

    //TODO remove this method and configuration options: always do it
    // by framework?  Could be useful to also do it here to leverage
    // getting those values from HTTP headers or other fetcher-specific
    // ways of doing it.
    private void performDetection(Doc doc) {
        var docRecord = doc.getDocContext();
        try {
            if (configuration.isForceContentTypeDetection()
                    || docRecord.getContentType() == null) {
                docRecord.setContentType(
                        ContentTypeDetector.detect(
                                doc.getInputStream(), doc.getReference()));
            }
        } catch (IOException e) {
            LOG.warn("Cannont perform content type detection.", e);
        }
        try {
            if (configuration.isForceCharsetDetection()
                    || docRecord.getCharset() == null) {
                docRecord.setCharset(
                        CharsetDetector.builder()
                                .build().detect(doc.getInputStream()));
            }
        } catch (IOException e) {
            LOG.warn("Cannot perform charset type detection.", e);
        }
    }

    //TODO Offer global PropertySetter option when adding headers and/or
    // other fields

    protected HttpClient createHttpClient() {
        var builder = HttpClientBuilder.create();
        var schemePortResolver = createSchemePortResolver();
        ofNullable(createRoutePlanner(schemePortResolver)).ifPresent(
                builder::setRoutePlanner);

        builder.setConnectionManager(createConnectionManager());
        builder.setSchemePortResolver(schemePortResolver);
        builder.setDefaultRequestConfig(createRequestConfig());
        builder.setProxy(createProxy());
        builder.setDefaultCredentialsProvider(createCredentialsProvider());
        builder.setUserAgent(configuration.getUserAgent());
        builder.evictExpiredConnections();
        ofNullable(configuration.getMaxConnectionIdleTime()).ifPresent(
                d -> builder
                        .evictIdleConnections(ofMilliseconds(d.toMillis())));
        builder.setDefaultHeaders(createDefaultRequestHeaders());
        builder.setDefaultCookieStore(createDefaultCookieStore());
        builder.setRedirectStrategy(
                new ApacheRedirectCaptureStrategy(
                        configuration.getRedirectUrlProvider()));

        buildCustomHttpClient(builder);

        return builder.build();
    }

    protected HttpClientConnectionManager createConnectionManager() {
        final var sslContext = createSSLContext();
        final var sslSocketFactory = createSSLSocketFactory(sslContext);

        var tlsBuilder = TlsConfig.custom();

        ofNullable(configuration.getSocketTimeout()).ifPresent(
                d -> tlsBuilder.setHandshakeTimeout(
                        d.toMillis(), TimeUnit.MINUTES));
        if (!configuration.getSslProtocols().isEmpty()) {
            tlsBuilder.setSupportedProtocols(
                    configuration
                            .getSslProtocols()
                            .toArray(EMPTY_STRING_ARRAY));
        }
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .setDefaultTlsConfig(tlsBuilder.build())
                .setDefaultConnectionConfig(createConnectionConfig())
                .setMaxConnTotal(configuration.getMaxConnections())
                .setMaxConnPerRoute(configuration.getMaxConnectionsPerRoute())
                .build();
    }

    protected HttpRoutePlanner createRoutePlanner(
            SchemePortResolver schemePortResolver) {
        if (StringUtils.isBlank(configuration.getLocalAddress())) {
            return null;
        }
        return new DefaultRoutePlanner(schemePortResolver) {
            @Override
            protected InetAddress determineLocalAddress(
                    HttpHost firstHop,
                    HttpContext context) throws HttpException {
                try {
                    return InetAddress.getByName(
                            configuration.getLocalAddress());
                } catch (UnknownHostException e) {
                    throw new CrawlerException(
                            "Invalid local address: {}"
                                    + configuration.getLocalAddress(),
                            e);
                }
            }
        };
    }

    /**
     * For implementors to subclass.  Does nothing by default.
     * @param builder http client builder
     */
    protected void buildCustomHttpClient(HttpClientBuilder builder) {
        //do nothing by default
    }

    protected void authenticateUsingForm(HttpClient httpClient) {
        try {
            ApacheHttpUtil.authenticateUsingForm(
                    httpClient, configuration.getAuthentication());
        } catch (IOException | URISyntaxException e) {
            analyseException(e);
            throw new CrawlerException(
                    "Could not perform FORM-based authentication.", e);
        }
    }

    /**
     * Creates the default cookie store to be added to each request context.
     * @return a cookie store
     */
    protected CookieStore createDefaultCookieStore() {
        return new BasicCookieStore();
    }

    /**
     * <p>
     * Creates a list of HTTP headers based on configuration.
     * </p>
     * <p>
     * This method will also add a "Basic" authentication
     * header if "preemptive" is <code>true</code> on the authentication
     * configuration and credentials were supplied.
     * </p>
     * @return a list of HTTP request headers
     */
    protected List<Header> createDefaultRequestHeaders() {
        //--- Configuration-defined headers
        List<Header> headers = new ArrayList<>();
        for (String name : configuration.getRequestHeaderNames()) {
            headers.add(
                    new BasicHeader(
                            name, configuration.getRequestHeader(name)));
        }

        //--- preemptive headers
        // preemptive authorisation could be done by creating a HttpContext
        // passed to the HttpClient execute method, but since that method
        // is not invoked from this class, we want to keep things
        // together and we add the preemptive authentication directly
        // in the default HTTP headers.
        if (configuration.getAuthentication() != null
                && configuration.getAuthentication().isPreemptive()) {
            var authConfig = configuration.getAuthentication();
            if (StringUtils.isBlank(
                    authConfig.getCredentials().getUsername())) {
                LOG.warn(
                        "Preemptive authentication is enabled while no "
                                + "username was provided.");
                return headers;
            }
            if (HttpAuthMethod.BASIC != authConfig.getMethod()) {
                LOG.warn("""
                        Using preemptive authentication with a\s\
                        method other than "Basic" may not produce the\s\
                        expected outcome.""");
            }
            var password = EncryptionUtil.decryptPassword(
                    authConfig.getCredentials());
            var auth = authConfig.getCredentials().getUsername()
                    + ":" + password;
            var encodedAuth = Base64.encodeBase64(
                    auth.getBytes(StandardCharsets.ISO_8859_1));
            var authHeader = "Basic " + new String(encodedAuth);
            headers.add(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader));
        }
        return headers;
    }

    protected SchemePortResolver createSchemePortResolver() {
        return SCHEME_PORT_RESOLVER;
    }

    protected RequestConfig createRequestConfig() {
        var builder = RequestConfig.custom();

        ofNullable(configuration.getConnectionRequestTimeout()).ifPresent(
                d -> builder.setConnectionRequestTimeout(
                        d.toMillis(), TimeUnit.MILLISECONDS));
        builder.setMaxRedirects(configuration.getMaxRedirects())
                .setExpectContinueEnabled(
                        configuration.isExpectContinueEnabled())
                .setCookieSpec(
                        Objects.toString(
                                configuration.getCookieSpec(), null));
        if (configuration.getMaxRedirects() <= 0) {
            builder.setRedirectsEnabled(false);
        }
        return builder.build();
    }

    protected HttpHost createProxy() {
        if (configuration.getProxySettings().isSet()) {
            return new HttpHost(
                    configuration.getProxySettings().getScheme(),
                    configuration.getProxySettings().getHost().getName(),
                    configuration.getProxySettings().getHost().getPort());
        }
        return null;
    }

    protected CredentialsProvider createCredentialsProvider() {
        BasicCredentialsProvider credsProvider = null;

        //--- Proxy ---
        var proxy = configuration.getProxySettings();
        if (proxy.isSet() && proxy.getCredentials().isSet()) {
            var password = EncryptionUtil.decryptPassword(
                    proxy.getCredentials());
            credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(
                            new HttpHost(
                                    proxy.getHost().getName(),
                                    proxy.getHost().getPort()),
                            proxy.getRealm(),
                            null),
                    new UsernamePasswordCredentials(
                            proxy.getCredentials().getUsername(),
                            trimToEmpty(password).toCharArray()));
        }

        //--- Auth ---
        var authConfig = configuration.getAuthentication();
        if (authConfig != null
                && authConfig.getCredentials().isSet()
                && HttpAuthMethod.FORM != authConfig.getMethod()
                && authConfig.getHost() != null) {
            if (credsProvider == null) {
                credsProvider = new BasicCredentialsProvider();
            }
            Credentials creds = null;
            var password = EncryptionUtil.decryptPassword(
                    authConfig.getCredentials());
            if (HttpAuthMethod.NTLM == authConfig.getMethod()) {
                creds = new NTCredentials(
                        authConfig.getCredentials().getUsername(),
                        trimToEmpty(password).toCharArray(),
                        authConfig.getWorkstation(),
                        authConfig.getDomain());
            } else {
                creds = new UsernamePasswordCredentials(
                        authConfig.getCredentials().getUsername(),
                        trimToEmpty(password).toCharArray());
            }
            credsProvider.setCredentials(
                    new AuthScope(
                            new HttpHost(
                                    authConfig.getHost().getName(),
                                    authConfig.getHost().getPort()),
                            authConfig.getRealm(),
                            Objects.toString(authConfig.getMethod(), null)),
                    creds);
        }
        return credsProvider;
    }

    protected ConnectionConfig createConnectionConfig() {
        var builder = ConnectionConfig.custom();
        ofNullable(configuration.getConnectionTimeout()).ifPresent(
                d -> builder.setConnectTimeout(
                        d.toMillis(), TimeUnit.MILLISECONDS));
        ofNullable(configuration.getSocketTimeout()).ifPresent(
                d -> builder.setSocketTimeout(
                        Timeout.ofMilliseconds(d.toMillis())));
        ofNullable(configuration.getMaxConnectionInactiveTime()).ifPresent(
                d -> builder.setValidateAfterInactivity(
                        TimeValue.ofMilliseconds(d.toMillis())));
        return builder.build();
    }

    protected SSLConnectionSocketFactory createSSLSocketFactory(
            SSLContext sslContext) {
        if (!configuration.isTrustAllSSLCertificates()
                && configuration.getSslProtocols().isEmpty()) {
            return null;
        }

        var context = sslContext;
        if (context == null) {
            try {
                context = SSLContexts.custom()
                        .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                        .build();
            } catch (KeyManagementException | NoSuchAlgorithmException
                    | KeyStoreException e) {
                throw new CrawlerException("Cannot create SSL context.", e);
            }
        }

        // Turn off host name verification and remove all algorithm constraints.
        return new SSLConnectionSocketFactory(
                context, new NoopHostnameVerifier()) {
            @Override
            protected void prepareSocket(SSLSocket socket)
                    throws IOException {
                var sslParams = new SSLParameters();

                // Trust all certificates
                if (configuration.isTrustAllSSLCertificates()) {
                    LOG.debug("SSL: Turning off host name verification.");
                    sslParams.setAlgorithmConstraints(
                            new AlgorithmConstraints() {
                                @Override
                                public boolean permits(
                                        Set<CryptoPrimitive> primitives,
                                        Key key) {
                                    return true;
                                }

                                @Override
                                public boolean permits(
                                        Set<CryptoPrimitive> primitives,
                                        String algorithm,
                                        AlgorithmParameters parameters) {
                                    return true;
                                }

                                @Override
                                public boolean permits(
                                        Set<CryptoPrimitive> primitives,
                                        String algorithm, Key key,
                                        AlgorithmParameters parameters) {
                                    return true;
                                }
                            });
                }

                // Specify protocols
                if (!configuration.getSslProtocols().isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(
                                "SSL: Protocols={}", StringUtils.join(
                                        configuration.getSslProtocols(), ","));
                    }
                    sslParams.setProtocols(
                            configuration.getSslProtocols()
                                    .toArray(ArrayUtils.EMPTY_STRING_ARRAY));
                }

                sslParams.setEndpointIdentificationAlgorithm("HTTPS");

                if (configuration.isSniDisabled()) {
                    // Disabling SNI extension introduced in Java 7 is necessary
                    // to avoid
                    // SSLProtocolException: handshake alert: unrecognized_name
                    // for some sites with wrong Virtual Host - config.
                    // Described here:
                    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=7127374
                    // Instead of using the SystemProperty to disable SNI,
                    // follow the approach from here
                    // https://github.com/lightbody/browsermob-proxy/issues/117#issuecomment-141363454
                    // and disable SNI for this SSLConnectionSocketFactory only
                    LOG.debug(
                            "SSL: Disabling SNI Extension for this "
                                    + "httpClientFactory.");
                    sslParams.setServerNames(Collections.emptyList());
                }

                socket.setSSLParameters(sslParams);
            }
        };
    }

    protected SSLContext createSSLContext() {
        if (!configuration.isTrustAllSSLCertificates()) {
            return null;
        }
        LOG.info("SSL: Trusting all certificates.");

        // Use a trust strategy that always returns true
        try {
            return SSLContexts.custom()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();
        } catch (Exception e) {
            throw new CrawlerException(
                    "Cannot create SSL context trusting all certificates.", e);
        }
    }

    private void analyseException(Exception e) {
        if (e instanceof SSLHandshakeException
                && !configuration.isTrustAllSSLCertificates()) {
            LOG.warn(
                    "SSL handshake exception. Consider "
                            + "setting 'trustAllSSLCertificates' to true.");
        }
    }
}
