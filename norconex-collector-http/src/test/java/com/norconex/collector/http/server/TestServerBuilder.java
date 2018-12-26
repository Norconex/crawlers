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

import java.net.URL;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * @author Pascal Essiembre
 *
 */
//TODO consider making part of Norconex Commons Lang?
public class TestServerBuilder {

    private final HandlerList handlers = new HandlerList();

    /* Adds a static file server using a local directory as root. */
    public TestServerBuilder addDirectory(String path) {
        ResourceHandler handler = new ResourceHandler();
        handler.setResourceBase(path);
        handler.setWelcomeFiles(new String[] {"index.html"});
        handler.setDirectoriesListed(false);
        handlers.addHandler(handler);
        return this;
    }

    /* Adds a static file server using resource in this class loader
     * package identified as root. */
    public TestServerBuilder addPackage(String path) {
        addPackage(path, null);
        return this;
    }
    /* Adds a static file server using resource in provided class loader
     * package identified as root. */
    public TestServerBuilder addPackage(String path, ClassLoader classLoader) {
        ResourceHandler handler = new ResourceHandler();
        ClassLoader cl = classLoader;
        if (cl == null) {
            cl = TestServerBuilder.class.getClassLoader();
        }
        URL staticResources = cl.getResource(path);
        handler.setResourceBase(staticResources.toExternalForm());
        handler.setWelcomeFiles(new String[] {"index.html"});
        handler.setDirectoriesListed(false);
        handlers.addHandler(handler);
        return this;
    }


    //TODO test below
    public TestServerBuilder addServlet(Servlet servlet, String urlMapping) {
        WebAppContext wac = new WebAppContext();
        wac.setResourceBase("/");
        wac.addServlet(new ServletHolder(servlet), urlMapping);
        this.handlers.addHandler(wac);
        return this;
    }
    public TestServerBuilder addHandler(Handler handler) {
        this.handlers.addHandler(handler);
        return this;
    }

    public TestServerBuilder addFilter(Filter filter, String urlMapping) {
        WebAppContext wac = new WebAppContext();
        wac.setResourceBase("/");
        wac.addFilter(new FilterHolder(filter),
                urlMapping, EnumSet.allOf(DispatcherType.class));
        this.handlers.addHandler(wac);
        return this;
    }




    /*
    private WebAppContext buildWebappContext() {

        WebAppContext webappContext = new WebAppContext();
        webappContext.setResourceBase("/");

        // Add test servlet
        ServletHolder servletHolder = new ServletHolder(new TestServlet());
        webappContext.addServlet(servletHolder, "/test/*");

        // Add custom error message
        webappContext.setErrorHandler(new ErrorHandler() {
            @Override
            protected void writeErrorPageBody(
                    HttpServletRequest request,
                    Writer writer,
                    int code,
                    String message,
                    boolean showStacks) throws IOException {
                String uri= request.getRequestURI();
                writeErrorPageMessage(request, writer, code, message, uri);
                if (showStacks) {
                    writeErrorPageStacks(request,writer);
                }
                writer.write("<hr><i><small>Norconex HTTP Collector Test "
                        + "Web Server Error Page</small></i><hr/>\n");
            }
        });

        return webappContext;
    }
    */

    public TestServer build() {
        return new TestServer(handlers);
    }
}
