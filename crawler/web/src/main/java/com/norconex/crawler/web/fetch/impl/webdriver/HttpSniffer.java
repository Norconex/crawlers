/* Copyright 2018-2025 Norconex Inc.
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

import static java.util.Optional.ofNullable;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceMap;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.encrypt.EncryptionUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.filters.ResponseFilterAdapter;
import net.lightbody.bmp.proxy.auth.AuthType;

/**
 * <p>
 * Used to set and capture HTTP request/response headers, when enabled.
 * </p>
 * <p>
 * <b>EXPERIMENTAL:</b> The use of this class is experimental.
 * It is known to not be supported properly with some web drivers and/or
 * browsers. It can even be ignored altogether by some web drivers.
 * </p>
 *
 * @since 3.0.0
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class HttpSniffer implements Configurable<HttpSnifferConfig> {

    //MAYBE If it gets stable enough, move the proxy setting to Browser class.

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final Map<String, SniffedResponseHeader> trackedUrlResponses =
            new ReferenceMap<>(ReferenceStrength.HARD, ReferenceStrength.WEAK);

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BrowserMobProxyServer mobProxy;

    @Getter
    private final HttpSnifferConfig configuration = new HttpSnifferConfig();

    SniffedResponseHeader track(String url) {
        return trackedUrlResponses.computeIfAbsent(
                url, na -> new SniffedResponseHeader());
    }

    void untrack(String url) {
        trackedUrlResponses.remove(url);
    }

    void start(MutableCapabilities options) {
        //NOTE we use to have `mobProxy.setMitmDisabled(true)` in this method,
        // but that made it fail to invoke the response filter set below.
        // We can make that option configurable if it causes issues for some.

        mobProxy = new BrowserMobProxyServer();
        mobProxy.setTrustAllServers(true);
        mobProxy.setTrustSource(null);

        var chainedCfg = configuration.getChainedProxy();
        if (chainedCfg.isSet()) {
            var inetAddress = chainedCfg.getHost().toInetSocketAddress();
            // Set Chained Proxy Host and IP
            mobProxy.setChainedProxy(inetAddress);
            LOG.info("Chained proxy set on HTTP Sniffer as: {}.", inetAddress);
            // Set Chained Proxy Credentials
            var creds = chainedCfg.getCredentials();
            if (chainedCfg.getCredentials().isSet()) {
                mobProxy.chainedProxyAuthorization(
                        creds.getUsername(),
                        EncryptionUtil.decryptPassword(creds),
                        AuthType.BASIC);
                LOG.info("Chained proxy authorization set.");
            }
        } else {
            LOG.info("No chained proxy configured on HTTP Sniffer.");
        }

        // request headers
        configuration.getRequestHeaders().entrySet().forEach(
                en -> mobProxy.addHeader(en.getKey(), en.getValue()));

        // User agent
        if (StringUtils.isNotBlank(configuration.getUserAgent())) {
            mobProxy.addRequestFilter((request, contents, messageInfo) -> {
                request.headers().remove("User-Agent");
                request.headers().add(
                        "User-Agent", configuration.getUserAgent());
                return null;// Return null to continue with the modified request
            });
        }

        //Fix response too long in HttpSniffer
        mobProxy.addLastHttpFilterFactory(
                new ResponseFilterAdapter.FilterSource(
                        (response, contents, messageInfo) -> {
                            // sniff only if original URL is being tracked
                            var trackedResponse = trackedUrlResponses
                                    .get(messageInfo.getOriginalUrl());
                            if (trackedResponse != null) {
                                response.headers().forEach(
                                        en -> trackedResponse.headers.put(
                                                en.getKey(), en.getValue()));
                                trackedResponse.statusCode =
                                        response.status().code();
                                trackedResponse.reasonPhrase =
                                        response.status().reasonPhrase();
                            }
                        }, configuration.getMaxBufferSize()));

        Optional.ofNullable(configuration.getHost())
                .map(host -> new InetSocketAddress(host,
                        configuration.getPort()).getAddress())
                .ifPresentOrElse(
                        address -> mobProxy.start(configuration.getPort(),
                                address),
                        () -> mobProxy.start(configuration.getPort()));

        var actualPort = mobProxy.getPort();
        var proxyHost = ofNullable(configuration.getHost()).orElse("localhost");
        LOG.info("HttpSniffer set as a proxy on browser as: {}.",
                proxyHost + ":" + actualPort);

        // Fix bug with firefox where request/response filters are not
        // triggered properly unless dealing with firefox profile
        if (options instanceof FirefoxOptions) {
            //TODO Shall we prevent calls to firefox browser addons?
            var profile = ((FirefoxOptions) options).getProfile();
            profile.setAcceptUntrustedCertificates(true);
            profile.setAssumeUntrustedCertificateIssuer(true);
            profile.setPreference("network.proxy.http", proxyHost);
            profile.setPreference("network.proxy.http_port", actualPort);
            profile.setPreference("network.proxy.ssl", proxyHost);
            profile.setPreference("network.proxy.ssl_port", actualPort);
            profile.setPreference("network.proxy.type", 1);
            profile.setPreference("network.proxy.no_proxies_on", "");
            profile.setPreference("devtools.console.stdout.content", true);
            // Required since FF v67 to enable a localhost proxy:
            // https://bugzilla.mozilla.org/show_bug.cgi?id=1535581
            profile.setPreference(
                    "network.proxy.allow_hijacking_localhost", true);
            ((FirefoxOptions) options).setProfile(profile);

        } else if (options instanceof ChromeOptions chromeOptions) {
            // Required since Chrome v72 to enable a localhost proxy:
            // https://bugs.chromium.org/p/chromium/issues/detail?id=899126#c15
            chromeOptions.addArguments(
                    "--proxy-bypass-list=<-loopback>",
                    "--proxy-server=" + proxyHost + ":" + actualPort,
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
                    "--disable-sign-in");
            if (LOG.isDebugEnabled()) {
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

    @EqualsAndHashCode
    @ToString
    @Getter
    static class SniffedResponseHeader {
        private final MultiValuedMap<String, String> headers =
                MultiMapUtils.newListValuedHashMap();
        private int statusCode;
        private String reasonPhrase;

        public MultiValuedMap<String, String> getHeaders() {
            return headers;
        }
    }
}
