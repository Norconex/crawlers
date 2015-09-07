/* Copyright 2010-2015 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.pipeline.importer.HttpImporterPipeline;
import com.norconex.commons.lang.url.HttpURL;

/**
 * <p>
 * This class handles HTTP redirects which by default ends up not
 * storing the final redirect URL but the original one instead.  
 * </p>
 * <p>
 * By default, this class is used by the {@link GenericHttpClientFactory}. Its
 * {@link #fixRedirectURL(HttpClient, HttpDocument, HttpCrawlData, ICrawlDataStore)}
 * method gets invoked right after a document is fetched in
 * the {@link HttpImporterPipeline}.
 * </p>
 * <p>
 * The constructor supports a flag to ignore external redirects.  An external
 * redirect is a redirected having a different host, port, or scheme than
 * the original one.
 * </p>
 * @author Pascal Essiembre
 * @since 1.1.1
 */
public class TargetURLRedirectStrategy implements RedirectStrategy {
    
    private static final Logger LOG = 
            LogManager.getLogger(TargetURLRedirectStrategy.class);
    
    private static final ThreadLocal<String> CURRENT_URL = 
            new ThreadLocal<String>();
    private final RedirectStrategy nested;
    private final boolean ignoreExternalRedirects;
    
    /**
     * Constructor.
     * @param nested original redirect strategy
     */
    public TargetURLRedirectStrategy(RedirectStrategy nested) {
        this(nested, false);
    }
    /**
     * Constructor.
     * @param nested original redirect strategy
     * @param ignoreExternalRedirects whether to ignore external redirects
     * @since 2.3.0
     */
    public TargetURLRedirectStrategy(
            RedirectStrategy nested, boolean ignoreExternalRedirects) {
        super();
        this.nested = nested;
        this.ignoreExternalRedirects = ignoreExternalRedirects;
    }
    
    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        boolean isRedirected = nested.isRedirected(
                request, response, context);
        HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute( 
                HttpCoreContext.HTTP_REQUEST);
        HttpHost currentHost = (HttpHost)  context.getAttribute( 
                HttpCoreContext.HTTP_TARGET_HOST);
        
        // If ignoring external redirects, prevent redirecting to one
        if (ignoreExternalRedirects && isRedirected) {
            String location = null;
            Header h = response.getLastHeader("Location");
            if (h != null) {
                location = h.getValue();
            }
            if (!isLocalRedirect(currentHost, location)) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Ignoring external redirect: "
                            + toAbsoluteURI(currentHost, currentReq)
                            + " -> " + location);
                }
                isRedirected = false;                
            }
        }

        // If not a redirect (or ignoring), store current URL info for later.
        if (!isRedirected) {
            CURRENT_URL.set(toAbsoluteURI(currentHost, currentReq));
        }
        return isRedirected;
    }

    private String toAbsoluteURI(HttpHost host, HttpUriRequest req) {
        if (req.getURI().isAbsolute()) {
            return req.getURI().toString();
        }
        return host.toURI() + req.getURI();
    }
    
    private boolean isLocalRedirect(HttpHost host, String redirectLocation) {
        if (StringUtils.isBlank(redirectLocation)) {
            return true;
        }
        HttpURL redirect = new HttpURL(redirectLocation);
        if (redirect == null || StringUtils.isBlank(redirect.getProtocol())) {
            return true;
        }
        return host.getHostName().equalsIgnoreCase(redirect.getHost())
                && host.getSchemeName().equalsIgnoreCase(redirect.getProtocol())
                && host.getPort() == redirect.getPort();
    }
    
    @Override
    public HttpUriRequest getRedirect(HttpRequest request,
            HttpResponse response, HttpContext context)
            throws ProtocolException {
        return nested.getRedirect(request, response, context);
    }
    
    public RedirectStrategy getOriginalRedirectStrategy() {
        return nested;
    }

    public static String getCurrentUrl() {
        return CURRENT_URL.get();
    }
    
    /**
     * Whether external redirects are being ignored
     * @return <code>true</code> when ignored
     * @since 2.3.0
     */
    public boolean isIgnoreExternalRedirects() {
        return ignoreExternalRedirects;
    }
    
    public static void fixRedirectURL(
            HttpClient httpClient, 
            HttpDocument doc,
            HttpCrawlData httpCrawlData,
            ICrawlDataStore database) {
        //TODO can we store the required object in the HTTPContext instead
        //and perform this fix in isRedirected(), then drop this method?
        String originalURL = httpCrawlData.getReference();
        String currentURL = getCurrentUrl();
        if (StringUtils.isNotBlank(currentURL) 
                && ObjectUtils.notEqual(currentURL, originalURL)) {
            httpCrawlData.setOriginalReference(originalURL);
            httpCrawlData.setReference(currentURL);
            doc.getMetadata().setString(HttpMetadata.COLLECTOR_URL, currentURL);
            doc.setReference(currentURL);
            if (LOG.isInfoEnabled()) {
                LOG.info("URL Redirect: " + originalURL + " -> " + currentURL);
            }
        }
    }
}
