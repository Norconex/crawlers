/* Copyright 2017-2018 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;

/**
 * Proxy that allows to bing URLs to be processed by an external application
 * with {@link HttpClient} instances.
 * @author Pascal Essiembre
 * @since 2.7.0
 */

//TODO deprecate??

/*default*/ class HttpClientProxy extends HttpServlet {

    private static final long serialVersionUID = 6443306025065328217L;
    private static final Logger LOG =
            LoggerFactory.getLogger(HttpClientProxy.class);

    public static final String KEY_PROXY_REDIRECT = "collector.proxy.redirect";
    public static final String KEY_PROXY_BIND_ID = "collector.proxy.bindId";
    public static final String KEY_PROXY_PROTOCOL = "collector.proxy.protocol";

    private static Server server;
    private static int proxyPort;
    private static boolean proxyStarted;
    private static final BidiMap<HttpClient, Integer> CLIENT_IDS =
            new DualHashBidiMap<>();
    private static final AtomicInteger ID_GEN = new AtomicInteger();

    private HttpClientProxy() {
        super();

    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            proxy(req, resp);
        } catch (SocketException e) {
            if (!resp.isCommitted()) {
                // if a socket problem, retry.
                resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                resp.setHeader("Location", getURL(req));
            } else {
                handleException(req, resp, e);
            }
        } catch (Exception e) {
            handleException(req, resp, e);
        }
    }


    private void handleException(
            HttpServletRequest req, HttpServletResponse resp, Exception e) {

        String url = req.getRequestURL().toString();
        String queryString = req.getQueryString();
        if (queryString != null) {
            url += "?"+queryString;
        }
        if (e instanceof EofException) {
            LOG.debug("Client connection was closed (e.g. timeout) before this "
                   + "URL could be processed: {}", url);
        } else if (e instanceof IllegalStateException
                && "Connection pool shut down".equals(e.getMessage())) {
            LOG.debug("Crawling completed before this URL could be "
                    + "processed: {}", url);
        } else {
            LOG.error("Could NOT proxy this URL: '{}'.", url, e);
        }
        try {
            if (!resp.isCommitted()) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could NOT proxy this URL: '" + url + "'.");
            } else {
                LOG.debug("Could not send error to client: "
                        + "response already commited.");
            }
        } catch (IOException e1) {
            LOG.error("Could not send HTTP error.", e1);
        }
    }



    private void proxy(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Request received.");
        }

        ProxyRequest pr = new ProxyRequest(req);
        InputStream in = null;
        OutputStream out = null;
        HttpGet get = new HttpGet(pr.getUrl());

        try {
            HttpResponse response = pr.getHttpClient().execute(get);

            resp.setStatus(response.getStatusLine().getStatusCode());

            HttpEntity entity = response.getEntity();

            Charset charset = ContentType.getOrDefault(entity).getCharset();
            if (charset != null) {
                resp.setCharacterEncoding(charset.toString());
            }

            in = entity.getContent();
            out = resp.getOutputStream();
            if (in == null || out == null) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            // Set headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                String name = header.getName();
                String value = header.getValue();
                if (!name.toLowerCase().startsWith("transfer-encoding")) {
                    resp.addHeader(name, value);
                }
            }

            //-- Deal with redirects ---
            // We do this here since threading prevents the existing solution
            // from working fine.
            RedirectStrategyWrapper.getRedirectURL();
            String redirectURL = RedirectStrategyWrapper.getRedirectURL();
            if (StringUtils.isNotBlank(redirectURL)) {
                resp.addHeader(KEY_PROXY_REDIRECT, redirectURL);
            }
            IOUtils.copy(in, out);
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
            try {
                get.releaseConnection();
            } catch (Exception e) {
                LOG.warn("Cannont release HttpClientProxy connection", e);
            }
        }
    }

    private String getURL(HttpServletRequest req) {
        String url = req.getRequestURL().toString();
        String queryString = req.getQueryString();
        if (queryString != null) {
            url += "?"+queryString;
        }
        return url;
    }

    public static synchronized int getId(HttpClient httpClient) {
        return CLIENT_IDS.computeIfAbsent(
                httpClient, k -> ID_GEN.incrementAndGet());
    }

    public static synchronized void start() {
        start(0);
    }

    public static synchronized void start(int port) {
        if (proxyStarted) {
            LOG.info("HTTPClient proxy already started.");
            return;
        }

        server = new Server();
        WebAppContext webappContext = new WebAppContext();
        webappContext.setContextPath("/");
        webappContext.setResourceBase("/");

        ServletHolder servletHolder = new ServletHolder(
                new HttpClientProxy());
        webappContext.addServlet(servletHolder, "/*");

        server.setHandler(webappContext);

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        LOG.info("Starting HTTPClient proxy.");

        // start
        try {
            server.start();
            proxyPort = ((ServerConnector)
                    server.getConnectors()[0]).getLocalPort();
            proxyStarted = true;
            LOG.info("HTTPClient proxy started on port {}.", proxyPort);
        } catch (Exception e) {
            throw new CollectorException(
                    "Could not start HTTPClient proxy.", e);
        }
    }
    public static synchronized void stop() {
        if (server == null) {
            LOG.info("HTTPClient proxy was never started.");
            return;
        }
        if (server.isStopped()) {
            LOG.info("HTTPClient proxy already stopped.");
            return;
        }
        LOG.info("Stopping HTTPClient proxy.");
        try {
            server.stop();
            server.join();
            CLIENT_IDS.clear();
            server = null;
            proxyStarted = false;
            proxyPort = 0;
        } catch (Exception e) {
            throw new CollectorException("Could not stop HTTPClient proxy.", e);
        }
    }
    public static boolean isStarted() {
        return proxyStarted;
    }

    public static String getProxyHost() {
        return "http://localhost:" + proxyPort;
    }

    private class ProxyRequest {
        private final String url;
        private final HttpClient httpClient;
        public ProxyRequest(HttpServletRequest req) {
            super();
            String protocol = req.getHeader(KEY_PROXY_PROTOCOL);
            Integer id = Integer.parseInt(req.getHeader(KEY_PROXY_BIND_ID));
            String targetURL = getURL(req);
            if ("https".equals(protocol)) {
                targetURL = targetURL.replaceFirst("http", "https");
            }
            this.url = targetURL;
            this.httpClient = CLIENT_IDS.getKey(id);
            LOG.debug("Proxy URL={}; ID={}; PROTOCOL={}", url, id, protocol);
        }
        public String getUrl() {
            return url;
        }
        public HttpClient getHttpClient() {
            return httpClient;
        }
    }
}
