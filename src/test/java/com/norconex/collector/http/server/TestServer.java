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
package com.norconex.collector.http.server;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.Sleeper;

/**
 * @author Pascal Essiembre
 */
//TODO consider making part of Norconex Commons Lang (not just as test)?
public class TestServer {

    private static final Logger LOG = LoggerFactory.getLogger(TestServer.class);

    private Server server = new Server();
    private int port;
    private int securePort;
    private boolean startedOnce;
    private Thread shutdownHook;
    private final boolean enableHttps;

    public TestServer(HandlerList handlers) {
        this(handlers, false);
    }
    public TestServer(HandlerList handlers, boolean enableHttps) {
        this.enableHttps = enableHttps;
        server.setHandler(handlers);
        shutdownHook = new Thread() {
            @Override
            public void run() {
                try {
                    if (server != null) {
                        server.stop();
                    }
                } catch (Exception e) {
                    LOG.error("Could not shutdown test server.", e);
                }
            }
        };
    }

    public void start() {
        StopWatch watch = new StopWatch();
        watch.start();
        if (startedOnce) {
            throw new IllegalStateException(
                    "Cannot start the test server more than once.");
        }
        startedOnce = true;

        setupConnectors();

        // Start it
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        new Thread() {
            @Override
            public void run() {
                try {
                    server.start();
                    port = ((ServerConnector)
                            server.getConnectors()[0]).getLocalPort();
                    if (enableHttps) {
                        securePort = ((ServerConnector)
                                server.getConnectors()[1]).getLocalPort();
                    }

                    watch.stop();
                    LOG.info("Test server started on port " + port
                            + " (" + (watch.getTime() / 1000.0) + " s)");
                    server.join();
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Could not start test server.", e);
                }
            }
        }.start();
        StopWatch timer = new StopWatch();
        timer.start();
        while (!server.isStarted()) {
            Sleeper.sleepMillis(100);
            if (timer.getTime(TimeUnit.SECONDS) > 10) {
                throw new RuntimeException(
                        "Could not start server after 10 seconds. Aborting.");
            }
        }
        timer.stop();
    }

    public void stop() {
        try {
            server.stop();
            server.join();
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
            server = null;
            port = 0;
        } catch (Exception e) {
            throw new RuntimeException("Could not stop test server.", e);
        }
    }

    /**
     * @return the localPort (http)
     */
    public int getPort() {
        return port;
    }
    /**
     * @return the secure localPort (https)
     */
    public int getSecurePort() {
        return securePort;
    }

    private void setupConnectors() {
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        if (enableHttps) {
            HttpConfiguration https = new HttpConfiguration();
            https.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory sslCtxFactory = new SslContextFactory.Server();
            sslCtxFactory.setKeyStorePath(TestServer.class.getResource(
                    "/ssl/keystore.jks").toExternalForm());
            sslCtxFactory.setKeyStorePassword("selfsigned");
            sslCtxFactory.setKeyManagerPassword("selfsigned");

           ServerConnector sslConnector = new ServerConnector(server,
                   new SslConnectionFactory(
                           sslCtxFactory, HttpVersion.HTTP_1_1.asString()),
                   new HttpConnectionFactory(https));
           sslConnector.setPort(0);
           server.addConnector(sslConnector);
        }
    }
}
