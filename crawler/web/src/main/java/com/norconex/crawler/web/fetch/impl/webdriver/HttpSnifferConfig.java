/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.webdriver;

import java.util.HashMap;
import java.util.Map;

import com.norconex.commons.lang.unit.DataUnit;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link HttpSniffer}.
 * </p>
 *
 * {@nx.xml.usage
 * <host>
 *   (Host to access the HTTP Sniffer as a proxy. Default is "localhost")
 * </host>
 * <port>(default is 0 = random free port)</port>
 * <userAgent>(optionally overwrite browser user agent)</userAgent>
 * <maxBufferSize>
 *   (Maximum byte size before a request/response content is considered
 *    too large. Can be specified using notations, e.g., 25MB. Default is 10MB)
 * </maxBufferSize>
 * <!-- Optional HTTP request headers passed on every HTTP requests -->
 * <headers>
 *   <!-- You can repeat this header tag as needed. -->
 *   <header name="(header name)">(header value)</header>
 * </headers>
 * }
 *
 * <p>
 * The above XML configurable options can be nested in a supporting parent
 * tag of any name.
 * The expected parent tag name is defined by the consuming classes
 * (e.g. "httpSniffer").
 * </p>
 *
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class HttpSnifferConfig {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_MAX_BUFFER_SIZE =
            DataUnit.MB.toBytes(10).intValue();

    private String host = DEFAULT_HOST;
    private int port;
    private String userAgent;
    private final Map<String, String> requestHeaders = new HashMap<>();
    private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;

    public HttpSnifferConfig setRequestHeaders(
            Map<String, String> requestHeaders) {
        this.requestHeaders.clear();
        this.requestHeaders.putAll(requestHeaders);
        return this;
    }

//    @Override
//    public void loadFromXML(XML xml) {
//        setHost(xml.getString("host", getHost()));
//        setPort(xml.getInteger("port", getPort()));
//        setUserAgent(xml.getString("userAgent", getUserAgent()));
//        setMaxBufferSize(xml.getDataSize(
//                "maxBufferSize", (long) getMaxBufferSize()).intValue());
//        setRequestHeaders(xml.getStringMap(
//                "headers/header", "@name", ".", requestHeaders));
//    }
//    @Override
//    public void saveToXML(XML xml) {
//        xml.addElement("host", host);
//        xml.addElement("port", port);
//        xml.addElement("userAgent", userAgent);
//        xml.addElement("maxBufferSize", maxBufferSize);
//        var xmlHeaders = xml.addXML("headers");
//        for (Entry<String, String> entry : requestHeaders.entrySet()) {
//            xmlHeaders.addXML("header").setAttribute(
//                    "name", entry.getKey()).setTextContent(entry.getValue());
//        }
//    }
}