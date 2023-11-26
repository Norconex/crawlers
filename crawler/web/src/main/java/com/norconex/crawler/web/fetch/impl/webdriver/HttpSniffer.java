/* Copyright 2018-2023 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

import com.norconex.commons.lang.config.Configurable;

import io.netty.handler.codec.http.HttpResponse;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.BrowserMobProxyServer;
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
 *
 * @since 3.0.0
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class HttpSniffer implements Configurable<HttpSnifferConfig> {

    //MAYBE If it gets stable enough, move the proxy setting to Browser class.

    private final ThreadLocal<FilterAndSource> tlocal = new ThreadLocal<>();
    private BrowserMobProxyServer mobProxy;

    @Getter
    private final HttpSnifferConfig configuration = new HttpSnifferConfig();

    void bind(String url) {
        if (mobProxy == null) {
            return;
        }

        var fs = tlocal.get();
        if (fs != null) {
            throw new IllegalStateException("A URL is already bound to "
                    + "WebDriverHttpAdapter on this thread: " + fs.filter.url);
        }

        var f = new DriverResponseFilter(url);
        HttpFiltersSource s = new ResponseFilterAdapter.FilterSource(f);
        tlocal.set(new FilterAndSource(f, s));
        mobProxy.addLastHttpFilterFactory(s);
    }

    DriverResponseFilter unbind() {
        if (mobProxy == null) {
            return null;
        }
        var fs = tlocal.get();
        if (fs == null) {
            return null;
        }
        mobProxy.getFilterFactories().remove(fs.source);
        tlocal.remove();
        return fs.filter;
    }

    void start(@NonNull MutableCapabilities options) {

        var cfg = ofNullable(configuration).orElseGet(HttpSnifferConfig::new);

        mobProxy = new BrowserMobProxyServer();
        mobProxy.setTrustAllServers(true);

        // maximum content length (#751)
        if (cfg.getMaxBufferSize() > 0 ) {
            mobProxy.addFirstHttpFilterFactory(new HttpFiltersSourceAdapter() {
                @Override
                public int getMaximumRequestBufferSizeInBytes() {
                    return cfg.getMaxBufferSize();
                }
                @Override
                public int getMaximumResponseBufferSizeInBytes() {
                    return cfg.getMaxBufferSize();
                }
            });
        }

        // request headers
        cfg.getRequestHeaders().entrySet().forEach(
                en -> mobProxy.addHeader(en.getKey(), en.getValue()));

        // User agent
        if (StringUtils.isNotBlank(cfg.getUserAgent())) {
            mobProxy.addHeader("User-Agent", cfg.getUserAgent());
        }

        mobProxy.start(cfg.getPort());

        var actualPort = mobProxy.getPort();
        LOG.info("Proxy started on port {} "
                + "for HTTP response header capture.", actualPort);




        var proxy = new Proxy();
        var proxyStr = cfg.getHost() + ":" + actualPort;
        proxy.setHttpProxy(proxyStr);
        options.setCapability("proxy", proxy);

        LOG.info("Proxy set on browser as: {}.", proxyStr);


        // Fix bug with firefox where request/response filters are not
        // triggered properly unless dealing with firefox profile
        if (options instanceof FirefoxOptions foxOptions) {
            //MAYBE: Shall we prevent calls to firefox browser addons?

            var profile = new FirefoxProfile();
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

            foxOptions.setProfile(profile);

        } else if (options instanceof ChromeOptions chromeOptions) {
            // Required since Chrome v72 to enable a localhost proxy:
            // https://bugs.chromium.org/p/chromium/issues/detail?id=899126#c15
            chromeOptions.addArguments("--proxy-bypass-list=<-loopback>");
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

    private static class FilterAndSource {
        private final DriverResponseFilter filter;
        private final HttpFiltersSource source;
        public FilterAndSource(
                DriverResponseFilter filter, HttpFiltersSource source) {
            this.filter = filter;
            this.source = source;
        }
    }

    static class DriverResponseFilter implements ResponseFilter {
        private final List<Map.Entry<String, String>> headers =
                new ArrayList<>();
        private int statusCode;
        private String reasonPhrase;
        private final String url;
        public DriverResponseFilter(String url) {
            this.url = url;
        }
        @Override
        public void filterResponse(HttpResponse response,
                HttpMessageContents contents, HttpMessageInfo messageInfo) {
            if (url.equals(messageInfo.getOriginalUrl())) {
                headers.addAll(response.headers().entries());
                statusCode = response.status().code();
                reasonPhrase = response.status().reasonPhrase();
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