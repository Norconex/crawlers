/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.client.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.crawler.TargetURLRedirectStrategy;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * Default implementation of {@link IHttpClientFactory}.  
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;httpClientFactory class="com.norconex.collector.http.client.impl.GenericHttpClientFactory"&gt;
 *      &lt;cookiesDisabled&gt;[false|true]&lt;/cookiesDisabled&gt;
 *      &lt;connectionTimeout&gt;...&lt;/connectionTimeout&gt;
 *      &lt;socketTimeout&gt;...&lt;/socketTimeout&gt;
 *      &lt;connectionRequestTimeout&gt;...&lt;/connectionRequestTimeout&gt;
 *      &lt;connectionCharset&gt;...&lt;/connectionCharset&gt;
 *      &lt;expectContinueEnabled&gt;[false|true]&lt;/expectContinueEnabled&gt;
 *      &lt;maxRedirects&gt;...&lt;/maxRedirects&gt;
 *      &lt;localAddress&gt;...&lt;/localAddress&gt;
 *      &lt;staleConnectionCheckDisabled&gt;[false|true]&lt;/staleConnectionCheckDisabled&gt;
 *      &lt;maxConnections&gt;...&lt;/maxConnections&gt;
 *
 *      &lt;-- Be warned: trusting all certificates is usually a bad idea. --&gt;
 *      &lt;trustAllSSLCertificates&gt;[false|true]&lt;/trustAllSSLCertificates&gt;
 *
 *      &lt;proxyHost&gt;...&lt;/proxyHost&gt;
 *      &lt;proxyPort&gt;...&lt;/proxyPort&gt;
 *      &lt;proxyUsername&gt;...&lt;/proxyUsername&gt;
 *      &lt;proxyPassword&gt;...&lt;/proxyPassword&gt;
 *      &lt;proxyRealm&gt;...&lt;/proxyRealm&gt;
 *      &lt;proxyScheme&gt;...&lt;/proxyScheme&gt;
 *      
 *      &lt;authMethod&gt;[form|basic|digest|ntlm|spnego|kerberos]&lt;/authMethod&gt;
 *      
 *      &lt;!-- These apply to any authentication mechanism --&gt;
 *      &lt;authUsername&gt;...&lt;/authUsername&gt;
 *      &lt;authPassword&gt;...&lt;/authPassword&gt;
 *      
 *      &lt;!-- These apply to FORM authentication --&gt;
 *      &lt;authUsernameField&gt;...&lt;/authUsernameField&gt;
 *      &lt;authPasswordField&gt;...&lt;/authPasswordField&gt;
 *      &lt;authURL&gt;...&lt;/authURL&gt;
 *      &lt;authFormCharset&gt;...&lt;/authFormCharset&gt;
 *      
 *      &lt;!-- These apply to both BASIC and DIGEST authentication --&gt;
 *      &lt;authHostname&gt;...&lt;/authHostname&gt;
 *      &lt;authPort&gt;...&lt;/authPort&gt;
 *      &lt;authRealm&gt;...&lt;/authRealm&gt;
 *      
 *      &lt;!-- These apply to NTLM authentication --&gt;
 *      &lt;authHostname&gt;...&lt;/authHostname&gt;
 *      &lt;authPort&gt;...&lt;/authPort&gt;
 *      &lt;authWorkstation&gt;...&lt;/authWorkstation&gt;
 *      &lt;authDomain&gt;...&lt;/authDomain&gt;
 *
 *  &lt;/httpClientFactory&gt;
 * </pre>
 * @author Pascal Essiembre
 * @since 1.3.0
 */
public class GenericHttpClientFactory 
        implements IHttpClientFactory, IXMLConfigurable {

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

    public static final int DEFAULT_TIMEOUT = 30 * 1000;
    public static final int DEFAULT_MAX_REDIRECT = 50;
    public static final int DEFAULT_MAX_CONNECTIONS = 5;

    private static final int FTP_PORT = 80;
    
    private static final SchemePortResolver SCHEME_PORT_RESOLVER =
        new SchemePortResolver() {
            @Override
            public int resolve(HttpHost host) 
                    throws UnsupportedSchemeException {
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
            }
        };
    
    //--- Configurable arguments ---
    private String authMethod;
    private String authURL;
    private String authUsernameField;
    private String authUsername;
    private String authPasswordField;
    private String authPassword;
    private String authHostname;
    private int authPort = -1;
    private String authRealm;
    private String authFormCharset = CharEncoding.UTF_8;
    private String authWorkstation;
    private String authDomain;
    private boolean cookiesDisabled;
    private boolean trustAllSSLCertificates;
    private String proxyHost;
    private int proxyPort;
    private String proxyScheme;
    private String proxyUsername;
    private String proxyPassword;
    private String proxyRealm;
    private int connectionTimeout = DEFAULT_TIMEOUT;
    private int socketTimeout = DEFAULT_TIMEOUT;
    private int connectionRequestTimeout = DEFAULT_TIMEOUT;
    private String connectionCharset;
    private boolean expectContinueEnabled;
    private int maxRedirects = DEFAULT_MAX_REDIRECT;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private String localAddress;
    private boolean staleConnectionCheckDisabled;
    
    @Override
    public HttpClient createHTTPClient(String userAgent) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setSslcontext(createSSLContext());
        builder.setSchemePortResolver(createSchemePortResolver());
        builder.setDefaultRequestConfig(createRequestConfig());
        builder.setProxy(createProxy());
        builder.setDefaultCredentialsProvider(createCredentialsProvider());
        builder.setDefaultConnectionConfig(createConnectionConfig());
        builder.setUserAgent(userAgent);
        builder.setMaxConnTotal(maxConnections);
        //builder.setMaxConnPerRoute(maxConnPerRoute)
        buildCustomHttpClient(builder);
        
        //TODO Put in place a more permanent solution to the following
        //Fix GitHub #17 start
        RedirectStrategy strategy = createRedirectStrategy();
        if (strategy == null) {
            strategy = LaxRedirectStrategy.INSTANCE;
        }
        builder.setRedirectStrategy(new TargetURLRedirectStrategy(strategy));
        //Fix end

        HttpClient httpClient = builder.build();
        if (AUTH_METHOD_FORM.equalsIgnoreCase(authMethod)) {
            authenticateUsingForm(httpClient);
        }
        return httpClient;
    }

    /**
     * For implementors to subclass.  Does nothing by default.
     * @param builder http client builder
     */
    protected void buildCustomHttpClient(HttpClientBuilder builder) {
        //do nothing by default
    }
    
    protected void authenticateUsingForm(HttpClient httpClient) {
        HttpPost post = new HttpPost(getAuthURL());

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair(
                getAuthUsernameField(), getAuthUsername()));
        formparams.add(new BasicNameValuePair(
                getAuthPasswordField(), getAuthPassword()));
        try {
            UrlEncodedFormEntity entity = 
                    new UrlEncodedFormEntity(formparams, authFormCharset);
            post.setEntity(entity);
            httpClient.execute(post);
        } catch (Exception e) {
            throw new CollectorException(e);
        }
        post.releaseConnection();
    }

    protected RedirectStrategy createRedirectStrategy() {
        return LaxRedirectStrategy.INSTANCE;
    }
    
    protected SchemePortResolver createSchemePortResolver() {
        return SCHEME_PORT_RESOLVER;
    }
    protected RequestConfig createRequestConfig() {
        RequestConfig.Builder builder = RequestConfig.custom()
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setMaxRedirects(maxRedirects)
                .setStaleConnectionCheckEnabled(!staleConnectionCheckDisabled)
                .setExpectContinueEnabled(expectContinueEnabled);
        if (cookiesDisabled) {
            builder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
        } else {
            builder.setCookieSpec(CookieSpecs.BEST_MATCH);
        }
        if (maxRedirects <= 0) {
            builder.setRedirectsEnabled(false);
        }
        if (StringUtils.isNotBlank(localAddress)) {
            try {
                builder.setLocalAddress(InetAddress.getByName(localAddress));
            } catch (UnknownHostException e) {
                throw new CollectorException(
                        "Invalid local address: " + localAddress, e);
            }
        }
        return builder.build();
    }
    protected HttpHost createProxy() {
        if (StringUtils.isNotBlank(proxyHost)) {
            return new HttpHost(proxyHost, proxyPort, proxyScheme);
        }
        return null;
    }
    protected CredentialsProvider createCredentialsProvider() {
        CredentialsProvider credsProvider = null;
        //--- Proxy ---
        if (StringUtils.isNotBlank(proxyUsername)) {
            credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(proxyHost, proxyPort),
                    new UsernamePasswordCredentials(
                            proxyUsername, proxyPassword));
        }
        //--- Auth ---
        if (StringUtils.isNotBlank(authUsername)
                && !AUTH_METHOD_FORM.equalsIgnoreCase(authMethod)) {
            if (credsProvider == null) {
                credsProvider = new BasicCredentialsProvider();
            }
            Credentials creds = null;
            if (AUTH_METHOD_NTLM.equalsIgnoreCase(authMethod)) {
                creds = new NTCredentials(authUsername, authPassword, 
                        authWorkstation, authDomain);
            } else {
                creds = new UsernamePasswordCredentials(
                        authUsername, authPassword);
            }
            credsProvider.setCredentials(new AuthScope(
                    authHostname, authPort, authRealm, authMethod), creds);
        }
        return credsProvider;
    }
    protected ConnectionConfig createConnectionConfig() {
        if (StringUtils.isNotBlank(proxyUsername)) {
            return ConnectionConfig.custom()
                    .setCharset(Consts.UTF_8)
                    .build(); 
        }
        return null;
    }
    protected SSLContext createSSLContext() {
        if (!trustAllSSLCertificates) {
            return null;
        }
        SSLContext sslcontext;
        try {
            sslcontext = SSLContexts.custom().loadTrustMaterial(
                null, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(final X509Certificate[] chain,
                            final String authType) throws CertificateException {
                        return true;
                    }
                }).build();
        } catch (Exception e) {
            throw new CollectorException(
                    "Cannot create SSL context trusting all certificates.", e);
        }
        return sslcontext;
    }
    
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        cookiesDisabled = xml.getBoolean("cookiesDisabled", cookiesDisabled);
        authMethod = xml.getString("authMethod", authMethod);
        authUsernameField = 
                xml.getString("authUsernameField", authUsernameField);
        authUsername = xml.getString("authUsername", authUsername);
        authPasswordField = 
                xml.getString("authPasswordField", authPasswordField);
        authPassword = xml.getString("authPassword", authPassword);
        authURL = xml.getString("authURL", authURL);
        authHostname = xml.getString("authHostname", authHostname);
        authPort = xml.getInt("authPort", authPort);
        authRealm = xml.getString("authRealm", authRealm);
        authFormCharset = xml.getString("authFormCharset", authFormCharset);
        authWorkstation = xml.getString("authWorkstation", authWorkstation);
        authDomain = xml.getString("authDomain", authDomain);
        proxyHost = xml.getString("proxyHost", proxyHost);
        proxyPort = xml.getInt("proxyPort", proxyPort);
        proxyScheme = xml.getString("proxyScheme", proxyScheme);
        proxyUsername = xml.getString("proxyUsername", proxyUsername);
        proxyPassword = xml.getString("proxyPassword", proxyPassword);
        proxyRealm = xml.getString("proxyRealm", proxyRealm);
        connectionTimeout = xml.getInt("connectionTimeout", connectionTimeout);
        socketTimeout = xml.getInt("socketTimeout", socketTimeout);
        connectionRequestTimeout = xml.getInt(
                "connectionRequestTimeout", connectionRequestTimeout);
        connectionCharset = xml.getString(
                "connectionCharset", connectionCharset);
        expectContinueEnabled = xml.getBoolean(
                "expectContinueEnabled", expectContinueEnabled);
        maxRedirects = xml.getInt("maxRedirects", maxRedirects);
        maxConnections = xml.getInt("maxConnections", maxConnections);
        localAddress = xml.getString("localAddress", localAddress);
        staleConnectionCheckDisabled = xml.getBoolean(
                "staleConnectionCheckDisabled", staleConnectionCheckDisabled);
        
        
        
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("httpClientFactory");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writeBoolElement(writer, "cookiesDisabled", cookiesDisabled);
            writeStringElement(writer, "authMethod", authMethod);
            writeStringElement(writer, "authUsername", authUsername);
            writeStringElement(writer, "authPassword", authPassword);
            writeStringElement(writer, "authUsernameField", authUsernameField);
            writeStringElement(writer, "authPasswordField", authPasswordField);
            writeStringElement(writer, "authURL", authURL);
            writeStringElement(writer, "authHostname", authHostname);
            writeIntElement(writer, "authPort", authPort);
            writeStringElement(writer, "authFormCharset", authFormCharset);
            writeStringElement(writer, "authWorkstation", authWorkstation);
            writeStringElement(writer, "authDomain", authDomain);
            writeStringElement(writer, "authRealm", authRealm);
            writeStringElement(writer, "proxyHost", proxyHost);
            writeIntElement(writer, "proxyPort", proxyPort);
            writeStringElement(writer, "proxyScheme", proxyScheme);
            writeStringElement(writer, "proxyUsername", proxyUsername);
            writeStringElement(writer, "proxyPassword", proxyPassword);
            writeStringElement(writer, "proxyRealm", proxyRealm);
            writer.writeEndElement();
            writeIntElement(writer, "connectionTimeout", connectionTimeout);
            writeIntElement(writer, "socketTimeout", socketTimeout);
            writeIntElement(writer, "connectionRequestTimeout",
                    connectionRequestTimeout);
            writeStringElement(writer, "connectionCharset", connectionCharset);
            writeBoolElement(
                    writer, "expectContinueEnabled", expectContinueEnabled);
            writeIntElement(writer, "maxRedirects", maxRedirects);
            writeStringElement(writer, "localAddress", localAddress);
            writeBoolElement(writer, "staleConnectionCheckDisabled", 
                    staleConnectionCheckDisabled);
            writeIntElement(writer, "maxConnections", maxConnections);
            
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }
    
    private void writeStringElement(
            XMLStreamWriter writer, String name, String value)
                    throws XMLStreamException {
        writer.writeStartElement(name);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }
    private void writeIntElement(
            XMLStreamWriter writer, String name, int value)
                    throws XMLStreamException {
        writeStringElement(writer, name, Integer.toString(value));
    }
    private void writeBoolElement(
            XMLStreamWriter writer, String name, boolean value)
                    throws XMLStreamException {
        writeStringElement(writer, name, Boolean.toString(value));
    }

    //--- Getters/Setters ------------------------------------------------------
    
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
     * Gets the password.
     * Used for all authentication methods.
     * @return the password
     */
    public String getAuthPassword() {
        return authPassword;
    }
    /**
     * Sets the password.
     * Used for all authentication methods.
     * @param authPassword password
     */
    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
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
    public String getAuthFormCharset() {
        return authFormCharset;
    }
    /**
     * Sets the authentication form character set for the form field values.
     * Default is UTF-8.
     * @param authFormCharset authentication form character set 
     */
    public void setAuthFormCharset(String authFormCharset) {
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
     * in milliseconds. Zero means infinite and -1 means system default.
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
     * packets, in milliseconds. Zero means infinite and -1 means 
     * system default.
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
     * Zero means infinite and -1 means system default.
     * @param connectionRequestTimeout connection request timeout
     */
    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    /**
     * Gets the connection character set.
     * @return connection character set
     */
    public String getConnectionCharset() {
        return connectionCharset;
    }
    /**
     * Sets the connection character set.  The HTTP protocol specification
     * mandates the use of ASCII for HTTP message headers.  Sites do not always
     * respect this and it may be necessary to force a non-standard character 
     * set.
     * @param connectionCharset connection character set
     */
    public void setConnectionCharset(String connectionCharset) {
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
     * redirects.  Default is 50.
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
     * Gets whether stale connection check is disabled.
     * @return <code>true</code> if stale connection check is disabled
     */
    public boolean isStaleConnectionCheckDisabled() {
        return staleConnectionCheckDisabled;
    }
    /**
     * Sets whether stale connection check is disabled.  Disabling stale
     * connection check can slightly improve performance.
     * @param staleConnectionCheckDisabled <code>true</code> if stale 
     *        connection check is disabled
     */
    public void setStaleConnectionCheckDisabled(boolean staleConnectionCheckDisabled) {
        this.staleConnectionCheckDisabled = staleConnectionCheckDisabled;
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
     * @param maxConnections maximum number of connections
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    
    
}
