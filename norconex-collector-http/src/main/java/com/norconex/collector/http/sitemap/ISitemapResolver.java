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
    
}
