/* Copyright 2015-2020 Norconex Inc.
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
package com.norconex.collector.http.fetch.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * <p>This class is used by each crawler instance to capture the closest
 * redirect target whether it is part of a redirect chain or not.
 * Target URLs are treated as new URLs to potentially process,
 * while the original URL gets rejected.
 * </p>
 * @author Pascal Essiembre
 * @since 3.0.0 (adapted from v2.x RedirectStrategyWrapper)
 */
public class ApacheRedirectCaptureStrategy extends LaxRedirectStrategy {

    public static final String TARGET_REDIRECT_CONTEXT_KEY =
            ApacheRedirectCaptureStrategy.class.getName() + ".targetRedirect";


    private final IRedirectURLProvider redirectURLProvider;


    public ApacheRedirectCaptureStrategy(
            IRedirectURLProvider redirectURLProvider) {
        super();
        this.redirectURLProvider = redirectURLProvider;
    }

    // Here we always return false since we are not following redirects
    // right away. They are queued for separate processing instead.
    @Override
    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {

        //TODO check for max redirects here using config max redirect setting?

        boolean isRedirected = super.isRedirected(request, response, context);
        if (isRedirected) {
            String targetURL = redirectURLProvider.provideRedirectURL(
                    request, response, context);
            if (StringUtils.isNotBlank(targetURL)) {
                context.setAttribute(TARGET_REDIRECT_CONTEXT_KEY, targetURL);
            }
        }
        return false;
    }

    public static String getRedirectTarget(HttpContext context) {
        if (context != null) {
            return (String) context.getAttribute(TARGET_REDIRECT_CONTEXT_KEY);
        }
        return null;
    }

}
