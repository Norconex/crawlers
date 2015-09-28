/* Copyright 2015 Norconex Inc.
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

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * <p>This class is used by each crawler instance to wraps the original redirect
 * strategy set on the HttpClient to make sure redirect
 * targets are treated as new URLs to potentially process while the original 
 * URL gets rejected.
 * </p>
 * @author Pascal Essiembre
 * @since 2.3.0
 */
public class HttpCrawlerRedirectStrategy implements RedirectStrategy {
    
    private static final Logger LOG = 
            LogManager.getLogger(HttpCrawlerRedirectStrategy.class);

    private static final ThreadLocal<String> REDIRECT_REF = new ThreadLocal<>();

    private final RedirectStrategy nested;
    
    public HttpCrawlerRedirectStrategy(RedirectStrategy nested) {
        super();
        this.nested = nested;
    }
    
    public static String getRedirectTargetReference() {
        return REDIRECT_REF.get();
    }
    
    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        REDIRECT_REF.set(null);
        boolean isRedirected = nested.isRedirected(
                request, response, context);
        HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute( 
                HttpCoreContext.HTTP_REQUEST);
        HttpHost currentHost = (HttpHost)  context.getAttribute( 
                HttpCoreContext.HTTP_TARGET_HOST);
        
        if (isRedirected) {
            Header h = response.getLastHeader("Location");
            String originalURL = toAbsoluteURI(currentHost, currentReq);
            String targetURL = null;
            if (h == null) {
                //TODO should throw exception instead?
                LOG.error("Redirect detected to a null Location for: " 
                        + toAbsoluteURI(currentHost, currentReq));
                return false;
            }

            targetURL = h.getValue();

            if (LOG.isDebugEnabled()) {
                LOG.debug("URL redirect: " + originalURL + " -> " + targetURL);
            }
            REDIRECT_REF.set(targetURL);
            return false;
        }
        return isRedirected;
    }

    private String toAbsoluteURI(HttpHost host, HttpUriRequest req) {
        // Check if we can get full URL from a nested request, to keep
        // the #fragment, if present.
        if (req instanceof HttpRequestWrapper) {
            HttpRequest originalReq = ((HttpRequestWrapper) req).getOriginal();
            if (originalReq instanceof HttpRequestBase) {
                return ((HttpRequestBase) originalReq).getURI().toString();
            }
        }
        
        // Else, built it
        if (req.getURI().isAbsolute()) {
            return req.getURI().toString();
        }
        return host.toURI() + req.getURI();
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
}
