/* Copyright 2019 Norconex Inc.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * Configuration for {@link WebDriverHttpAdapter}.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class WebDriverHttpAdapterConfig implements IXMLConfigurable {

    private int port;
    private String userAgent;
    private final Map<String, String> requestHeaders = new HashMap<>();

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
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

    @Override
    public void loadFromXML(XML xml) {
        setPort(xml.getInteger("port"));
        setUserAgent(xml.getString("userAgent"));
        setRequestHeaders(xml.getStringMap(
                "headers/header", "@name", ".", requestHeaders));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("port", port);
        xml.addElement("userAgent", userAgent);
        XML xmlHeaders = xml.addXML("headers");
        for (Entry<String, String> entry : requestHeaders.entrySet()) {
            xmlHeaders.addXML("header").setAttribute(
                    "name", entry.getKey()).setTextContent(entry.getValue());
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof WebDriverHttpAdapterConfig)) {
            return false;
        }
        WebDriverHttpAdapterConfig other = (WebDriverHttpAdapterConfig) obj;
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