/* Copyright 2025 Norconex Inc.
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

import static io.netty.handler.codec.http.HttpHeaderNames.USER_AGENT;
import static java.util.Optional.ofNullable;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.extras.SelfSignedMitmManager;
import org.littleshoot.proxy.extras.SelfSignedSslEngineSource;
import org.littleshoot.proxy.impl.ClientDetails;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.openqa.selenium.MutableCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.url.HttpURL;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

public class HttpSniffer {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpSniffer.class);

    public static final String PARAM_REQUEST_ID = "crawlRequestId";
    public static final String HEADER_REQUEST_ID = "X-Crawl-Request-Id";

    private final Cache<String, CompletableFuture<SniffedResponseHeaders>>
            sniffedHeaders = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

    private final HttpProxyServer proxyServer;
    private final HttpSnifferConfig config;
    private final Host proxyHost;

    public HttpSniffer(HttpSnifferConfig snifferConfig) {
        config = Optional.ofNullable(snifferConfig)
                .orElseGet(HttpSnifferConfig::new);

        var hostName = ofNullable(config.getHost()).orElse("localhost");

        var serverBootstrap = DefaultHttpProxyServer.bootstrap()
            .withAddress(new InetSocketAddress(hostName, config.getPort()))
            .withAllowLocalOnly(false)
            .withFiltersSource(new SniffingHttpFilter())
            .withManInTheMiddle(new SelfSignedMitmManager(
                    new SelfSignedSslEngineSource(
                            true, /* trustAllServers= */
                            true)));/* sendCerts= */

        // chained proxy
        if (config.getChainedProxy().isSet()) {
            serverBootstrap.withChainProxyManager(
                    new ChainedProxies(config.getChainedProxy()));
        } else {
            LOG.info("No chained proxy configured on SniffingProxy.");
        }

        proxyServer = serverBootstrap.start();
        proxyHost = new Host(
                hostName, proxyServer.getListenAddress().getPort());
        LOG.info("SnifferProxy started as: {}.", proxyHost);
    }

    public void stop() {
        proxyServer.stop();
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(proxyHost.getName(), proxyHost.getPort());
    }

    public CompletableFuture<SniffedResponseHeaders> track(String requestId) {
        var future = new CompletableFuture<SniffedResponseHeaders>();
        sniffedHeaders.put(requestId, future);
        future.whenComplete((res, ex) -> sniffedHeaders.invalidate(requestId));
        return future;
    }

    public void configureBrowser(Browser browser, MutableCapabilities opts) {
        browser.configureProxy(opts, proxyHost);
    }

    /**
     * Handles proxy chaining
     */
    class ChainedProxies implements ChainedProxyManager {
        private final ProxySettings settings;
        ChainedProxies(ProxySettings proxySettings) {
            settings = proxySettings;
        }
        @Override
        public void lookupChainedProxies(
                HttpRequest httpRequest,
                Queue<ChainedProxy> chainedProxies,
                ClientDetails clientDetails) {
            chainedProxies.add(new ChainedProxyAdapter() {
                @Override
                public InetSocketAddress getChainedProxyAddress() {
                    var addr = new InetSocketAddress(
                            settings.getHost().getName(),
                            settings.getHost().getPort());
                    LOG.info("Chained proxy set on SniffingProxy: {}.", addr);
                    return addr;
                }

                @Override
                public String getUsername() {
                    return settings.getCredentials().getUsername();
                }

                @Override
                public String getPassword() {
                    LOG.info("Chained proxy credentials set.");
                    if (settings.getCredentials().isSet()) {
                        return EncryptionUtil.decryptPassword(
                                settings.getCredentials());
                    }
                    return null;
                }
            });
        }
    }

    /**
     * Filters requests and responses to intercept HTTP headers
     */
    class SniffingHttpFilter extends HttpFiltersSourceAdapter {
        @Override
        public HttpFilters filterRequest(
                HttpRequest originalRequest, ChannelHandlerContext ctx) {
            return new HttpFiltersAdapter(originalRequest) {
                private String requestId;
                private String browserAgent;

                @Override
                public HttpResponse clientToProxyRequest(HttpObject httpObj) {
                    if (httpObj instanceof HttpRequest) {
                        var request = (HttpRequest) httpObj;
                        LOG.trace("Sniffer incoming request: {}", request.uri());
                        var decoder = new QueryStringDecoder(request.uri());
                        var ids = decoder.parameters().get(PARAM_REQUEST_ID);
                        if (CollectionUtils.isNotEmpty(ids)) {
                            var url = new HttpURL(request.uri());
                            requestId = ids.get(0);

                            url.getQueryString().remove(PARAM_REQUEST_ID);
                            request.headers().set(HEADER_REQUEST_ID, requestId);

                            // Add custom request headers
                            config.getRequestHeaders().entrySet().forEach(
                                    en -> request.headers().add(
                                            en.getKey(), en.getValue()));
                            browserAgent = request.headers().get(USER_AGENT);
                            // Overwrite with custom user agent
                            if (StringUtils.isNotBlank(config.getUserAgent())) {
                                request.headers().remove(USER_AGENT);
                                request.headers().add(
                                        USER_AGENT, config.getUserAgent());
                            }

                            request.setUri(url.toString());
                        }
                    }
                    return null; // continue chain
                }

                @Override
                public HttpObject serverToProxyResponse(HttpObject httpObj) {
                    if (httpObj instanceof HttpResponse && requestId != null) {
                        var future = sniffedHeaders.getIfPresent(requestId);
                        var response = (HttpResponse) httpObj;
                        var header = new SniffedResponseHeaders(
                                requestId,
                                response.status(),
                                response.headers(),
                                browserAgent);
                        if (future != null) {
                            future.complete(header);
                        }
                    }
                    return httpObj;
                }
            };
        }

        @Override
        public int getMaximumRequestBufferSizeInBytes() {
            return config.getMaxBufferSize();
        }

        @Override
        public int getMaximumResponseBufferSizeInBytes() {
            return config.getMaxBufferSize();
        }
    }

    public static class SniffedResponseHeaders {
        private final String requestId;
        private final HttpResponseStatus status;
        private final HttpHeaders headers;
        private final String requestUserAgent;


        public SniffedResponseHeaders(
                String requestId,
                HttpResponseStatus status,
                HttpHeaders headers,
                String requestUserAgent) {
            this.requestId = requestId;
            this.status = status;
            this.headers = headers;
            this.requestUserAgent = requestUserAgent;
        }

        public String getRequestId() {
            return requestId;
        }

        public HttpResponseStatus getStatus() {
            return status;
        }

        public HttpHeaders getHeaders() {
            return headers;
        }

        public String getRequestUserAgent() {
            return requestUserAgent;
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof SniffedResponseHeaders)) {
                return false;
            }
            var other = (SniffedResponseHeaders) obj;
            return EqualsBuilder.reflectionEquals(
                    this, other, "requestHeaders")
                    && Objects.equals(headers.entries(),
                            other.headers.entries());
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
