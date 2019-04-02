/* Copyright 2018-2019 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.HttpFiltersSource;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.filters.ResponseFilter;
import net.lightbody.bmp.filters.ResponseFilterAdapter;
import net.lightbody.bmp.util.HttpMessageContents;
import net.lightbody.bmp.util.HttpMessageInfo;

/**
 * <p>
 * Used to set and capture HTTP request/response headers, when enabled.
 * </p>
 * <p>
 * <b>EXPERIMENTAL:</b> The use of this class is experimental.
 * It is known to not be supported properly
 * with some web drivers and/or browsers. It can even be ignored altogether
 * by some web drivers.  It is discouraged for normal use,
 * and is disabled by default.
 * </p>
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class WebDriverHttpSniffer {

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverHttpSniffer.class);

    private final ThreadLocal<FilterAndSource> tlocal = new ThreadLocal<>();
    private BrowserMobProxyServer mobProxy;

    public void bind(String url) {
        if (mobProxy == null) {
            return;
        }

        FilterAndSource fs = tlocal.get();
        if (fs != null) {
            throw new IllegalStateException("A URL is already bound to "
                    + "WebDriverHttpAdapter on this thread: " + fs.filter.url);
        }

        DriverResponseFilter f = new DriverResponseFilter(url);
        HttpFiltersSource s = new ResponseFilterAdapter.FilterSource(f);
        tlocal.set(new FilterAndSource(f, s));
        mobProxy.addLastHttpFilterFactory(s);
    }

    public DriverResponseFilter unbind() {
        if (mobProxy == null) {
            return null;
        }
        FilterAndSource fs = tlocal.get();
        if (fs == null) {
            return null;
        }
        mobProxy.getFilterFactories().remove(fs.source);
        tlocal.remove();
        return fs.filter;
    }

    public void start(MutableCapabilities options) {
        start(options, null);
    }
    public void start(
            MutableCapabilities options, WebDriverHttpSnifferConfig config) {
        Objects.requireNonNull("'options' must not be null");

        WebDriverHttpSnifferConfig cfg = Optional.ofNullable(
                config).orElseGet(WebDriverHttpSnifferConfig::new);

        mobProxy = new BrowserMobProxyServer();
        mobProxy.setTrustAllServers(true);

        // request headers
        config.getRequestHeaders().entrySet().forEach(
                en -> mobProxy.addHeader(en.getKey(), en.getValue()));

        // User agent
        if (StringUtils.isNotBlank(cfg.getUserAgent())) {
            mobProxy.addHeader("User-Agent", cfg.getUserAgent());
        }

        mobProxy.start(cfg.getPort());


        int actualPort = mobProxy.getPort();
        LOG.info("Proxy started on port {} "
                + "for HTTP response header capture.", actualPort);

        // Fix bug with firefox where request/response filters are not
        // triggered properly unless dealing with firefox profile
        if (options instanceof FirefoxOptions) {
            //TODO Shall we prevent calls to firefox browser addons?

            FirefoxProfile profile = new FirefoxProfile();
            profile.setAcceptUntrustedCertificates(true);
            profile.setAssumeUntrustedCertificateIssuer(true);
            profile.setPreference("network.proxy.http", "localhost");
            profile.setPreference("network.proxy.http_port", actualPort);
            profile.setPreference("network.proxy.ssl", "localhost");
            profile.setPreference("network.proxy.ssl_port", actualPort);
            profile.setPreference("network.proxy.type", 1);
            profile.setPreference("network.proxy.no_proxies_on", "");
            options.setCapability(FirefoxDriver.PROFILE, profile);
        } else if (options instanceof ChromeOptions && LOG.isDebugEnabled()) {
            System.setProperty("webdriver.chrome.verboseLogging", "true");
        }
        options.setCapability(CapabilityType.PROXY,
                ClientUtil.createSeleniumProxy(mobProxy));
    }

    public void stop() {
        if (mobProxy != null && mobProxy.isStarted()) {
            mobProxy.stop();
            mobProxy = null;
        }
    }

    private class FilterAndSource {
        private final DriverResponseFilter filter;
        private final HttpFiltersSource source;
        public FilterAndSource(
                DriverResponseFilter filter, HttpFiltersSource source) {
            super();
            this.filter = filter;
            this.source = source;
        }
    }

    public static class DriverResponseFilter implements ResponseFilter {
        private final List<Map.Entry<String, String>> headers =
                new ArrayList<>();
        private int statusCode;
        private String reasonPhrase;
        private final String url;
        public DriverResponseFilter(String url) {
            super();
            this.url = url;
        }
        @Override
        public void filterResponse(HttpResponse response,
                HttpMessageContents contents, HttpMessageInfo messageInfo) {
            if (url.equals(messageInfo.getOriginalUrl())) {
                headers.addAll(response.headers().entries());
                statusCode = response.getStatus().code();
                reasonPhrase = response.getStatus().reasonPhrase();
            }
        }
        public List<Map.Entry<String, String>> getHeaders() {
            return headers;
        }
        public int getStatusCode() {
            return statusCode;
        }
        public String getReasonPhrase() {
            return reasonPhrase;
        }
    }
}