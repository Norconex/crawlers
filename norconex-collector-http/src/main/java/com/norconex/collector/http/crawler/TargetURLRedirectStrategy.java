/* Copyright 2010-2013 Norconex Inc.
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
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;

/**
 * <p>
 * This class is a hot fix for github issue #17 where HTTP redirects does not
 * store target URL but the source instead.  <b>It should be considered 
 * a temporary solution and assume this class will disappear.</b>   
 * A better solution would involve breaking an
 * interface method signature.  A more permanent fix should be put in place
 * in <code>DefaultHttpDocumentFetcher</code> once we are in a position to 
 * make non-backward compatible changes (new minor/major release).
 * </p>
 * <p>
 * This class is set in <code>HttpCrawler</code> after invoking the 
 * <code>IHttpClientInitializer</code> implementation.  It gets
 * used in <code>DocumentProcessor.DocumentFetcherStep</code>. <b>If you
 * create your own initializer</b> with your own 
 * <code>RedirectStrategy</code>, please not that it will transparently
 * be wrapped by this class.  If you need to access to your implementation,
 * later down in the processing flow, you can call the method 
 * <code>getOriginalRedirectStrategy</code> to obtain it. Again, be
 * warned this wrapper class will disappear.
 * </p>
 * @author Pascal Essiembre
 * @since 1.1.1
 */
public class TargetURLRedirectStrategy implements RedirectStrategy {
    private ThreadLocal<String> currentURL = new ThreadLocal<String>();
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
                ExecutionContext.HTTP_REQUEST);
        HttpHost currentHost = (HttpHost)  context.getAttribute( 
                ExecutionContext.HTTP_TARGET_HOST);
        if (!isRedirected) {
            if (currentReq.getURI().isAbsolute()) {
                currentURL.set(currentReq.getURI().toString());
            } else {
                currentURL.set(currentHost.toURI() + currentReq.getURI());
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

    public String getCurrentUrl() {
        return currentURL.get();
    }
    
    public static void fixRedirectURL(
            DefaultHttpClient httpClient, 
            HttpDocument doc,
            CrawlURL crawlURL,
            ICrawlURLDatabase database) {
        RedirectStrategy aStrategy = httpClient.getRedirectStrategy();
        if (aStrategy instanceof TargetURLRedirectStrategy) {
            TargetURLRedirectStrategy s = (TargetURLRedirectStrategy) aStrategy;
            String originalURL = crawlURL.getUrl();
            String currentURL = s.getCurrentUrl();
            if (ObjectUtils.notEqual(currentURL, originalURL)) {
                crawlURL.setUrl(currentURL);
                doc.getMetadata().setString(HttpMetadata.DOC_URL, currentURL);
            }
        }
    }
}
