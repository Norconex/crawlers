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
package com.norconex.collector.http.sitemap;

import org.apache.http.client.HttpClient;


/**
 * Given a URL root, resolve the corresponding sitemap(s), if any, and 
 * only if it has not yet been resolved for a crawling session.
 * 
 * @author Pascal Essiembre
 */
public interface ISitemapResolver {

    /**
     * Resolves the sitemap instructions for a URL "root" (e.g. 
     * http://www.example.com).
     * @param httpClient the http client to use to stream Internet 
     *        files if needed
     * @param urlRoot the URL root for which to resolve the sitemap
     * @param robotsTxtLocations sitemap locations specified in robots.txt
     *        (provided robots are not ignored)
     * @param sitemapURLAdder where to store retrieved site map URLs
     */
    void resolveSitemaps(HttpClient httpClient, String urlRoot, 
            String[] robotsTxtLocations, SitemapURLAdder sitemapURLAdder);
    
    /**
     * Stops any ongoing sitemap resolution.  Some sitemaps can be huge, 
     * and they may take a while to process.  Upon the crawler receiving a 
     * stop request, this method will be invoked and implementors should
     * try to exit cleanly without much delay.
     */
    void stop();
}
