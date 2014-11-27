/* Copyright 2010-2014 Norconex Inc.
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
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;

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
            HttpCrawlData httpCrawlData,
            ICrawlDataStore database) {
        String originalURL = httpCrawlData.getReference();
        String currentURL = getCurrentUrl();
        if (ObjectUtils.notEqual(currentURL, originalURL)) {
            httpCrawlData.setOriginalReference(originalURL);
            httpCrawlData.setReference(currentURL);
            doc.getMetadata().setString(HttpMetadata.COLLECTOR_URL, currentURL);
            doc.setReference(currentURL);
//            Unless the DocCrawl is enough to ensure proper storing + sending to committer?
//                     or... add a setNewURL() instead of storing original URL.
        }
    }
}
