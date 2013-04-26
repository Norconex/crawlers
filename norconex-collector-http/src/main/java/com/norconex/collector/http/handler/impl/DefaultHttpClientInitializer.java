/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.handler.impl;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.handler.IHttpClientInitializer;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * Default implementation of {@link IHttpClientInitializer}.  
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;httpClientInitializer class="com.norconex.collector.http.handler.impl.DefaultHttpClientInitializer"&gt;
 *      &lt;cookiesDisabled&gt;[false|true]&lt;/cookiesDisabled&gt;
 *      &lt;userAgent&gt;...&lt;/userAgent&gt;
 *      &lt;authMethod&gt;[form|basic|digest]&lt;/authMethod&gt;
 *      &lt;authUsername&gt;...&lt;/authUsername&gt;
 *      &lt;authPassword&gt;...&lt;/authPassword&gt;
 *      &lt;authUsernameField&gt;...&lt;/authUsernameField&gt;
 *      &lt;authPasswordField&gt;...&lt;/authPasswordField&gt;
 *      &lt;authURL&gt;...&lt;/authURL&gt;
 *      &lt;proxyHost&gt;...&lt;/proxyHost&gt;
 *      &lt;proxyPort&gt;...&lt;/proxyPort&gt;
 *      &lt;proxyUsername&gt;...&lt;/proxyUsername&gt;
 *      &lt;proxyPassword&gt;...&lt;/proxyPassword&gt;
 *      &lt;proxyRealm&gt;...&lt;/proxyRealm&gt;
 *  &lt;/httpClientInitializer&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class DefaultHttpClientInitializer implements
		IHttpClientInitializer, IXMLConfigurable {

	private static final long serialVersionUID = 8489434479618081974L;
    private static final Logger LOG = LogManager.getLogger(
            DefaultHttpClientInitializer.class);

    //TODO use enum?
	public static final String AUTH_METHOD_FORM = "form";
    public static final String AUTH_METHOD_BASIC = "basic";
    public static final String AUTH_METHOD_DIGEST = "digest";
	
	private String authMethod;
    private String authURL;
    private String authUsernameField;
	private String authUsername;
    private String authPasswordField;
    private String authPassword;
    private boolean cookiesDisabled;
    private String userAgent;
    private String proxyHost;
    private int proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private String proxyRealm;
	
    @Override
	public void initializeHTTPClient(DefaultHttpClient httpClient) {
        
        //TODO make charset configurable instead since UTF-8 is not right
        // charset for URL specifications.  It is used here to overcome
        // so invalid redirect errors, where the redirect target URL is not 
        // URL-Encoded and has non-ascii values, and fails
        // (e.g. like ja.wikipedia.org).
        // Can consider a custom RedirectStrategy too if need be.
        httpClient.getParams().setParameter(
                CoreProtocolPNames.HTTP_ELEMENT_CHARSET, "UTF-8");
        
        if (StringUtils.isNotBlank(proxyHost)) {
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, 
                    new HttpHost(proxyHost, proxyPort));
            if (StringUtils.isNotBlank(proxyUsername)) {
                httpClient.getCredentialsProvider().setCredentials(
                        new AuthScope(proxyHost, proxyPort),
                        new UsernamePasswordCredentials(
                                proxyUsername, proxyPassword));
            }
        }
        
        if (!cookiesDisabled) {
            httpClient.getParams().setParameter(
                    ClientPNames.COOKIE_POLICY,
                    CookiePolicy.BROWSER_COMPATIBILITY);
        }
        if (AUTH_METHOD_FORM.equalsIgnoreCase(authMethod)) {
            authenticateUsingForm(httpClient);
        } else if (AUTH_METHOD_BASIC.equalsIgnoreCase(authMethod)) {
            LOG.error("BASIC authentication method not yet supported.");
        } else if (AUTH_METHOD_DIGEST.equalsIgnoreCase(authMethod)) {
            LOG.error("DIGEST authentication method not yet supported.");
        }
        if (userAgent != null) {
            httpClient.getParams().setParameter(
                    CoreProtocolPNames.USER_AGENT, userAgent);
        }
	}
    
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        cookiesDisabled = xml.getBoolean("cookiesDisabled", cookiesDisabled);
        authMethod = xml.getString("authMethod", authMethod);
        authUsernameField = 
                xml.getString("authUsernameField", authUsernameField);
        authUsername = xml.getString("authUsername", authUsername);
        authPasswordField = 
                xml.getString("authPasswordField", authPasswordField);
        authPassword = xml.getString("authPassword", authPassword);
        authURL = xml.getString("authURL", authURL);
        userAgent = xml.getString("userAgent", userAgent);
        proxyHost = xml.getString("proxyHost", proxyHost);
        proxyPort = xml.getInt("proxyPort", proxyPort);
        proxyUsername = xml.getString("proxyUsername", proxyUsername);
        proxyPassword = xml.getString("proxyPassword", proxyPassword);
        proxyRealm = xml.getString("proxyRealm", proxyRealm);
    }
    @Override
    public void saveToXML(Writer out) {
        // TODO Implement me.
        System.err.println("saveToXML not implemented");
    }
    
    
    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getAuthUsernameField() {
        return authUsernameField;
    }

    public void setAuthUsernameField(String authUsernameField) {
        this.authUsernameField = authUsernameField;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPasswordField() {
        return authPasswordField;
    }

    public void setAuthPasswordField(String authPasswordField) {
        this.authPasswordField = authPasswordField;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public boolean isCookiesDisabled() {
        return cookiesDisabled;
    }

    public void setCookiesDisabled(boolean cookiesDisabled) {
        this.cookiesDisabled = cookiesDisabled;
    }

    public String getAuthURL() {
        return authURL;
    }

    public void setAuthURL(String authURL) {
        this.authURL = authURL;
    }
    
    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getProxyHost() {
        return proxyHost;
    }
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }
    public String getProxyRealm() {
        return proxyRealm;
    }
    public void setProxyRealm(String proxyRealm) {
        this.proxyRealm = proxyRealm;
    }

    protected void authenticateUsingForm(DefaultHttpClient httpClient) {
        HttpPost post = new HttpPost(getAuthURL());

        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair(
                getAuthUsernameField(), getAuthUsername()));
        formparams.add(new BasicNameValuePair(
                getAuthPasswordField(), getAuthPassword()));
        try {
            UrlEncodedFormEntity entity = 
                    new UrlEncodedFormEntity(formparams, "UTF-8");
            post.setEntity(entity);
            httpClient.execute(post);
        } catch (Exception e) {
            throw new HttpCollectorException(e);
        }
        post.releaseConnection();
    }
    
}
