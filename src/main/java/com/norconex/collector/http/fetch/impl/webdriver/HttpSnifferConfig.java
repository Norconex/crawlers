/* Copyright 2019-2021 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl.webdriver;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Configuration for {@link HttpSniffer}.
 * </p>
 *
 * {@nx.xml.usage
 * <port>(default is 0 = random free port)</port>
 * <host>(default is "localhost")</host>
 * <userAgent>(optionally overwrite browser user agent)</userAgent>
 * <maxBufferSize>
 *   (Maximum byte size before a request/response content is considered
 *    too large. Can be specified using notations, e.g., 25MB.
 *    Zero or less means unlimited. Default is 10MB)
 * </maxBufferSize>
 * <responseTimeout>
 *   How long to wait for the HTTP response from the target host to be
 *   processed.
 * </responseTimeout>
 * <!-- Optional HTTP request headers passed on every HTTP requests -->
 * <headers>
 *   <!-- You can repeat this header tag as needed. -->
 *   <header name="(header name)">(header value)</header>
 * </headers>
 * <!-- Optional chained proxy -->
 * <chainedProxy>
 *   {@nx.include com.norconex.commons.lang.net.ProxySettings@nx.xml.usage}
 * </chainedProxy>
 * }
 *
 * <p>
 * The above XML configurable options can be nested in a supporting parent
 * tag of any name.
 * The expected parent tag name is defined by the consuming classes
 * (e.g. "httpSniffer").
 * </p>
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
public class HttpSnifferConfig implements IXMLConfigurable {

    public static final int DEFAULT_MAX_BUFFER_SIZE =
            DataUnit.MB.toBytes(10).intValue();
    public static final Duration DEFAULT_RESPONSE_TIMEOUT =
            Duration.ofMinutes(2);

    private int port;
    private String host;
    private String userAgent;
    private final Map<String, String> requestHeaders = new HashMap<>();
    private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;
    private final ProxySettings chainedProxy = new ProxySettings();
    private Duration responseTimeout = DEFAULT_RESPONSE_TIMEOUT;

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    /**
     * Gets the host name passed to the browser pointing to the sniffer proxy.
     * Defaults to "localhost".
     * @return host name
     * @since 3.1.0
     */
    public String getHost() {
        return host;
    }
    /**
     * Sets the host name passed to the browser pointing to the sniffer proxy.
     * @param host host name
     * @since 3.1.0
     */
    public void setHost(String host) {
        this.host = host;
    }
    public String getUserAgent() {
        return userAgent;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }
    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders.clear();
        this.requestHeaders.putAll(requestHeaders);
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }
    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * Gets the amount of time for the HTTP sniffer to wait for the target
     * host response matching the request being "sniffed". Default is
     * 2 minutes.
     * @return a duration
     * @since 3.1.0
     */
    public Duration getResponseTimeout() {
        return responseTimeout;
    }
    /**
     * Gets the amount of time for the HTTP sniffer to wait for the target
     * host response matching the request being "sniffed". Default is
     * 2 minutes.
     * @since 3.1.0
     * @param responseTimeout response timeout
     */
    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    /**
     * Gets chained proxy settings, if any. That is, when the sniffer proxy
     * has to itself use a proxy.
     * @return chained proxy settings
     * @since 3.1.0
     */
    public ProxySettings getChainedProxy() {
        return chainedProxy;
    }
    /**
     * Sets chained proxy settings, if any. That is, when the sniffer proxy
     * has to itself use a proxy.
     * @param chainedProxy chained proxy settings
     * @since 3.1.0
     */
    public void setChainedProxy(ProxySettings chainedProxy) {
        this.chainedProxy.copyFrom(chainedProxy);
    }

    @Override
    public void loadFromXML(XML xml) {
        setPort(xml.getInteger("port", getPort()));
        setHost(xml.getString("host", getHost()));
        setUserAgent(xml.getString("userAgent", getUserAgent()));
        setMaxBufferSize(xml.getDataSize(
                "maxBufferSize", (long) getMaxBufferSize()).intValue());
        setResponseTimeout(xml.getDuration("responseTimeout", responseTimeout));
        setRequestHeaders(xml.getStringMap(
                "headers/header", "@name", ".", requestHeaders));
        xml.ifXML("chainedProxy", chainedProxy::loadFromXML);
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("port", port);
        xml.addElement("host", host);
        xml.addElement("userAgent", userAgent);
        xml.addElement("maxBufferSize", maxBufferSize);
        xml.addElement("responseTimeout", responseTimeout);
        var xmlHeaders = xml.addXML("headers");
        for (Entry<String, String> entry : requestHeaders.entrySet()) {
            xmlHeaders.addXML("header").setAttribute(
                    "name", entry.getKey()).setTextContent(entry.getValue());
        }
        chainedProxy.saveToXML(xml.addElement("chainedProxy"));
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof HttpSnifferConfig)) {
            return false;
        }
        var other = (HttpSnifferConfig) obj;
        return EqualsBuilder.reflectionEquals(
                this, other, "requestHeaders")
                && EqualsUtil.equalsMap(requestHeaders, other.requestHeaders);
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