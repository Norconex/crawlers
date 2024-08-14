/* Copyright 2018-2021 Norconex Inc.
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

import static java.util.Optional.ofNullable;
import static org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength.HARD;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.collections4.map.ReferenceMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.EqualsUtil;

import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.filters.ResponseFilterAdapter;

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
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
class HttpSniffer {

    //MAYBE: If it gets stable enough, move the proxy setting to Browser class?

    private static final Logger LOG = LoggerFactory.getLogger(
            HttpSniffer.class);

    private final Map<String, SniffedResponseHeader> trackedUrlResponses =
            new ReferenceMap<>(HARD, ReferenceStrength.WEAK);

    private BrowserMobProxyServer mobProxy;

    SniffedResponseHeader track(String url) {
        return trackedUrlResponses.computeIfAbsent(
                url, na -> new SniffedResponseHeader());
    }
    void untrack(String url) {
        trackedUrlResponses.remove(url);
    }

    void start(MutableCapabilities options, HttpSnifferConfig config) {
        var cfg = Optional.ofNullable(config).orElseGet(HttpSnifferConfig::new);
        mobProxy = new BrowserMobProxyServer();
        mobProxy.setTrustAllServers(true);
        mobProxy.setTrustSource(null);
        //NOTE we use to have `mobProxy.setMitmDisabled(true)` here, but
        // that made it fail to invoke the response filter set below.
        // We can make that option configurable if it causes issues for some.


        // request headers
        cfg.getRequestHeaders().entrySet().forEach(
                en -> mobProxy.addHeader(en.getKey(), en.getValue()));

        // User agent
        if (StringUtils.isNotBlank(cfg.getUserAgent())) {
            mobProxy.addHeader("User-Agent", cfg.getUserAgent());
        }

        mobProxy.addLastHttpFilterFactory(
                new ResponseFilterAdapter.FilterSource(
                        (response, contents, messageInfo) -> {
            // sniff only if original URL is being tracked
            var trackedResponse =
                    trackedUrlResponses.get(messageInfo.getOriginalUrl());

            if (trackedResponse != null) {
                response.headers().forEach(en ->
                    trackedResponse.headers.put(en.getKey(), en.getValue()));
                trackedResponse.statusCode = response.status().code();
                trackedResponse.reasonPhrase = response.status().reasonPhrase();
            }
        }, cfg.getMaxBufferSize()));

        mobProxy.start(cfg.getPort());

        var actualPort = mobProxy.getPort();
        LOG.info("Proxy started on port {} "
                + "for HTTP response header capture.", actualPort);

        var proxyStr = ofNullable(cfg.getHost()).orElse("localhost")
                + ":" + actualPort;

        LOG.info("Proxy set on browser as: {}.", proxyStr);

        // Fix bug with firefox where request/response filters are not
        // triggered properly unless dealing with firefox profile
        if (options instanceof FirefoxOptions) {
            //TODO Shall we prevent calls to firefox browser addons?
            var profile = ((FirefoxOptions) options).getProfile();
            profile.setAcceptUntrustedCertificates(true);
            profile.setAssumeUntrustedCertificateIssuer(true);
            profile.setPreference("network.proxy.http", "localhost");
            profile.setPreference("network.proxy.http_port", actualPort);
            profile.setPreference("network.proxy.ssl", "localhost");
            profile.setPreference("network.proxy.ssl_port", actualPort);
            profile.setPreference("network.proxy.type", 1);
            profile.setPreference("network.proxy.no_proxies_on", "");
            profile.setPreference("devtools.console.stdout.content", true);
            // Required since FF v67 to enable a localhost proxy:
            // https://bugzilla.mozilla.org/show_bug.cgi?id=1535581
            profile.setPreference(
                    "network.proxy.allow_hijacking_localhost", true);
            ((FirefoxOptions) options).setProfile(profile);

        } else if (options instanceof ChromeOptions) {
            var chromeOptions = (ChromeOptions) options;
            // Required since Chrome v72 to enable a localhost proxy:
            // https://bugs.chromium.org/p/chromium/issues/detail?id=899126#c15
            chromeOptions.addArguments(
                    "--proxy-bypass-list=<-loopback>",
                    "--proxy-server=" + proxyStr,
                    "--disable-popup-blocking",
                    "--disable-extensions",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-software-rasterizer",
                    "--disable-infobars",
                    "--disable-browser-side-navigation",
                    "--disable-features=EnableEphemeralFlashPermission",
                    "--disable-translate",
                    "--disable-sync",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-sign-in"
            );
            if  (LOG.isDebugEnabled()) {
                System.setProperty("webdriver.chrome.verboseLogging", "true");
            }
        }
    }

    void stop() {
        if (mobProxy != null && mobProxy.isStarted()) {
            mobProxy.stop();
            mobProxy = null;
        }
    }


    static class SniffedResponseHeader {

        private final Map<String, String> headers =
                new ListOrderedMap<>();
        private int statusCode;
        private String reasonPhrase;
        public Map<String, String> getHeaders() {
            return headers;
        }
        public int getStatusCode() {
            return statusCode;
        }
        public String getReasonPhrase() {
            return reasonPhrase;
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof SniffedResponseHeader)) {
                return false;
            }
            var other = (SniffedResponseHeader) obj;
            return EqualsBuilder.reflectionEquals(
                    this, other, "requestHeaders")
                    && EqualsUtil.equalsMap(headers, other.headers);
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

}