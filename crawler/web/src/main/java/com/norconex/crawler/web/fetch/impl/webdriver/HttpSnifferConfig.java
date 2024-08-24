/* Copyright 2019-2024 Norconex Inc.
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

import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.unit.DataUnit;

import lombok.Data;
import lombok.experimental.Accessors;

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
 *    too large. Can be specified using notations, e.g., 25MB. Default is 10MB)
 * </maxBufferSize>
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
@Data
@Accessors(chain = true)
public class HttpSnifferConfig {

    public static final int DEFAULT_MAX_BUFFER_SIZE =
            DataUnit.MB.toBytes(10).intValue();

    private int port;
    /**
     * The host name passed to the browser pointing to the sniffer proxy.
     * Defaults to "localhost".
     * @param host host name
     * @return host name
     * @since 3.1.0
     */
    private String host;
    private String userAgent;
    private final Map<String, String> requestHeaders = new HashMap<>();
    private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;
    private final ProxySettings chainedProxy = new ProxySettings();

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }
    public HttpSnifferConfig setRequestHeaders(
            Map<String, String> requestHeaders) {
        this.requestHeaders.clear();
        this.requestHeaders.putAll(requestHeaders);
        return this;
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
    public HttpSnifferConfig setChainedProxy(ProxySettings chainedProxy) {
        this.chainedProxy.copyFrom(chainedProxy);
        return this;
    }
}