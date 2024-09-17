/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.fs.fetch.impl.cmis;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.chemistry.opencmis.commons.endpoints.CmisEndpointsDocument;
import org.apache.chemistry.opencmis.commons.impl.IOUtils;
import org.apache.chemistry.opencmis.commons.impl.UrlBuilder;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParseException;
import org.apache.chemistry.opencmis.server.impl.CmisRepositoryContextListener;
import org.apache.chemistry.opencmis.server.impl.atompub.CmisAtomPubServlet;
import org.apache.chemistry.opencmis.server.impl.browser.CmisBrowserBindingServlet;
import org.apache.chemistry.opencmis.server.impl.endpoints.AbstractCmisEndpointsDocumentServlet;
import org.apache.chemistry.opencmis.server.impl.webservices.CmisWebServicesServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CmisTestServer {

    public static final String WS_1_0 = "/services";
    public static final String WS_1_1 = "/services11";
    public static final String ATOM_1_0 = "/atom";
    public static final String ATOM_1_1 = "/atom11";
    public static final String BROWSER = "/browser";

    private final Server server = new Server();

    private int localPort;
    private boolean initialized;

    private WebAppContext buildWebappContext() throws URISyntaxException {

        var webAppDir = Thread.currentThread()
                .getContextClassLoader().getResource("cmis/webapp");
        var webappContext = new WebAppContext();
        webappContext.setResourceBase(webAppDir.toURI().toString());

        webappContext.addEventListener(new CmisRepositoryContextListener());

        var servlet = new ServletHolder(new CmisWebServicesServlet());

        servlet.setInitParameter("cmisVersion", "1.0");
        webappContext.addServlet(servlet, "/services/*");

        // CMIS Web Service 1.1
        servlet = new ServletHolder(new CmisWebServicesServlet());
        servlet.setInitParameter("cmisVersion", "1.1");
        webappContext.addServlet(servlet, "/services11/*");

        // CMIS Atom 1.0
        servlet = new ServletHolder(new CmisAtomPubServlet());
        servlet.setInitParameter("cmisVersion", "1.0");
        servlet.setInitParameter(
                "callContextHandler", "org.apache.chemistry."
                        + "opencmis.server.shared.BasicAuthCallContextHandler");
        webappContext.addServlet(servlet, "/atom/*");

        // CMIS Atom 1.1
        servlet = new ServletHolder(new CmisAtomPubServlet());
        servlet.setInitParameter("cmisVersion", "1.1");
        servlet.setInitParameter(
                "callContextHandler", "org.apache.chemistry."
                        + "opencmis.server.shared.BasicAuthCallContextHandler");
        webappContext.addServlet(servlet, "/atom11/*");

        // CMIS Browser
        servlet = new ServletHolder(new CmisBrowserBindingServlet());
        servlet.setInitParameter("cmisVersion", "1.1");
        servlet.setInitParameter(
                "callContextHandler", "org.apache.chemistry."
                        + "opencmis.server.impl.browser.token.TokenCallContextHandler");
        webappContext.addServlet(servlet, "/browser/*");

        // CMIS Endpoints
        servlet = new ServletHolder(new CmisEndpointsDocumentServlet());
        webappContext.addServlet(servlet, "/cmis-endpoints.json");

        return webappContext;
    }

    private ResourceHandler buildStaticResourcesHandler() {
        var staticHandler = new ResourceHandler();
        var staticResources = CmisTestServer.class.getClassLoader().getResource(
                "cmis/webapp");
        staticHandler.setResourceBase(staticResources.toExternalForm());
        staticHandler.setWelcomeFiles(new String[] { "index.html" });
        staticHandler.setDirectoriesListed(false);
        return staticHandler;
    }

    private synchronized void initServer() throws URISyntaxException {
        if (initialized) {
            return;
        }
        var webappContext = buildWebappContext();
        var staticHandler = buildStaticResourcesHandler();

        var handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { staticHandler, webappContext });
        server.setHandler(handlers);

        var connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        initialized = true;
    }

    public void start() throws Exception {
        initServer();
        server.start();
        localPort =
                ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        System.out.println(
                "Test CMIS server has successfully started on port "
                        + localPort);

        //        server.join();
    }

    public void stop() throws Exception {
        server.stop();
        //        server.join();
    }

    /**
     * @return the localPort
     */
    public int getLocalPort() {
        return localPort;
    }

    public static void main(String[] args) throws Exception {
        final var server = new CmisTestServer();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    server.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        //        server.run();

        new Thread() {
            @Override
            public void run() {
                try {
                    server.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
    }

    // Adapted from org.apache.chemistry...SimpleCmisEndpointsDocumentServlet
    private class CmisEndpointsDocumentServlet
            extends AbstractCmisEndpointsDocumentServlet {
        private static final long serialVersionUID = 1L;
        private static final String EP_LOC = "cmis/webapp/cmis-endpoints.json";

        private String endpointsDocument;

        @Override
        public void init(ServletConfig config) throws ServletException {
            super.init(config);
            try (var stream =
                    getClass().getResourceAsStream(EP_LOC)) {
                endpointsDocument = IOUtils.readAllLines(stream);
            } catch (IOException e) {
                throw new ServletException("Could not read " + EP_LOC, e);
            }
        }

        @Override
        public CmisEndpointsDocument getCmisEndpointsDocument(
                HttpServletRequest req, HttpServletResponse resp) {
            if (endpointsDocument == null) {
                // we don't have a template
                return null;
            }
            var url = new UrlBuilder(
                    req.getScheme(), req.getServerName(),
                    req.getServerPort(), null);
            url.addPath(req.getContextPath());
            try {
                return readCmisEndpointsDocument(
                        endpointsDocument.replaceAll(
                                "\\{webapp\\}", url.toString()));
            } catch (JSONParseException e) {
                LOG.error("Invalid JSON!", e);
                return null;
            }
        }
    }
}
