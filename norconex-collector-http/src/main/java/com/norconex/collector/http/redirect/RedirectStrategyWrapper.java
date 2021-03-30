/* Copyright 2015-2016 Norconex Inc.
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
package com.norconex.collector.http.redirect;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;

/**
 * <p>This class is used by each crawler instance to wrap the original redirect
 * strategy set on the {@link HttpClient} to make sure redirect
 * target URLs are handled as required.
 * Target URLs are treated as new URLs to potentially process,
 * while the original URL gets rejected.
 * </p>
 * @author Pascal Essiembre
 * @since 2.4.0
 */
public class RedirectStrategyWrapper implements RedirectStrategy {

    private static final ThreadLocal<String> REDIRECT_URL = new ThreadLocal<>();

    private final RedirectStrategy nested;
    private final IRedirectURLProvider redirectURLProvider;

    public RedirectStrategyWrapper(
            RedirectStrategy nested, IRedirectURLProvider redirectURLProvider) {
        super();
        this.nested = nested;
        this.redirectURLProvider = redirectURLProvider;
    }

    public static String getRedirectURL() {
        return REDIRECT_URL.get();
    }

    /**
     * Sets the redirect URL.  This method is normally never invoked unless
     * you cannot rely on the default crawler behavior to set it for you.
     * @param redirectUrl redirect URL
     * @since 2.7.0
     */
    public static void setRedirectURL(String redirectUrl) {
        REDIRECT_URL.set(redirectUrl);
    }

    /**
     * Gets the redirect URL provider.
     * @return the redirect URL provider
     */
    public IRedirectURLProvider getRedirectURLProvider() {
        return redirectURLProvider;
    }

    @Override
    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        REDIRECT_URL.remove();
        boolean isRedirected = nested.isRedirected(request, response, context);
        if (isRedirected) {
            String targetURL = redirectURLProvider.provideRedirectURL(
                    request, response, context);
            if (StringUtils.isNotBlank(targetURL)) {
                REDIRECT_URL.set(targetURL);
            }
            return false;
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
}
