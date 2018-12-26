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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.HttpFiltersSource;
import org.openqa.selenium.MutableCapabilities;
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
 * Used to captured HTTP response headers, when enabled.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class WebDriverHttpProxy {

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverHttpProxy.class);

    private final ThreadLocal<FilterAndSource> tlocal = new ThreadLocal<>();

    private BrowserMobProxyServer proxy;


    public void bind(String url) {
        if (proxy == null) {
            return;
        }

        FilterAndSource fs = tlocal.get();
        if (fs != null) {
            throw new IllegalStateException("A URL is already bound to "
                    + "the WebDriver header proxy: " + fs.filter.url);
        }

        DriverResponse f = new DriverResponse(url);
        HttpFiltersSource s = new ResponseFilterAdapter.FilterSource(f);
        tlocal.set(new FilterAndSource(f, s));
        proxy.addLastHttpFilterFactory(s);
    }

    public DriverResponse unbind() {
        if (proxy == null) {
            return null;
        }
        FilterAndSource fs = tlocal.get();
        if (fs == null) {
            return null;
        }
//        List<Map.Entry<String, String>> headers = fs.filter.getHeaders();
        proxy.getFilterFactories().remove(fs.source);
        tlocal.remove();
        return fs.filter;
    }
//    public List<Map.Entry<String, String>> unbind() {
//        if (proxy == null) {
//            return Collections.emptyList();
//        }
//        FilterAndSource fs = tlocal.get();
//        if (fs == null) {
//            return Collections.emptyList();
//        }
//        List<Map.Entry<String, String>> headers = fs.filter.getHeaders();
//        proxy.getFilterFactories().remove(fs.source);
//        tlocal.remove();
//        return headers;
//    }

    public void start(MutableCapabilities options, int port, String userAgent) {
        proxy = new BrowserMobProxyServer();
        proxy.setTrustAllServers(true);
//        proxy.addResponseFilter((response, contents, messageInfo) -> {
//            LOG.info(response.getStatus() + " => " + messageInfo.getOriginalUrl());
//            LOG.info("       => " + messageInfo.getUrl());
//            LOG.info("HEADERS:");
//            for (Entry<String, String> entry : response.headers()) {
//                LOG.info("    " + entry.getKey() + " => " + entry.getValue());
//            }
//        });

        if (StringUtils.isNotBlank(userAgent)) {
            proxy.addHeader("User-Agent", userAgent);
        }

        proxy.start(port);
        int actualPort = proxy.getPort();
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
            profile.setPreference("network.proxy.http_port", proxy.getPort());
            profile.setPreference("network.proxy.ssl", "localhost");
            profile.setPreference("network.proxy.ssl_port", proxy.getPort());
            profile.setPreference("network.proxy.type", 1);
            profile.setPreference("network.proxy.no_proxies_on", "");
            options.setCapability(FirefoxDriver.PROFILE, profile);
        }
        options.setCapability(CapabilityType.PROXY,
                ClientUtil.createSeleniumProxy(proxy));
    }

    public void stop() {
        if (proxy != null && proxy.isStarted()) {
            proxy.stop();
            proxy = null;
        }
    }

    private class FilterAndSource {
        private final DriverResponse filter;
        private final HttpFiltersSource source;
        public FilterAndSource(
                DriverResponse filter, HttpFiltersSource source) {
            super();
            this.filter = filter;
            this.source = source;
        }
    }

    public static class DriverResponse implements ResponseFilter {
        private final List<Map.Entry<String, String>> headers =
                new ArrayList<>();
        private int statusCode;
        private String reasonPhrase;
        private final String url;
        public DriverResponse(String url) {
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

//            LOG.info(response.getStatus() + " => " + messageInfo.getOriginalUrl());
//            LOG.info("       => " + messageInfo.getUrl());
//            LOG.info("HEADERS:");
//            for (Entry<String, String> entry : response.headers()) {
//                LOG.info("    " + entry.getKey() + " => " + entry.getValue());
//            }
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