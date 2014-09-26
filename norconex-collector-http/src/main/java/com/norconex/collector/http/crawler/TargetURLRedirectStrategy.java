/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.crawler;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doccrawl.HttpDocCrawl;

/**
 * <p>
 * This class handles HTTP redirects which by default ends up not not
 * storing the final redirect URL but the original one instead.  
 * </p>
 * <p>
 * This class is set in <code>HttpCrawler</code> after invoking the 
 * <code>IHttpClientInitializer</code> implementation.  It gets
 * used in <code>DocumentProcessor.DocumentFetcherStep</code>. <b>If you
 * create your own initializer</b> with your own 
 * <code>RedirectStrategy</code>, please not that it will transparently
 * be wrapped by this class.  If you need to access to your implementation,
 * later down in the processing flow, you can call the method 
 * <code>getOriginalRedirectStrategy</code> to obtain it. 
 * </p>
 * @author Pascal Essiembre
 * @since 1.1.1
 */
public class TargetURLRedirectStrategy implements RedirectStrategy {
    private static final ThreadLocal<String> CURRENT_URL = 
            new ThreadLocal<String>();
    private final RedirectStrategy nested;
    
    public TargetURLRedirectStrategy(RedirectStrategy nested) {
        super();
        this.nested = nested;
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
        if (!isRedirected) {
            if (currentReq.getURI().isAbsolute()) {
                CURRENT_URL.set(currentReq.getURI().toString());
            } else {
                CURRENT_URL.set(currentHost.toURI() + currentReq.getURI());
            }
        }
        return isRedirected;
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
    
    public static void fixRedirectURL(
            HttpClient httpClient, 
            HttpDocument doc,
            HttpDocCrawl httpDocCrawl,
            ICrawlDataStore database) {
        String originalURL = httpDocCrawl.getReference();
        String currentURL = getCurrentUrl();
        if (ObjectUtils.notEqual(currentURL, originalURL)) {
            httpDocCrawl.setOriginalReference(originalURL);
            httpDocCrawl.setReference(currentURL);
            doc.getMetadata().setString(HttpMetadata.COLLECTOR_URL, currentURL);
            doc.setReference(currentURL);
//            Unless the DocCrawl is enough to ensure proper storing + sending to committer?
//                     or... add a setNewURL() instead of storing original URL.
        }
    }
}
