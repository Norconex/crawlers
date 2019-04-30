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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;

import com.norconex.collector.http.fetch.util.GenericRedirectURLProvider;
import com.norconex.collector.http.fetch.util.IRedirectURLProvider;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 *
 * @author Pascal Essiembre
 * @since 3.0.0 (extracted from GenericHttpClientFactory and
 *        GenericDocumentFetcher from version 2.x)
 */
public class GenericHttpFetcherConfig implements IXMLConfigurable {

    public static final int DEFAULT_TIMEOUT = 30 * 1000;
    public static final int DEFAULT_MAX_REDIRECT = 50;
    public static final int DEFAULT_MAX_CONNECTIONS = 200;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 20;
    public static final int DEFAULT_MAX_IDLE_TIME = 10 * 1000;

    public static final List<Integer> DEFAULT_VALID_STATUS_CODES =
            CollectionUtil.unmodifiableList(HttpStatus.SC_OK);
    public static final List<Integer> DEFAULT_NOT_FOUND_STATUS_CODES =
            CollectionUtil.unmodifiableList(HttpStatus.SC_NOT_FOUND);

    private final List<Integer> validStatusCodes =
            new ArrayList<>(DEFAULT_VALID_STATUS_CODES);
    private final List<Integer> notFoundStatusCodes =
            new ArrayList<>(DEFAULT_NOT_FOUND_STATUS_CODES);
    private String headersPrefix;
    private boolean detectContentType;
    private boolean detectCharset;

    private String authMethod;
    private String authURL;
    private String authUsernameField;
    private String authUsername;
    private String authPasswordField;
    private String authPassword;
    private EncryptionKey authPasswordKey;
    private String authHostname;
    private int authPort = -1;
    private String authRealm;
    private Charset authFormCharset = StandardCharsets.UTF_8;
    private String authWorkstation;
    private String authDomain;
    private boolean authPreemptive;
    private boolean cookiesDisabled;
    private boolean trustAllSSLCertificates;
    private String proxyHost;
    private int proxyPort;
    private String proxyScheme;
    private String proxyUsername;
    private String proxyPassword;
    private EncryptionKey proxyPasswordKey;
    private String proxyRealm;
    private int connectionTimeout = DEFAULT_TIMEOUT;
    private int socketTimeout = DEFAULT_TIMEOUT;
    private int connectionRequestTimeout = DEFAULT_TIMEOUT;
    private Charset connectionCharset;
    private String localAddress;
    private boolean expectContinueEnabled;
    private int maxRedirects = DEFAULT_MAX_REDIRECT;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxConnectionsPerRoute = DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
    private int maxConnectionIdleTime = DEFAULT_MAX_IDLE_TIME;
    private int maxConnectionInactiveTime;
    private final List<String> sslProtocols = new ArrayList<>();
    private final Map<String, String> requestHeaders = new HashMap<>();
    private final Map<String, String> authFormParams = new HashMap<>();
    private String userAgent;
    private IRedirectURLProvider redirectURLProvider =
            new GenericRedirectURLProvider();


    /**
     * Gets the redirect URL provider.
     * @return the redirect URL provider
     * @since 2.4.0
     */
    public IRedirectURLProvider getRedirectURLProvider() {
        return redirectURLProvider;
    }
    /**
     * Sets the redirect URL provider
     * @param redirectURLProvider redirect URL provider
     * @since 2.4.0
     */
    public void setRedirectURLProvider(
            IRedirectURLProvider redirectURLProvider) {
        this.redirectURLProvider = redirectURLProvider;
    }

    public List<Integer> getValidStatusCodes() {
        return Collections.unmodifiableList(validStatusCodes);
    }
    /**
     * Gets valid HTTP response status codes.
     * @param validStatusCodes valid status codes
     * @since 3.0.0
     */
    public void setValidStatusCodes(List<Integer> validStatusCodes) {
        CollectionUtil.setAll(this.validStatusCodes, validStatusCodes);
    }
    /**
     * Gets valid HTTP response status codes.
     * @param validStatusCodes valid status codes
     */
    public void setValidStatusCodes(int... validStatusCodes) {
        CollectionUtil.setAll(this.validStatusCodes,
                ArrayUtils.toObject(validStatusCodes));
    }

    /**
     * Gets HTTP status codes to be considered as "Not found" state.
     * Default is 404.
     * @return "Not found" codes
     * @since 2.2.0
     */
    public List<Integer> getNotFoundStatusCodes() {
        return Collections.unmodifiableList(notFoundStatusCodes);
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 2.2.0
     */
    public final void setNotFoundStatusCodes(int... notFoundStatusCodes) {
        CollectionUtil.setAll(this.notFoundStatusCodes,
                ArrayUtils.toObject(notFoundStatusCodes));
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 3.0.0
     */
    public final void setNotFoundStatusCodes(
            List<Integer> notFoundStatusCodes) {
        CollectionUtil.setAll(this.notFoundStatusCodes, notFoundStatusCodes);
    }

    public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
    }

    /**
     * Gets whether content type is detected instead of relying on
     * HTTP response header.
     * @return <code>true</code> to enable detection
     * @since 2.7.0
     */
    public boolean isDetectContentType() {
        return detectContentType;
    }
    /**
     * Sets whether content type is detected instead of relying on
     * HTTP response header.
     * @param detectContentType <code>true</code> to enable detection
     * @since 2.7.0
     */
    public void setDetectContentType(boolean detectContentType) {
        this.detectContentType = detectContentType;
    }
    /**
     * Gets whether character encoding is detected instead of relying on
     * HTTP response header.
     * @return <code>true</code> to enable detection
     * @since 2.7.0
     */
    public boolean isDetectCharset() {
        return detectCharset;
    }
    /**
     * Sets whether character encoding is detected instead of relying on
     * HTTP response header.
     * @param detectCharset <code>true</code> to enable detection
     * @since 2.7.0
     */
    public void setDetectCharset(boolean detectCharset) {
        this.detectCharset = detectCharset;
    }


    public String getUserAgent() {
        return userAgent;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Sets a default HTTP request header every HTTP connection should have.
     * Those are in addition to any default request headers Apache HttpClient
     * may already provide.
     * @param name HTTP request header name
     * @param value HTTP request header value
     */
    public void setRequestHeader(String name, String value) {
        requestHeaders.put(name, value);
    }
    /**
     * Sets a default HTTP request headers every HTTP connection should have.
     * Those are in addition to any default request headers Apache HttpClient
     * may already provide.
     * @param headers map of header names and values
     */
    public void setRequestHeaders(Map<String, String> headers) {
        CollectionUtil.setAll(requestHeaders, headers);
    }
    /**
     * Gets the HTTP request header value matching the given name, previously
     * set with {@link #setRequestHeader(String, String)}.
     * @param name HTTP request header name
     * @return HTTP request header value or <code>null</code> if
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
     * @since 2.8.0
     */
    public List<String> getRequestHeaderNames() {
        return Collections.unmodifiableList(
                new ArrayList<>(requestHeaders.keySet()));
    }
    /**
     * Remove the request header matching the given name.
     * @param name name of HTTP request header to remove
     * @return the previous value associated with the name, or <code>null</code>
     *         if there was no request header for the name.
     * @since 2.8.0
     */
    public String removeRequestHeader(String name) {
        return requestHeaders.remove(name);
    }

    /**
     * Gets the authentication method.
     * @return authentication method
     */
    public String getAuthMethod() {
        return authMethod;
    }
    /**
     * Sets the authentication method.
     * <br><br>
     * Valid values are (case insensitive):
     * <ul>
     *   <li>form</li>
     *   <li>basic</li>
     *   <li>digest</li>
     *   <li>ntlm</li>
     * </ul>
     * Experimental (not fully tested, please report):
     * <ul>
     *   <li>spnego</li>
     *   <li>kerberos</li>
     * </ul>
     * @param authMethod authentication method
     */
    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    /**
     * Gets the name of the HTML field where the username is set.
     * This is used only for "form" authentication.
     * @return username name of the HTML field
     */
    public String getAuthUsernameField() {
        return authUsernameField;
    }
    /**
     * Sets the name of the HTML field where the username is set.
     * This is used only for "form" authentication.
     * @param authUsernameField name of the HTML field
     */
    public void setAuthUsernameField(String authUsernameField) {
        this.authUsernameField = authUsernameField;
    }

    /**
     * Gets the username.
     * Used for all authentication methods.
     * @return username
     */
    public String getAuthUsername() {
        return authUsername;
    }
    /**
     * Sets the username.
     * Used for all authentication methods.
     * @param authUsername username
     */
    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    /**
     * Gets the name of the HTML field where the password is set.
     * This is used only for "form" authentication.
     * @return name of the HTML field
     */
    public String getAuthPasswordField() {
        return authPasswordField;
    }
    /**
     * Sets the name of the HTML field where the password is set.
     * This is used only for "form" authentication.
     * @param authPasswordField name of the HTML field
     */
    public void setAuthPasswordField(String authPasswordField) {
        this.authPasswordField = authPasswordField;
    }

    /**
     * Gets the authentication password.
     * Used for all authentication methods.
     * @return the password
     */
    public String getAuthPassword() {
        return authPassword;
    }
    /**
     * Sets the authentication password.
     * Used for all authentication methods.
     * @param authPassword password
     */
    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    /**
     * Gets the authentication password encryption key.
     * @return the password key or <code>null</code> if the password is not
     * encrypted.
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public EncryptionKey getAuthPasswordKey() {
        return authPasswordKey;
    }
    /**
     * Sets the authentication password encryption key. Only required when
     * the password is encrypted.
     * @param authPasswordKey password key
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public void setAuthPasswordKey(EncryptionKey authPasswordKey) {
        this.authPasswordKey = authPasswordKey;
    }

    /**
     * Whether cookie support is disabled.
     * @return <code>true</code> if disabled
     */
    public boolean isCookiesDisabled() {
        return cookiesDisabled;
    }
    /**
     * Sets whether cookie support is disabled.
     * @param cookiesDisabled <code>true</code> if disabled
     */
    public void setCookiesDisabled(boolean cookiesDisabled) {
        this.cookiesDisabled = cookiesDisabled;
    }

    /**
     * Gets the URL for "form" authentication.
     * The username and password will be POSTed to this URL.
     * This is used only for "form" authentication.
     * @return "form" authentication URL
     */
    public String getAuthURL() {
        return authURL;
    }
    /**
     * Sets the URL for "form" authentication.
     * The username and password will be POSTed to this URL.
     * This is used only for "form" authentication.
     * @param authURL "form" authentication URL
     */
    public void setAuthURL(String authURL) {
        this.authURL = authURL;
    }

    /**
     * Gets the host name for the current authentication scope.
     * <code>null</code> means any host names for the scope.
     * Used for BASIC and DIGEST authentication.
     * @return hostname for the scope
     */
    public String getAuthHostname() {
        return authHostname;
    }
    /**
     * Sets the host name for the current authentication scope.
     * Setting this to null (default value) indicates "any hostname" for the
     * scope.
     * Used for BASIC and DIGEST authentication.
     * @param authHostname hostname for the scope
     */
    public void setAuthHostname(String authHostname) {
        this.authHostname = authHostname;
    }

    /**
     * Gets the port for the current authentication scope.
     * A negative number indicates "any port"
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @return port for the scope
     */
    public int getAuthPort() {
        return authPort;
    }
    /**
     * Sets the port for the current authentication scope.
     * Setting this to a negative number (default value) indicates "any port"
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @param authPort port for the scope
     */
    public void setAuthPort(int authPort) {
        this.authPort = authPort;
    }

    /**
     * Gets the realm name for the current authentication scope.
     * <code>null</code> indicates "any realm"
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @return realm name for the scope
     */
    public String getAuthRealm() {
        return authRealm;
    }
    /**
     * Sets the realm name for the current authentication scope.
     * Setting this to null (the default value) indicates "any realm"
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @param authRealm reaml name for the scope
     */
    public void setAuthRealm(String authRealm) {
        this.authRealm = authRealm;
    }

    /**
     * Gets the authentication form character set.
     * @return authentication form character set
     */
    public Charset getAuthFormCharset() {
        return authFormCharset;
    }
    /**
     * Sets the authentication form character set for the form field values.
     * Default is UTF-8.
     * @param authFormCharset authentication form character set
     */
    public void setAuthFormCharset(Charset authFormCharset) {
        this.authFormCharset = authFormCharset;
    }

    /**
     * Whether to trust all SSL certificates (affects only "https" connections).
     * @since 1.3.0
     * @return <code>true</code> if trusting all SSL certificates
     */
    public boolean isTrustAllSSLCertificates() {
        return trustAllSSLCertificates;
    }
    /**
     * Sets whether to trust all SSL certificate.  This is typically a bad
     * idea (favors man-in-the-middle attacks) . Try to install a SSL
     * certificate locally to ensure a proper certificate exchange instead.
     * @since 1.3.0
     * @param trustAllSSLCertificates <code>true</code> if trusting all SSL
     *            certificates
     */
    public void setTrustAllSSLCertificates(boolean trustAllSSLCertificates) {
        this.trustAllSSLCertificates = trustAllSSLCertificates;
    }

    /**
     * Gets the proxy host.
     * @return proxy host
     */
    public String getProxyHost() {
        return proxyHost;
    }
    /**
     * Sets the proxy host.
     * @param proxyHost proxy host
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * Gets the proxy port.
     * @return proxy port
     */
    public int getProxyPort() {
        return proxyPort;
    }
    /**
     * Sets the proxy port.
     * @param proxyPort proxy port
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Gets the proxy scheme.
     * @return proxy scheme
     */
    public String getProxyScheme() {
        return proxyScheme;
    }
    /**
     * Sets the proxy scheme.
     * @param proxyScheme proxy scheme
     */
    public void setProxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
    }

    /**
     * Gets the proxy username.
     * @return proxy username
     */
    public String getProxyUsername() {
        return proxyUsername;
    }
    /**
     * Sets the proxy username
     * @param proxyUsername proxy username
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * Gets the proxy password.
     * @return proxy password
     */
    public String getProxyPassword() {
        return proxyPassword;
    }
    /**
     * Sets the proxy password.
     * @param proxyPassword proxy password
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    /**
     * Gets the proxy password encryption key.
     * @return the password key or <code>null</code> if the password is not
     * encrypted.
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public EncryptionKey getProxyPasswordKey() {
        return proxyPasswordKey;
    }
    /**
     * Sets the proxy password encryption key. Only required when
     * the password is encrypted.
     * @param proxyPasswordKey password key
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public void setProxyPasswordKey(EncryptionKey proxyPasswordKey) {
        this.proxyPasswordKey = proxyPasswordKey;
    }

    /**
     * Gets the proxy realm.
     * @return proxy realm
     */
    public String getProxyRealm() {
        return proxyRealm;
    }
    /**
     * Sets the proxy realm
     * @param proxyRealm proxy realm
     */
    public void setProxyRealm(String proxyRealm) {
        this.proxyRealm = proxyRealm;
    }

    /**
     * Gets the connection timeout until a connection is established,
     * in milliseconds.
     * @return connection timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    /**
     * Sets the connection timeout until a connection is established,
     * in milliseconds. Default is {@link #DEFAULT_TIMEOUT}.
     * @param connectionTimeout connection timeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Gets the maximum period of inactivity between two consecutive data
     * packets, in milliseconds.
     * @return connection timeout
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }
    /**
     * Sets the maximum period of inactivity between two consecutive data
     * packets, in milliseconds. Default is {@link #DEFAULT_TIMEOUT}.
     * @param socketTimeout socket timeout
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * Gets the timeout when requesting a connection, in milliseconds
     * @return connection timeout
     */
    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }
    /**
     * Sets the timeout when requesting a connection, in milliseconds.
     * Default is {@link #DEFAULT_TIMEOUT}.
     * @param connectionRequestTimeout connection request timeout
     */
    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    /**
     * Gets the connection character set.
     * @return connection character set
     */
    public Charset getConnectionCharset() {
        return connectionCharset;
    }
    /**
     * Sets the connection character set.  The HTTP protocol specification
     * mandates the use of ASCII for HTTP message headers.  Sites do not always
     * respect this and it may be necessary to force a non-standard character
     * set.
     * @param connectionCharset connection character set
     */
    public void setConnectionCharset(Charset connectionCharset) {
        this.connectionCharset = connectionCharset;
    }

    /**
     * Whether 'Expect: 100-continue' handshake is enabled.
     * @return <code>true</code> if enabled
     */
    public boolean isExpectContinueEnabled() {
        return expectContinueEnabled;
    }
    /**
     * Sets whether 'Expect: 100-continue' handshake is enabled.
     * See {@link RequestConfig#isExpectContinueEnabled()}
     * @param expectContinueEnabled <code>true</code> if enabled
     */
    public void setExpectContinueEnabled(boolean expectContinueEnabled) {
        this.expectContinueEnabled = expectContinueEnabled;
    }

    /**
     * Gets the maximum number of redirects to be followed.
     * @return maximum number of redirects to be followed
     */
    public int getMaxRedirects() {
        return maxRedirects;
    }
    /**
     * Sets the maximum number of redirects to be followed.  This can help
     * prevent infinite loops.  A value of zero effectively disables
     * redirects.  Default is {@link #DEFAULT_MAX_REDIRECT}.
     * @param maxRedirects maximum number of redirects to be followed
     */
    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    /**
     * Gets the local address (IP or hostname).
     * @return local address
     */
    public String getLocalAddress() {
        return localAddress;
    }
    /**
     * Sets the local address, which may be useful when working with multiple
     * network interfaces.
     * @param localAddress locale address
     */
    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    /**
     * Gets the NTLM authentication workstation name.
     * @return workstation name
     */
    public String getAuthWorkstation() {
        return authWorkstation;
    }
    /**
     * Sets the NTLM authentication workstation name.
     * @param authWorkstation workstation name
     */
    public void setAuthWorkstation(String authWorkstation) {
        this.authWorkstation = authWorkstation;
    }

    /**
     * Gets the NTLM authentication domain.
     * @return authentication domain
     */
    public String getAuthDomain() {
        return authDomain;
    }
    /**
     * Sets the NTLM authentication domain
     * @param authDomain authentication domain
     */
    public void setAuthDomain(String authDomain) {
        this.authDomain = authDomain;
    }

    /**
     * Gets the maximum number of connections that can be created.
     * @return number of connections
     */
    public int getMaxConnections() {
        return maxConnections;
    }
    /**
     * Sets maximum number of connections that can be created.  Typically,
     * you would have at least the same amount as threads.
     * Default is {@link #DEFAULT_MAX_CONNECTIONS}.
     * @param maxConnections maximum number of connections
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Gets the maximum number of connections that can be used per route.
     * @return number of connections per route
     * @since 2.2.0
     */
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }
    /**
     * Sets the maximum number of connections that can be used per route.
     * Default is {@link #DEFAULT_MAX_CONNECTIONS_PER_ROUTE}.
     * @param maxConnectionsPerRoute maximum number of connections per route
     * @since 2.2.0
     */
    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    /**
     * Gets the period of time in milliseconds after which to evict idle
     * connections from the connection pool.
     * @return amount of time after which to evict idle connections
     * @since 2.2.0
     */
    public int getMaxConnectionIdleTime() {
        return maxConnectionIdleTime;
    }
    /**
     * Sets the period of time in milliseconds after which to evict idle
     * connections from the connection pool.
     * Default is {@link #DEFAULT_MAX_IDLE_TIME}.
     * @param maxConnectionIdleTime amount of time after which to evict idle
     *         connections
     * @since 2.2.0
     */
    public void setMaxConnectionIdleTime(int maxConnectionIdleTime) {
        this.maxConnectionIdleTime = maxConnectionIdleTime;
    }

    /**
     * Gets the period of time in milliseconds a connection must be inactive
     * to be checked in case it became stalled.
     * @return period of time in milliseconds
     * @since 2.2.0
     */
    public int getMaxConnectionInactiveTime() {
        return maxConnectionInactiveTime;
    }
    /**
     * Sets the period of time in milliseconds a connection must be inactive
     * to be checked in case it became stalled. Default is 0 (not proactively
     * checked).
     * @param maxConnectionInactiveTime period of time in milliseconds
     * @since 2.2.0
     */
    public void setMaxConnectionInactiveTime(int maxConnectionInactiveTime) {
        this.maxConnectionInactiveTime = maxConnectionInactiveTime;
    }

    /**
     * Gets the supported SSL/TLS protocols.  Default is <code>null</code>,
     * which means it will use those provided/configured by your Java
     * platform.
     * @return SSL/TLS protocols
     * @since 2.6.2
     */
    public List<String> getSSLProtocols() {
        return Collections.unmodifiableList(sslProtocols);
    }
    /**
     * Sets the supported SSL/TLS protocols, such as SSLv3, TLSv1, TLSv1.1,
     * and TLSv1.2.  Note that specifying a protocol not supported by
     * your underlying Java platform will not work.
     * @param sslProtocols SSL/TLS protocols supported
     * @since 2.6.2
     */
    public void setSSLProtocols(List<String> sslProtocols) {
        CollectionUtil.setAll(this.sslProtocols, sslProtocols);
    }

    /**
     * Sets an authentication form parameter (equivalent to "input" or other
     * fields in HTML forms).
     * @param name form parameter name
     * @param value form parameter value
     * @since 2.8.0
     */
    public void setAuthFormParam(String name, String value) {
        authFormParams.put(name, value);
    }
    /**
     * Sets authentication form parameters (equivalent to "input" or other
     * fields in HTML forms).
     * @param params map of form parameter names and values
     * @since 3.0.0
     */
    public void setAuthFormParams(Map<String, String> params) {
        CollectionUtil.setAll(authFormParams, params);
    }
    /**
     * Gets an authentication form parameter (equivalent to "input" or other
     * fields in HTML forms).
     * @param name form parameter name
     * @return form parameter value or <code>null</code> if
     *         no match is found
     * @since 2.8.0
     */
    public String getAuthFormParam(String name) {
        return authFormParams.get(name);
    }
    /**
     * Gets all authentication form parameter names. If no form parameters
     * are set, it returns an empty array.
     * @return HTTP request header names
     * @since 2.8.0
     */
    public List<String> getAuthFormParamNames() {
        return Collections.unmodifiableList(
                new ArrayList<>(authFormParams.keySet()));
    }
    /**
     * Remove the authentication form parameter matching the given name.
     * @param name name of form parameter to remove
     * @return the previous value associated with the name, or <code>null</code>
     *         if there was no form parameter for the name.
     * @since 2.8.0
     */
    public String removeAuthFormParameter(String name) {
        return authFormParams.remove(name);
    }

    /**
     * Gets whether to perform preemptive authentication
     * (valid for "basic" authentication method).
     * @return <code>true</code> to perform preemptive authentication
     * @since 2.8.0
     */
    public boolean isAuthPreemptive() {
        return authPreemptive;
    }
    /**
     * Sets whether to perform preemptive authentication
     * (valid for "basic" authentication method).
     * @param authPreemptive
     *            <code>true</code> to perform preemptive authentication
     * @since 2.8.0
     */
    public void setAuthPreemptive(boolean authPreemptive) {
        this.authPreemptive = authPreemptive;
    }

    @Override
    public void loadFromXML(XML xml) {
        setValidStatusCodes(xml.getDelimitedList(
                "validStatusCodes", Integer.class, validStatusCodes));
        setNotFoundStatusCodes(xml.getDelimitedList(
                "notFoundStatusCodes", Integer.class, notFoundStatusCodes));
        setHeadersPrefix(xml.getString("headersPrefix"));
        setDetectContentType(
                xml.getBoolean("detectContentType", detectContentType));
        setDetectCharset(xml.getBoolean("detectCharset", detectCharset));

        userAgent = xml.getString("userAgent", userAgent);
        cookiesDisabled = xml.getBoolean("cookiesDisabled", cookiesDisabled);
        authMethod = xml.getString("authMethod", authMethod);
        authUsernameField =
                xml.getString("authUsernameField", authUsernameField);
        authUsername = xml.getString("authUsername", authUsername);
        authPasswordField =
                xml.getString("authPasswordField", authPasswordField);
        authPassword = xml.getString("authPassword", authPassword);
        authPasswordKey =
                loadXMLPasswordKey(xml, "authPasswordKey", authPasswordKey);
        authURL = xml.getString("authURL", authURL);
        authHostname = xml.getString("authHostname", authHostname);
        authPort = xml.getInteger("authPort", authPort);
        authRealm = xml.getString("authRealm", authRealm);
        authFormCharset = xml.getCharset("authFormCharset", authFormCharset);
        authWorkstation = xml.getString("authWorkstation", authWorkstation);
        authDomain = xml.getString("authDomain", authDomain);
        authPreemptive = xml.getBoolean("authPreemptive", authPreemptive);
        proxyHost = xml.getString("proxyHost", proxyHost);
        proxyPort = xml.getInteger("proxyPort", proxyPort);
        proxyScheme = xml.getString("proxyScheme", proxyScheme);
        proxyUsername = xml.getString("proxyUsername", proxyUsername);
        proxyPassword = xml.getString("proxyPassword", proxyPassword);
        proxyPasswordKey =
                loadXMLPasswordKey(xml, "proxyPasswordKey", proxyPasswordKey);
        proxyRealm = xml.getString("proxyRealm", proxyRealm);
        connectionTimeout = xml.getDurationMillis(
                "connectionTimeout", (long) connectionTimeout).intValue();
        socketTimeout = xml.getDurationMillis(
                "socketTimeout", (long) socketTimeout).intValue();
        connectionRequestTimeout = xml.getDurationMillis(
                "connectionRequestTimeout",
                (long) connectionRequestTimeout).intValue();
        connectionCharset = xml.getCharset(
                "connectionCharset", connectionCharset);
        expectContinueEnabled = xml.getBoolean(
                "expectContinueEnabled", expectContinueEnabled);
        maxRedirects = xml.getInteger("maxRedirects", maxRedirects);
        maxConnections = xml.getInteger("maxConnections", maxConnections);
        trustAllSSLCertificates = xml.getBoolean(
                "trustAllSSLCertificates", trustAllSSLCertificates);
        localAddress = xml.getString("localAddress", localAddress);
        maxConnectionsPerRoute = xml.getInteger(
                "maxConnectionsPerRoute", maxConnectionsPerRoute);
        maxConnectionIdleTime = xml.getDurationMillis(
                "maxConnectionIdleTime",
                (long) maxConnectionIdleTime).intValue();
        maxConnectionInactiveTime = xml.getDurationMillis(
                "maxConnectionInactiveTime",
                (long) maxConnectionInactiveTime).intValue();
        setSSLProtocols(
                xml.getDelimitedStringList("sslProtocols", sslProtocols));
        setRequestHeaders(xml.getStringMap(
                "headers/header", "@name", ".", requestHeaders));
        setAuthFormParams(xml.getStringMap(
                "authFormParams/param", "@name", ".", authFormParams));
        setRedirectURLProvider(
                xml.getObject("redirectURLProvider", redirectURLProvider));

    }

    @Override
    public void saveToXML(XML xml) {
        xml.addElement("detectContentType", detectContentType);
        xml.addElement("detectCharset", detectCharset);
        xml.addDelimitedElementList("validStatusCodes", validStatusCodes);
        xml.addDelimitedElementList("notFoundStatusCodes", notFoundStatusCodes);
        xml.addElement("headersPrefix", headersPrefix);

        xml.addElement("userAgent", userAgent);
        xml.addElement("cookiesDisabled", cookiesDisabled);
        xml.addElement("authMethod", authMethod);
        xml.addElement("authUsername", authUsername);
        xml.addElement("authPassword", authPassword);
        saveXMLPasswordKey(xml, "authPasswordKey", authPasswordKey);
        xml.addElement("authUsernameField", authUsernameField);
        xml.addElement("authPasswordField", authPasswordField);
        xml.addElement("authURL", authURL);
        xml.addElement("authHostname", authHostname);
        xml.addElement("authPort", authPort);
        xml.addElement("authFormCharset", authFormCharset);
        xml.addElement("authWorkstation", authWorkstation);
        xml.addElement("authDomain", authDomain);
        xml.addElement("authRealm", authRealm);
        xml.addElement("authPreemptive", authPreemptive);
        xml.addElement("proxyHost", proxyHost);
        xml.addElement("proxyPort", proxyPort);
        xml.addElement("proxyScheme", proxyScheme);
        xml.addElement("proxyUsername", proxyUsername);
        xml.addElement("proxyPassword", proxyPassword);
        saveXMLPasswordKey(xml, "proxyPasswordKey", proxyPasswordKey);
        xml.addElement("proxyRealm", proxyRealm);
        xml.addElement("connectionTimeout", connectionTimeout);
        xml.addElement("socketTimeout", socketTimeout);
        xml.addElement("connectionRequestTimeout", connectionRequestTimeout);
        xml.addElement("connectionCharset", connectionCharset);
        xml.addElement("expectContinueEnabled", expectContinueEnabled);
        xml.addElement("maxRedirects", maxRedirects);
        xml.addElement("localAddress", localAddress);
        xml.addElement("maxConnections", maxConnections);
        xml.addElement("trustAllSSLCertificates", trustAllSSLCertificates);
        xml.addElement("maxConnectionsPerRoute", maxConnectionsPerRoute);
        xml.addElement("maxConnectionIdleTime", maxConnectionIdleTime);
        xml.addElement("maxConnectionInactiveTime", maxConnectionInactiveTime);
        xml.setDelimitedAttributeList("sslProtocols", sslProtocols);

        XML xmlHeaders = xml.addXML("headers");
        for (Entry<String, String> entry : requestHeaders.entrySet()) {
            xmlHeaders.addXML("header").setAttribute(
                    "name", entry.getKey()).setTextContent(entry.getValue());
        }

        XML xmlAuthFormParams = xml.addXML("authFormParams");
        for (Entry<String, String> entry : authFormParams.entrySet()) {
            xmlAuthFormParams.addXML("param").setAttribute(
                    "name", entry.getKey()).setTextContent(entry.getValue());
        }
        xml.addElement("redirectURLProvider", redirectURLProvider);
    }

    private EncryptionKey loadXMLPasswordKey(
            XML xml, String field, EncryptionKey defaultKey) {
        String xmlKey = xml.getString(field, null);
        String xmlSource = xml.getString(field + "Source", null);
        if (StringUtils.isBlank(xmlKey)) {
            return defaultKey;
        }
        EncryptionKey.Source source = null;
        if (StringUtils.isNotBlank(xmlSource)) {
            source = EncryptionKey.Source.valueOf(xmlSource.toUpperCase());
        }
        return new EncryptionKey(xmlKey, source);
    }
    private void saveXMLPasswordKey(XML xml, String field, EncryptionKey key) {
        if (key == null) {
            return;
        }
        xml.addElement(field, key.getValue());
        if (key.getSource() != null) {
            xml.addElement(
                    field + "Source", key.getSource().name().toLowerCase());
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof GenericHttpFetcherConfig)) {
            return false;
        }
        GenericHttpFetcherConfig other = (GenericHttpFetcherConfig) obj;
        return EqualsBuilder.reflectionEquals(
                this, other, "requestHeaders", "authFormParams")
                && EqualsUtil.equalsMap(requestHeaders, other.requestHeaders)
                && EqualsUtil.equalsMap(authFormParams, other.authFormParams);
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
