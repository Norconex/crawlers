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
package com.norconex.collector.http.server;

import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Pascal Essiembre
 */
//TODO consider making part of Norconex Commons Lang (not just as test)?
public class TestServer {

    private static final Logger LOG = LoggerFactory.getLogger(TestServer.class);

    private Server server = new Server();
    private int port;
    private boolean startedOnce;
    private Thread shutdownHook;

    public TestServer(HandlerList handlers) {
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

        // Set a random port
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        // Start it
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        new Thread() {
            @Override
            public void run() {
                try {
                    server.start();
                    port = ((ServerConnector)
                            server.getConnectors()[0]).getLocalPort();
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
    }

    public void stop() {
        try {
            server.stop();
            server.join();
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
            server = null;
        } catch (Exception e) {
            throw new RuntimeException("Could not stop test server.", e);
        }
    }

    /**
     * @return the localPort
     */
    public int getPort() {
        return port;
    }
}
