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
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class HttpSnifferConfig {

    public static final int DEFAULT_MAX_BUFFER_SIZE =
            DataUnit.MB.toBytes(10).intValue();

    /**
     * The host name passed to the browser pointing to the sniffer proxy.
     * Defaults to 0 (random free port).
     */
    private int port;
    /**
     * The host name passed to the browser pointing to the sniffer proxy.
     * Defaults to "localhost".
     */
    private String host;
    /**
     * Optionally overwrite browser user agent.
     */
    private String userAgent;
    private final Map<String, String> requestHeaders = new HashMap<>();
    /**
     * Maximum byte size before a request/response content is considered too
     * large. Can be specified using notations, e.g., 25MB. Default is
     * {@value #DEFAULT_MAX_BUFFER_SIZE}.
     */
    private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;

    /**
     * Chained proxy for cases where the HTTP Sniffer itself needs to use a
     * proxy.
     * @since 3.1.0
     */
    private final ProxySettings chainedProxy = new ProxySettings();

    /**
     * Gets the request headers to add to every HTTP request.
     * @return map of request headers
     */
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * Sets the request headers to add to every HTTP request.
     * @param requestHeaders map of request headers
     * @return this
     */
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
     * @return this
     * @since 3.1.0
     */
    public HttpSnifferConfig setChainedProxy(ProxySettings chainedProxy) {
        this.chainedProxy.copyFrom(chainedProxy);
        return this;
    }
}