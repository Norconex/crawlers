/* Copyright 2018 Norconex Inc.
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
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
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
import com.norconex.collector.core.crawler.CrawlerLifeCycleListener;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpFetcher;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.util.CharsetUtil;

/**
 * <p>
 * Default implementation of {@link IHttpFetcher}.
 * </p>
 * <h3>Password encryption in XML configuration:</h3>
 * <p>
 * As of 2.4.0, <code>proxyPassword</code> and <code>authPassword</code>
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
 * As of 2.7.0, XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;httpClientFactory class="com.norconex.collector.http.client.impl.GenericHttpClientFactory"&gt;
 *      &lt;userAgent&gt;(Identify yourself!)&lt;/userAgent&gt;
 *      &lt;cookiesDisabled&gt;[false|true]&lt;/cookiesDisabled&gt;
 *      &lt;connectionTimeout&gt;(milliseconds)&lt;/connectionTimeout&gt;
 *      &lt;socketTimeout&gt;(milliseconds)&lt;/socketTimeout&gt;
 *      &lt;connectionRequestTimeout&gt;(milliseconds)&lt;/connectionRequestTimeout&gt;
 *      &lt;connectionCharset&gt;...&lt;/connectionCharset&gt;
 *      &lt;expectContinueEnabled&gt;[false|true]&lt;/expectContinueEnabled&gt;
 *      &lt;maxRedirects&gt;...&lt;/maxRedirects&gt;
 *      &lt;redirectURLProvider&gt;(implementation handling redirects)&lt;/redirectURLProvider&gt;
 *      &lt;localAddress&gt;...&lt;/localAddress&gt;
 *      &lt;maxConnections&gt;...&lt;/maxConnections&gt;
 *      &lt;maxConnectionsPerRoute&gt;...&lt;/maxConnectionsPerRoute&gt;
 *      &lt;maxConnectionIdleTime&gt;(milliseconds)&lt;/maxConnectionIdleTime&gt;
 *      &lt;maxConnectionInactiveTime&gt;(milliseconds)&lt;/maxConnectionInactiveTime&gt;
 *
 *      &lt;!-- Be warned: trusting all certificates is usually a bad idea. --&gt;
 *      &lt;trustAllSSLCertificates&gt;[false|true]&lt;/trustAllSSLCertificates&gt;
 *
 *      &lt;!-- Since 2.6.2, you can specify SSL/TLS protocols to use --&gt;
 *      &lt;sslProtocols&gt;(coma-separated list)&lt;/sslProtocols&gt;
 *
 *      &lt;proxyHost&gt;...&lt;/proxyHost&gt;
 *      &lt;proxyPort&gt;...&lt;/proxyPort&gt;
 *      &lt;proxyRealm&gt;...&lt;/proxyRealm&gt;
 *      &lt;proxyScheme&gt;...&lt;/proxyScheme&gt;
 *      &lt;proxyUsername&gt;...&lt;/proxyUsername&gt;
 *      &lt;proxyPassword&gt;...&lt;/proxyPassword&gt;
 *      &lt;!-- Use the following if password is encrypted. --&gt;
 *      &lt;proxyPasswordKey&gt;(the encryption key or a reference to it)&lt;/proxyPasswordKey&gt;
 *      &lt;proxyPasswordKeySource&gt;[key|file|environment|property]&lt;/proxyPasswordKeySource&gt;
 *
 *      &lt;!-- HTTP request headers passed on every HTTP requests --&gt;
 *      &lt;headers&gt;
 *          &lt;header name="(header name)"&gt;(header value)&lt;/header&gt;
 *          &lt;!-- You can repeat this header tag as needed. --&gt;
 *      &lt;/headers&gt;
 *
 *      &lt;authMethod&gt;[form|basic|digest|ntlm|spnego|kerberos]&lt;/authMethod&gt;
 *
 *      &lt;!-- These apply to any authentication mechanism --&gt;
 *      &lt;authUsername&gt;...&lt;/authUsername&gt;
 *      &lt;authPassword&gt;...&lt;/authPassword&gt;
 *      &lt;!-- Use the following if password is encrypted. --&gt;
 *      &lt;authPasswordKey&gt;(the encryption key or a reference to it)&lt;/authPasswordKey&gt;
 *      &lt;authPasswordKeySource&gt;[key|file|environment|property]&lt;/authPasswordKeySource&gt;
 *
 *      &lt;!-- These apply to FORM authentication --&gt;
 *      &lt;authUsernameField&gt;...&lt;/authUsernameField&gt;
 *      &lt;authPasswordField&gt;...&lt;/authPasswordField&gt;
 *      &lt;authURL&gt;...&lt;/authURL&gt;
 *      &lt;authFormCharset&gt;...&lt;/authFormCharset&gt;
 *      &lt;!-- Extra form parameters required to authenticate (since 2.8.0) --&gt;
 *      &lt;authFormParams&gt;
 *          &lt;param name="(param name)"&gt;(param value)&lt;/param&gt;
 *          &lt;!-- You can repeat this param tag as needed. --&gt;
 *      &lt;/authFormParams&gt;
 *
 *      &lt;!-- These apply to both BASIC and DIGEST authentication --&gt;
 *      &lt;authHostname&gt;...&lt;/authHostname&gt;
 *      &lt;authPort&gt;...&lt;/authPort&gt;
 *      &lt;authRealm&gt;...&lt;/authRealm&gt;
 *
 *      &lt;!-- This applies to BASIC authentication --&gt;
 *      &lt;authPreemptive&gt;[false|true]&lt;/authPreemptive&gt;
 *
 *      &lt;!-- These apply to NTLM authentication --&gt;
 *      &lt;authHostname&gt;...&lt;/authHostname&gt;
 *      &lt;authPort&gt;...&lt;/authPort&gt;
 *      &lt;authWorkstation&gt;...&lt;/authWorkstation&gt;
 *      &lt;authDomain&gt;...&lt;/authDomain&gt;
 *
 *  &lt;/httpClientFactory&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following will authenticate the crawler to a web site before crawling.
 * The website uses an HTML form with a username and password fields called
 * "loginUser" and "loginPwd".
 * </p>
 * <pre>
 *  &lt;httpClientFactory class="com.norconex.collector.http.client.impl.GenericHttpClientFactory"&gt;
 *      &lt;authUsername&gt;joeUser&lt;/authUsername&gt;
 *      &lt;authPassword&gt;joePasword&lt;/authPassword&gt;
 *      &lt;authUsernameField&gt;loginUser&lt;/authUsernameField&gt;
 *      &lt;authPasswordField&gt;loginPwd&lt;/authPasswordField&gt;
 *      &lt;authURL&gt;http://www.example.com/login&lt;/authURL&gt;
 *  &lt;/httpClientFactory&gt;
 * </pre>
 * @author Pascal Essiembre
 * @since 3.0.0 (Merged from GenericDocumentFetcher and
 *        GenericHttpClientFactory.)
 */
public class GenericHttpFetcher
        extends CrawlerLifeCycleListener
        implements IHttpFetcher, IXMLConfigurable {

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
    private final transient ContentTypeDetector contentTypeDetector =
            new ContentTypeDetector();

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



//    @Override
//    protected void collectorStartup(CollectorEvent<Collector> event) {
//        this.httpClient = createHttpClient();
//        initializeRedirectionStrategy();
//
//        String userAgent = cfg.getUserAgent();
//        if (StringUtils.isBlank(userAgent)) {
//            LOG.info("User-Agent: <None specified>");
//            LOG.debug("It is recommended you identify yourself to web sites "
//                    + "by specifying a user agent "
//                    + "(https://en.wikipedia.org/wiki/User_agent)");
//        } else {
//            LOG.info("User-Agent: {}", userAgent);
//        }
//    }
//    @Override
//    protected void collectorShutdown(CollectorEvent<Collector> event) {
//        if (httpClient instanceof CloseableHttpClient) {
//            try {
//                ((CloseableHttpClient) httpClient).close();
//            } catch (IOException e) {
//                LOG.error("Cannot close HttpClient.", e);
//            }
//        }
//    }

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

//  @Override
//  public HttpFetchResponse fetchDocument(
//          String url, HttpMetadata httpHeaders, OutputStream content) {

    // have a http response status that means "unsupported" and move directly to next in chain or fallback.

    @Override
    public HttpFetchResponse fetchHeaders(String url, HttpMetadata headers) {
        return fetch(url, headers, null, true);
    }

    @Override
    public HttpFetchResponse fetchDocument(HttpDocument doc) {
        MutableObject<CachedInputStream> is =
                new MutableObject<>(doc.getInputStream());
        HttpFetchResponse response = fetch(
                doc.getReference(), doc.getMetadata(), is, false);

//        IOUtils.copy(is.getValue(), new NullOutputStream());

        doc.setInputStream(is.getValue());
        performDetection(doc);
        return response;
    }

    private HttpFetchResponse fetch(String url, HttpMetadata metadata,
            MutableObject<CachedInputStream> stream, boolean head) {

        //TODO replace signature with Writer class.
        LOG.debug("Fetching document: {}", url);
        HttpRequestBase method = createUriRequest(url, head);
        try {

            // Execute the method.
            HttpResponse response = httpClient.execute(method);

            int statusCode = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();

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
//                    doc.setInputStream(is);
//                    IOUtils.copy(is, doc.getOutputStream());
//                    IOUtils.copy(is, content);
//                    doc.setContent(doc.getContent().newInputStream(is));
    //
                    //read a copy to force caching and then close the HTTP stream
                    IOUtils.copy(content, new NullOutputStream());
                    stream.setValue(content);

//                    performDetection(doc);
                }
                return new HttpFetchResponse(
                        HttpCrawlState.NEW, statusCode, reason);
            }

            if (!head) {
                // INVALID http response
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Rejected response content: "
                            + IOUtils.toString(is, StandardCharsets.UTF_8));
                    IOUtils.closeQuietly(is);
                } else {
                    // read response anyway to be safer, but ignore content
                    BufferedInputStream bis = new BufferedInputStream(is);
                    int result = bis.read();
                    while(result != -1) {
                      result = bis.read();
                    }
                    IOUtils.closeQuietly(bis);
                }
            }

            if (cfg.getNotFoundStatusCodes().contains(statusCode)) {
                return new HttpFetchResponse(
                        HttpCrawlState.NOT_FOUND, statusCode, reason);
            }
            LOG.debug("Unsupported HTTP Response: "
                    + response.getStatusLine());
            return new HttpFetchResponse(
                    CrawlState.BAD_STATUS, statusCode, reason);
        } catch (Exception e) {
            LOG.info("Cannot fetch document: {}  ({})",
                    url, e.getMessage(), e);

//            if (LOG.isDebugEnabled()) {
//                LOG.info("Cannot fetch document: " + doc.getReference()
//                        + " (" + e.getMessage() + ")", e);
//            } else {
//                LOG.info("Cannot fetch document: " + doc.getReference()
//                        + " (" + e.getMessage() + ")");
//            }
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
                ContentType ct = contentTypeDetector.detect(
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
            LOG.warn("Cannont perform charset type detection.", e);
        }
    }

    /**
     * Creates the HTTP request to be executed.  Default implementation
     * returns an {@link HttpGet} request around the document reference.
     * This method can be overwritten to return another type of request,
     * add HTTP headers, etc.
     * @param url URL to fetch
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
                .setExpectContinueEnabled(cfg.isExpectContinueEnabled());
        if (cfg.isCookiesDisabled()) {
            builder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
        } else {
            builder.setCookieSpec(CookieSpecs.DEFAULT);
        }
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
                socket.setSSLParameters(sslParams);
            }
        };
    }

    protected SSLContext createSSLContext() {
        if (!cfg.isTrustAllSSLCertificates()) {
            return null;
        }
        LOG.info("SSL: Trusting all certificates.");

        //TODO consider moving some of the below settings at the collector
        //level since they affect the whole JVM.

        // Disabling SNI extension introduced in Java 7 is necessary
        // to avoid SSLProtocolException: handshake alert:  unrecognized_name
        // Described here:
        // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=7127374
        LOG.debug("SSL: Disabling SNI Extension using system property.");
        System.setProperty("jsse.enableSNIExtension", "false");

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
    public void loadFromXML(XML xml) {
        cfg.loadFromXML(xml);
    }

    @Override
    public void saveToXML(XML xml) {
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
