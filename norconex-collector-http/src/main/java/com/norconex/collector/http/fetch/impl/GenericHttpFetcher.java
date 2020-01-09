/* Copyright 2018-2020 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.AbstractHttpFetcher;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpFetcher;
import com.norconex.collector.http.fetch.util.RedirectStrategyWrapper;
import com.norconex.collector.http.fetch.util.TrustAllX509TrustManager;
import com.norconex.collector.http.reference.HttpCrawlState;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.util.CharsetUtil;

/**
 * <p>
 * Default implementation of {@link IHttpFetcher}.
 * </p>
 *
 * <p>
 * The "validStatusCodes" and "notFoundStatusCodes" elements expect a
 * coma-separated list of HTTP response code.  If a code is added in both
 * elements, the valid list takes precedence.
 * </p>
 *
 * <h3>Content type and character encoding</h3>
 * <p>
 * The default way for the HTTP Collector to identify the content type
 * and character encoding of a document is to rely on the
 * "<a href="https://www.w3.org/Protocols/rfc1341/4_Content-Type.html">Content-Type</a>"
 * HTTP response header.  Web servers can sometimes return invalid
 * or missing content type and character encoding information.
 * You can optionally decide not to trust web servers HTTP responses and have
 * the collector perform its own content type and encoding detection.
 * Such detection can be enabled with
 * {@link GenericHttpFetcherConfig#setDetectContentType(boolean)}
 * and {@link GenericHttpFetcherConfig#setDetectCharset(boolean)}.
 * </p>
 *
 * <h3>Password encryption in XML configuration:</h3>
 * <p>
 * <code>proxyPassword</code> and <code>authPassword</code>
 * can take a password that has been encrypted using {@link EncryptionUtil}.
 * In order for the password to be decrypted properly by the crawler, you need
 * to specify the encryption key used to encrypt it. The key can be stored
 * in a few supported locations and a combination of
 * <code>[auth|proxy]PasswordKey</code>
 * and <code>[auth|proxy]PasswordKeySource</code> must be specified to properly
 * locate the key. The supported sources are:
 * </p>
 * <table border="1" summary="">
 *   <tr>
 *     <th><code>[...]PasswordKeySource</code></th>
 *     <th><code>[...]PasswordKey</code></th>
 *   </tr>
 *   <tr>
 *     <td><code>key</code></td>
 *     <td>The actual encryption key.</td>
 *   </tr>
 *   <tr>
 *     <td><code>file</code></td>
 *     <td>Path to a file containing the encryption key.</td>
 *   </tr>
 *   <tr>
 *     <td><code>environment</code></td>
 *     <td>Name of an environment variable containing the key.</td>
 *   </tr>
 *   <tr>
 *     <td><code>property</code></td>
 *     <td>Name of a JVM system property containing the key.</td>
 *   </tr>
 * </table>
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <pre>{@code
 * <fetcher class="com.norconex.collector.http.fetch.impl.GenericHttpFetcher">
 *
 *     <userAgent>(identify yourself!)</userAgent>
 *     <cookiesDisabled>[false|true]</cookiesDisabled>
 *     <connectionTimeout>(milliseconds)</connectionTimeout>
 *     <socketTimeout>(milliseconds)</socketTimeout>
 *     <connectionRequestTimeout>(milliseconds)</connectionRequestTimeout>
 *     <connectionCharset>...</connectionCharset>
 *     <expectContinueEnabled>[false|true]</expectContinueEnabled>
 *     <maxRedirects>...</maxRedirects>
 *     <redirectURLProvider>(implementation handling redirects)</redirectURLProvider>
 *     <localAddress>...</localAddress>
 *     <maxConnections>...</maxConnections>
 *     <maxConnectionsPerRoute>...</maxConnectionsPerRoute>
 *     <maxConnectionIdleTime>(milliseconds)</maxConnectionIdleTime>
 *     <maxConnectionInactiveTime>(milliseconds)</maxConnectionInactiveTime>
 *
 *     <!-- Be warned: trusting all certificates is usually a bad idea. -->
 *     <trustAllSSLCertificates>[false|true]</trustAllSSLCertificates>
 *
 *     <!-- You can specify SSL/TLS protocols to use -->
 *     <sslProtocols>(coma-separated list)</sslProtocols>
 *
 *     <!-- Disable Server Name Indication (SNI) -->
 *     <disableSNI>[false|true]</disableSNI>
 *
 *     <proxyHost>...</proxyHost>
 *     <proxyPort>...</proxyPort>
 *     <proxyRealm>...</proxyRealm>
 *     <proxyScheme>...</proxyScheme>
 *     <proxyUsername>...</proxyUsername>
 *     <proxyPassword>...</proxyPassword>
 *     <!-- Use the following if password is encrypted. -->
 *     <proxyPasswordKey>(the encryption key or a reference to it)</proxyPasswordKey>
 *     <proxyPasswordKeySource>[key|file|environment|property]</proxyPasswordKeySource>
 *
 *     <!-- HTTP request headers passed on every HTTP requests -->
 *     <headers>
 *         <header name="(header name)">(header value)</header>
 *         <!-- You can repeat this header tag as needed. -->
 *     </headers>
 *
 *     <authMethod>[form|basic|digest|ntlm|spnego|kerberos]</authMethod>
 *
 *     <!-- These apply to any authentication mechanism -->
 *     <authUsername>...</authUsername>
 *     <authPassword>...</authPassword>
 *     <!-- Use the following if password is encrypted. -->
 *     <authPasswordKey>(the encryption key or a reference to it)</authPasswordKey>
 *     <authPasswordKeySource>[key|file|environment|property]</authPasswordKeySource>
 *
 *     <!-- These apply to FORM authentication -->
 *     <authUsernameField>...</authUsernameField>
 *     <authPasswordField>...</authPasswordField>
 *     <authURL>...</authURL>
 *     <authFormCharset>...</authFormCharset>
 *     <!-- Extra form parameters required to authenticate (since 2.8.0) -->
 *     <authFormParams>
 *         <param name="(param name)">(param value)</param>
 *         <!-- You can repeat this param tag as needed. -->
 *     </authFormParams>
 *
 *     <!-- These apply to both BASIC and DIGEST authentication -->
 *     <authHostname>...</authHostname>
 *     <authPort>...</authPort>
 *     <authRealm>...</authRealm>
 *
 *     <!-- This applies to BASIC authentication -->
 *     <authPreemptive>[false|true]</authPreemptive>
 *
 *     <!-- These apply to NTLM authentication -->
 *     <authHostname>...</authHostname>
 *     <authPort>...</authPort>
 *     <authWorkstation>...</authWorkstation>
 *     <authDomain>...</authDomain>
 *
 *     <validStatusCodes>(defaults to 200)</validStatusCodes>
 *     <notFoundStatusCodes>(defaults to 404)</notFoundStatusCodes>
 *     <headersPrefix>(string to prefix headers)</headersPrefix>
 *     <detectContentType>[false|true]</detectContentType>
 *     <detectCharset>[false|true]</detectCharset>
 *
 *     <restrictions>
 *         <restrictTo caseSensitive="[false|true]"
 *                 field="(name of metadata field name to match)">
 *             (regular expression of value to match)
 *         </restrictTo>
 *         <!-- multiple "restrictTo" tags allowed (only one needs to match) -->
 *     </restrictions>
 * </fetcher>
 * }</pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following will authenticate the crawler to a web site before crawling.
 * The website uses an HTML form with a username and password fields called
 * "loginUser" and "loginPwd".
 * </p>
 * <pre>{@code
 * <httpClientFactory class="com.norconex.collector.http.client.impl.GenericHttpClientFactory">
 *     <authUsername>joeUser</authUsername>
 *     <authPassword>joePasword</authPassword>
 *     <authUsernameField>loginUser</authUsernameField>
 *     <authPasswordField>loginPwd</authPasswordField>
 *     <authURL>http://www.example.com/login</authURL>
 * </httpClientFactory>
 * }</pre>
 *
 * @author Pascal Essiembre
 * @since 3.0.0 (Merged from GenericDocumentFetcher and
 *        GenericHttpClientFactory)
 */
public class GenericHttpFetcher extends AbstractHttpFetcher {

    private static final Logger LOG =
            LoggerFactory.getLogger(GenericHttpFetcher.class);

    /** Form-based authentication method. */
    public static final String AUTH_METHOD_FORM = "form";
    /** BASIC authentication method. */
    public static final String AUTH_METHOD_BASIC = "basic";
    /** DIGEST authentication method. */
    public static final String AUTH_METHOD_DIGEST = "digest";
    /** NTLM authentication method. */
    public static final String AUTH_METHOD_NTLM = "ntlm";
    /** Experimental: SPNEGO authentication method. */
    public static final String AUTH_METHOD_SPNEGO = "SPNEGO";
    /** Experimental: Kerberos authentication method. */
    public static final String AUTH_METHOD_KERBEROS = "Kerberos";

    private static final int FTP_PORT = 80;

    private static final SchemePortResolver SCHEME_PORT_RESOLVER =
            host -> {
        Args.notNull(host, "HTTP host");
        final int port = host.getPort();
        if (port > 0) {
            return port;
        } else {
            final String name = host.getSchemeName();
            if (name.equalsIgnoreCase("ftp")) {
                return FTP_PORT;
            } else {
                return DefaultSchemePortResolver.INSTANCE.resolve(host);
            }
        }
    };

    private final GenericHttpFetcherConfig cfg;
    private HttpClient httpClient;
    @HashCodeExclude
    @EqualsExclude
    private final AuthCache authCache = new BasicAuthCache();
    private Object userToken;

    public GenericHttpFetcher() {
        this(new GenericHttpFetcherConfig());
    }
    public GenericHttpFetcher(GenericHttpFetcherConfig httpFetcherConfig) {
        super();
        Objects.requireNonNull(
                httpFetcherConfig, "'httpFetcherConfig' must not be null.");
        this.cfg = httpFetcherConfig;
    }

    public GenericHttpFetcherConfig getConfig() {
        return cfg;
    }
    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    protected void crawlerStartup(CrawlerEvent<Crawler> event) {
        this.httpClient = createHttpClient();
        initializeRedirectionStrategy();

        String userAgent = cfg.getUserAgent();
        if (StringUtils.isBlank(userAgent)) {
            LOG.info("User-Agent: <None specified>");
            LOG.debug("It is recommended you identify yourself to web sites "
                    + "by specifying a user agent "
                    + "(https://en.wikipedia.org/wiki/User_agent)");
        } else {
            LOG.info("User-Agent: {}", userAgent);
        }
    }
    @Override
    protected void crawlerShutdown(CrawlerEvent<Crawler> event) {
        if (httpClient instanceof CloseableHttpClient) {
            try {
                ((CloseableHttpClient) httpClient).close();
            } catch (IOException e) {
                LOG.error("Cannot close HttpClient.", e);
            }
        }
    }


    @Override
    public String getUserAgent() {
        return cfg.getUserAgent();
    }

    @Override
    public boolean accept(HttpDocument doc) {
        //TODO base it on restrictTo
        return true;
    }

    @Override
    public IHttpFetchResponse fetchHeaders(String url, HttpMetadata headers) {
        return fetch(url, headers, null, true);
    }

    @Override
    public IHttpFetchResponse fetchDocument(HttpDocument doc) {
        MutableObject<CachedInputStream> is =
                new MutableObject<>(doc.getInputStream());
        IHttpFetchResponse response = fetch(
                doc.getReference(), doc.getMetadata(), is, false);
        doc.setInputStream(is.getValue());
        performDetection(doc);
        return response;
    }

    private IHttpFetchResponse fetch(String url, HttpMetadata metadata,
            MutableObject<CachedInputStream> stream, boolean head) {

        HttpFetchResponseBuilder responseBuilder =
                new HttpFetchResponseBuilder();

        //TODO replace signature with Writer class.
        LOG.debug("Fetching document: {}", url);
        HttpRequestBase method = createUriRequest(url, head);
        try {
            HttpClientContext ctx = HttpClientContext.create();
            // auth cache
            ctx.setAuthCache(authCache);
            // user token
            if (userToken != null) {
                ctx.setUserToken(userToken);
            }

            // Execute the method.
            HttpResponse response = httpClient.execute(method, ctx);

            int statusCode = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();

            responseBuilder.setStatusCode(statusCode);
            responseBuilder.setReasonPhrase(reason);
            responseBuilder.setUserAgent(cfg.getUserAgent());

//System.err.println((head ? "HEAD" : "GET") + ": " + response.getStatusLine() + "  ==>  " + url);

            InputStream is = null;
            if (!head) {
                is = response.getEntity().getContent();
            }

            // VALID http response
            if (cfg.getValidStatusCodes().contains(statusCode)) {
                //--- Fetch headers ---
                Header[] headers = response.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    Header header = headers[i];
                    String name = header.getName();
                    if (StringUtils.isNotBlank(cfg.getHeadersPrefix())) {
                        name = cfg.getHeadersPrefix() + name;
                    }
                    if (metadata.getString(name) == null) {
                        metadata.add(name, header.getValue());
                    }
                }

                if (!head) {
                    //--- Fetch body
                    CachedInputStream content = stream.getValue()
                            .getStreamFactory().newInputStream(is);
                    //read a copy to force caching and then close the stream
                    IOUtils.copy(content, new NullOutputStream());
                    stream.setValue(content);
//                    performDetection(doc);
                }

                userToken = ctx.getUserToken();

                return responseBuilder
                        .setCrawlState(HttpCrawlState.NEW)
                        .build();
            }

            if (!head) {
                // INVALID http response
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Rejected response content: "
                            + IOUtils.toString(is, StandardCharsets.UTF_8));
                    if (is != null) {
                        try { is.close(); } catch (IOException e) { /*NOOP*/ }
                    }
                } else {
                    // read response anyway to be safer, but ignore content
                    BufferedInputStream bis = new BufferedInputStream(is);
                    int result = bis.read();
                    while(result != -1) {
                      result = bis.read();
                    }
                    try { bis.close(); } catch (IOException e) { /*NOOP*/ }
                }
            }

            if (cfg.getNotFoundStatusCodes().contains(statusCode)) {
                return responseBuilder
                        .setCrawlState(HttpCrawlState.NOT_FOUND)
                        .build();
            }
            LOG.debug("Unsupported HTTP Response: {}",
                    response.getStatusLine());
            return responseBuilder
                    .setCrawlState(HttpCrawlState.BAD_STATUS)
                    .build();
        } catch (Exception e) {
            //TODO set exception on response instead?
            LOG.info("Cannot fetch document: {}  ({})",
                    url, e.getMessage(), e);
            throw new CollectorException(e);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    //TODO remove this method and configuration options: always do it
    // by framework?  Then how to leverage getting it from client
    // directly (e.g. http response headers)?  Rely on metadata for that?
    private void performDetection(HttpDocument doc) {
        try {
            if (cfg.isDetectContentType()) {
                ContentType ct = ContentTypeDetector.detect(
                        doc.getInputStream(), doc.getReference());
                if (ct != null) {
                    doc.getMetadata().set(
                            HttpMetadata.COLLECTOR_CONTENT_TYPE, ct.toString());
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannont perform content type detection.", e);
        }
        try {
            if (cfg.isDetectCharset()) {
                String charset = CharsetUtil.detectCharset(
                        doc.getInputStream());
                if (StringUtils.isNotBlank(charset)) {
                    doc.getMetadata().set(
                            HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
                }
            }
        } catch (IOException e) {
            LOG.warn("Cannot perform charset type detection.", e);
        }
    }

    //TODO Offer global PropertySetter option when adding headers and/or other fields


    /**
     * Creates the HTTP request to be executed.  Default implementation
     * returns an {@link HttpGet} request around the document reference.
     * This method can be overwritten to return another type of request,
     * add HTTP headers, etc.
     * @param url URL to fetch
     * @param head <code>true</code> to make an HTTP HEAD request
     * @return HTTP request
     */
    protected HttpRequestBase createUriRequest(String url, boolean head) {
        URI uri = HttpURL.toURI(url);
        LOG.debug("Encoded URI: {}", uri);
        if (head) {
            return new HttpHead(uri);
        }
        return new HttpGet(uri);
    }

    protected HttpClient createHttpClient() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        SSLContext sslContext = createSSLContext();
        builder.setSSLContext(sslContext);
        builder.setSSLSocketFactory(createSSLSocketFactory(sslContext));
        builder.setSchemePortResolver(createSchemePortResolver());
        builder.setDefaultRequestConfig(createRequestConfig());
        builder.setProxy(createProxy());
        builder.setDefaultCredentialsProvider(createCredentialsProvider());
        builder.setDefaultConnectionConfig(createConnectionConfig());
        builder.setUserAgent(cfg.getUserAgent());
        builder.setMaxConnTotal(cfg.getMaxConnections());
        builder.setMaxConnPerRoute(cfg.getMaxConnectionsPerRoute());
        builder.evictExpiredConnections();
        builder.evictIdleConnections(
                cfg.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS);
        builder.setDefaultHeaders(createDefaultRequestHeaders());
        builder.setDefaultCookieStore(createDefaultCookieStore());

        buildCustomHttpClient(builder);

        HttpClient client = builder.build();
        if (AUTH_METHOD_FORM.equalsIgnoreCase(cfg.getAuthMethod())) {
            authenticateUsingForm(client);
        }
        hackValidateAfterInactivity(client);
        return client;
    }

    /**
     * This is a hack to work around
     * PoolingHttpClientConnectionManager#setValidateAfterInactivity(int)
     * not being exposed to the builder.
     */
    //TODO get rid of this method in favor of setXXX method when available
    // in a future version of HttpClient (planned for 5.0.0).
    private void hackValidateAfterInactivity(HttpClient httpClient) {
        if (cfg.getMaxConnectionInactiveTime() <= 0) {
            return;
        }
        try {
            Object connManager =
                    FieldUtils.readField(httpClient, "connManager", true);
            if (connManager instanceof PoolingHttpClientConnectionManager) {
                ((PoolingHttpClientConnectionManager) connManager)
                        .setValidateAfterInactivity(
                                cfg.getMaxConnectionInactiveTime());
            } else {
                LOG.warn("\"maxConnectionInactiveTime\" could not be set since "
                        + "internal connection manager does not support it.");
            }
        } catch (Exception e) {
            LOG.warn("\"maxConnectionInactiveTime\" could not be set since "
                    + "internal connection manager does not support it.");
        }
    }


    /**
     * For implementors to subclass.  Does nothing by default.
     * @param builder http client builder
     */
    protected void buildCustomHttpClient(HttpClientBuilder builder) {
        //do nothing by default
    }

    protected void authenticateUsingForm(HttpClient httpClient) {
        HttpPost post = new HttpPost(cfg.getAuthURL());

        List<NameValuePair> formparams = new ArrayList<>();
        formparams.add(new BasicNameValuePair(
                cfg.getAuthUsernameField(), cfg.getAuthUsername()));
        formparams.add(new BasicNameValuePair(
                cfg.getAuthPasswordField(), cfg.getAuthPassword()));

        for (String name : cfg.getAuthFormParamNames()) {
            formparams.add(new BasicNameValuePair(
                    name, cfg.getAuthFormParam(name)));
        }

        LOG.info("Performing FORM authentication at \"{}\" (username={}; p"
                + "assword=*****)", cfg.getAuthURL(), cfg.getAuthUsername());
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(
                    formparams, cfg.getAuthFormCharset());
            post.setEntity(entity);
            HttpResponse response = httpClient.execute(post);
            StatusLine statusLine = response.getStatusLine();
            LOG.info("Authentication status: {}.", statusLine);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Authentication response:\n{}", IOUtils.toString(
                        response.getEntity().getContent(),
                        StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new CollectorException(e);
        }
        post.releaseConnection();

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
     * header if {@link GenericHttpFetcherConfig#setAuthPreemptive(boolean)}
     * is <code>true</code> and
     * credentials were supplied.
     * </p>
     * @return a list of HTTP request headers
     */
    protected List<Header> createDefaultRequestHeaders() {
        //--- Configuration-defined headers
        List<Header> headers = new ArrayList<>();
        for (String name : cfg.getRequestHeaderNames()) {
            headers.add(new BasicHeader(name, cfg.getRequestHeader(name)));
        }

        //--- preemptive headers
        // preemptive authaurisation could be done by creating a HttpContext
        // passed to the HttpClient execute method, but since that method
        // is not invoked from this class, we want to keep things
        // together and we add the preemptive authentication directly
        // in the default HTTP headers.
        if (cfg.isAuthPreemptive()) {
            if (StringUtils.isBlank(cfg.getAuthUsername())) {
                LOG.warn("Preemptive authentication is enabled while no "
                        + "username was provided.");
                return headers;
            }
            if (!AUTH_METHOD_BASIC.equalsIgnoreCase(cfg.getAuthMethod())) {
                LOG.warn("Using preemptive authentication with a "
                        + "method other than \"Basic\" may not produce the "
                        + "expected outcome.");
            }
            String password = EncryptionUtil.decrypt(
                    cfg.getAuthPassword(), cfg.getAuthPasswordKey());
            String auth = cfg.getAuthUsername() + ":" + password;
            byte[] encodedAuth = Base64.encodeBase64(
                    auth.getBytes(StandardCharsets.ISO_8859_1));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.add(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader));
        }
        return headers;
    }

    protected RedirectStrategy createRedirectStrategy() {
        return LaxRedirectStrategy.INSTANCE;
    }

    protected SchemePortResolver createSchemePortResolver() {
        return SCHEME_PORT_RESOLVER;
    }
    protected RequestConfig createRequestConfig() {
        RequestConfig.Builder builder = RequestConfig.custom()
                .setConnectTimeout(cfg.getConnectionTimeout())
                .setSocketTimeout(cfg.getSocketTimeout())
                .setConnectionRequestTimeout(cfg.getConnectionRequestTimeout())
                .setMaxRedirects(cfg.getMaxRedirects())
                .setExpectContinueEnabled(cfg.isExpectContinueEnabled())
                .setCookieSpec(cfg.getCookieSpec());
        if (cfg.getMaxRedirects() <= 0) {
            builder.setRedirectsEnabled(false);
        }
        if (StringUtils.isNotBlank(cfg.getLocalAddress())) {
            try {
                builder.setLocalAddress(
                        InetAddress.getByName(cfg.getLocalAddress()));
            } catch (UnknownHostException e) {
                throw new CollectorException(
                        "Invalid local address: " + cfg.getLocalAddress(), e);
            }
        }
        return builder.build();
    }
    protected HttpHost createProxy() {
        if (StringUtils.isNotBlank(cfg.getProxyHost())) {
            return new HttpHost(cfg.getProxyHost(),
                    cfg.getProxyPort(), cfg.getProxyScheme());
        }
        return null;
    }
    protected CredentialsProvider createCredentialsProvider() {
        CredentialsProvider credsProvider = null;
        //--- Proxy ---
        if (StringUtils.isNotBlank(cfg.getProxyUsername())) {
            String password = EncryptionUtil.decrypt(
                    cfg.getProxyPassword(), cfg.getProxyPasswordKey());
            credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(cfg.getProxyHost(),
                    cfg.getProxyPort(), cfg.getProxyRealm()),
                    new UsernamePasswordCredentials(
                            cfg.getProxyUsername(), password));
        }
        //--- Auth ---
        if (StringUtils.isNotBlank(cfg.getAuthUsername())
                && !AUTH_METHOD_FORM.equalsIgnoreCase(cfg.getAuthMethod())) {
            if (credsProvider == null) {
                credsProvider = new BasicCredentialsProvider();
            }
            Credentials creds = null;
            String password = EncryptionUtil.decrypt(
                    cfg.getAuthPassword(), cfg.getAuthPasswordKey());
            if (AUTH_METHOD_NTLM.equalsIgnoreCase(cfg.getAuthMethod())) {
                creds = new NTCredentials(cfg.getAuthUsername(), password,
                        cfg.getAuthWorkstation(), cfg.getAuthDomain());
            } else {
                creds = new UsernamePasswordCredentials(
                        cfg.getAuthUsername(), password);
            }
            credsProvider.setCredentials(new AuthScope(
                    cfg.getAuthHostname(), cfg.getAuthPort(),
                    cfg.getAuthRealm(), cfg.getAuthMethod()), creds);
        }
        return credsProvider;
    }
    protected ConnectionConfig createConnectionConfig() {
        if (StringUtils.isNotBlank(cfg.getProxyUsername())) {
            return ConnectionConfig.custom()
                    .setCharset(Consts.UTF_8)
                    .build();
        }
        return null;
    }

    protected LayeredConnectionSocketFactory createSSLSocketFactory(
            SSLContext sslContext) {
        if (!cfg.isTrustAllSSLCertificates()
                && cfg.getSSLProtocols().isEmpty()) {
            return null;
        }

        SSLContext context = sslContext;
        if (context == null) {
            try {
                context = SSLContexts.custom().build();
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                throw new CollectorException(
                        "Cannot create SSL context.", e);
            }
        }

        // Turn off host name verification and remove all algorithm constraints.
        return new SSLConnectionSocketFactory(
                        context, new NoopHostnameVerifier()) {
            @Override
            protected void prepareSocket(SSLSocket socket)
                    throws IOException {
                SSLParameters sslParams = new SSLParameters();

                // Trust all certificates
                if (cfg.isTrustAllSSLCertificates()) {
                    LOG.debug("SSL: Turning off host name verification.");
                    sslParams.setAlgorithmConstraints(
                            new AlgorithmConstraints() {
                        @Override
                        public boolean permits(
                                Set<CryptoPrimitive> primitives, Key key) {
                            return true;
                        }
                        @Override
                        public boolean permits(Set<CryptoPrimitive> primitives,
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
                if (!cfg.getSSLProtocols().isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("SSL: Protocols={}",
                                StringUtils.join(cfg.getSSLProtocols(), ","));
                    }
                    sslParams.setProtocols(cfg.getSSLProtocols().toArray(
                            ArrayUtils.EMPTY_STRING_ARRAY));
                }

                sslParams.setEndpointIdentificationAlgorithm("HTTPS");

                if (cfg.isDisableSNI()) {
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
                    LOG.debug("SSL: Disabling SNI Extension for this "
                            + "httpClientFactory.");
                    sslParams.setServerNames(Collections.emptyList());
                }

                socket.setSSLParameters(sslParams);
            }
        };
    }

    protected SSLContext createSSLContext() {
        if (!cfg.isTrustAllSSLCertificates()) {
            return null;
        }
        LOG.info("SSL: Trusting all certificates.");

        // Use a trust strategy that always returns true
        SSLContext sslcontext;
        try {
            sslcontext = SSLContexts.custom().build();
            sslcontext.init(null, new TrustManager[] {
                    new TrustAllX509TrustManager()}, new SecureRandom());
        } catch (Exception e) {
            throw new CollectorException(
                    "Cannot create SSL context trusting all certificates.", e);
        }
        return sslcontext;
    }

    // Wraps redirection strategy to consider URLs as new documents to
    // queue for processing.
    private void initializeRedirectionStrategy() {
        try {
            Object chain = FieldUtils.readField(httpClient, "execChain", true);
            Object redir = FieldUtils.readField(
                    chain, "redirectStrategy", true);
            if (redir instanceof RedirectStrategy) {
                RedirectStrategy originalStrategy = (RedirectStrategy) redir;
                RedirectStrategyWrapper strategyWrapper =
                        new RedirectStrategyWrapper(originalStrategy,
                                cfg.getRedirectURLProvider());
                FieldUtils.writeField(
                        chain, "redirectStrategy", strategyWrapper, true);
            } else {
                LOG.warn("Could not wrap RedirectStrategy to properly handle"
                        + "redirects.");
            }
        } catch (Exception e) {
            LOG.warn("\"maxConnectionInactiveTime\" could not be set since "
                    + "internal connection manager does not support it.");
        }
    }

    @Override
    public void loadHttpFetcherFromXML(XML xml) {
        cfg.loadFromXML(xml);
    }

    @Override
    public void saveHttpFetcherToXML(XML xml) {
        cfg.saveToXML(xml);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
