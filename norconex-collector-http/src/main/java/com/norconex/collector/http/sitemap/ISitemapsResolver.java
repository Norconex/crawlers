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
package com.norconex.collector.http.sitemap;

import java.io.Serializable;

import org.apache.http.client.HttpClient;


/**
 * Given a URL root, return the resolve the corresponding sitemap, if any.
 * @author Pascal Essiembre
 */
public interface ISitemapsResolver extends Serializable {

    /**
     * Resolves the sitemap instructions for a URL "root" (e.g. 
     * http://www.example.com).
     * @param httpClient the http client to use to stream Internet 
     *        files if needed
     * @param urlRoot the URL root for which to resolve the sitemap
     * @param robotsTxtLocations sitemap locations specified in robots.txt
     *        (provided robots are not ignored)
     * @param sitemapURLStore store holding retreived site maps
     */
    void resolveSitemaps(HttpClient httpClient, String urlRoot, 
            String[] robotsTxtLocations, SitemapURLStore sitemapURLStore);
    
    /**
     * Stops any ongoing sitemap resolution.  Some sitemaps can be huge, 
     * and they may take a while to process.  Upon the crawler receiving a 
     * stop request, this method will be invoked and implementors should
     * try to exit cleanly without much delay.
     */
    void stop();
}
