/**
 * 
 */
package com.norconex.collector.http.website;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * @author Pascal Essiembre
 *
 */
public class TestWebServer {

    private final Server server = new Server();
    
    private int localPort;
    
    /**
     * Constructor.
     */
    public TestWebServer() {
        
        //initRuntimeConfiguration();
        
        WebAppContext webappContext = buildWebappContext();
        ResourceHandler staticHandler = buildStaticResourcesHandler();
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { staticHandler, webappContext });
        server.setHandler(handlers);
        
        initHttpConnector();
        
    }

    private WebAppContext buildWebappContext() {
        
        WebAppContext webappContext = new WebAppContext();
        webappContext.setResourceBase("/");
        
//        // Add Wicket filter
//        WicketFilter filter = new WicketFilter(app);
//        FilterHolder filterHolder = new FilterHolder(filter);
//        filterHolder.setInitParameter(
//                WicketFilter.FILTER_MAPPING_PARAM, ANALYTICS_MAPPING);
//        webappContext.addFilter(
//                filterHolder, 
//                ANALYTICS_MAPPING, 
//                EnumSet.of(DispatcherType.REQUEST));
        
        // Add test serlet
        ServletHolder servletHolder = new ServletHolder(new TestServlet());
        webappContext.addServlet(servletHolder, "/test");
        
        // Add custom error message
        webappContext.setErrorHandler(new ErrorHandler() {
            protected void writeErrorPageBody(
                    HttpServletRequest request, 
                    Writer writer, 
                    int code, 
                    String message, 
                    boolean showStacks) throws IOException {
                String uri= request.getRequestURI();
                writeErrorPageMessage(request,writer,code,message,uri);
                if (showStacks)
                    writeErrorPageStacks(request,writer);
                writer.write("<hr><i><small>Norconex HTTP Collector Test "
                        + "Server</small></i><hr/>\n");
            }
        });
        
        return webappContext;
    }
    
    private ResourceHandler buildStaticResourcesHandler() {
        ResourceHandler staticHandler = new ResourceHandler();
        URL staticResources = TestWebServer.class.getClassLoader().getResource(
                "website/static");
        staticHandler.setResourceBase(staticResources.toExternalForm());
        staticHandler.setWelcomeFiles(new String[] {"index.html"});
        staticHandler.setDirectoriesListed(false);
        return staticHandler;
    }
    
    private void initHttpConnector() {
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);
    }
    
    public void run() throws Exception {
        server.start();
        this.localPort = ((ServerConnector) 
                server.getConnectors()[0]).getLocalPort();
        System.out.println("Test website has successfully started on port " 
                + this.localPort);

        server.join();
    }
    
    public void stop() throws Exception {
        server.stop();
        server.join();
    }
    
    /**
     * @return the localPort
     */
    public int getLocalPort() {
        return localPort;
    }
    
    public static void main(String[] args) throws Exception {
        final TestWebServer server = new TestWebServer();
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
                    server.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
    }
}
