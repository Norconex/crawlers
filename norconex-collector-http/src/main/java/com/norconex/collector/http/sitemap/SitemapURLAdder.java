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

import com.norconex.collector.http.crawler.HttpDocReference;


// THIS is a wrapper class where add( ) will add a URL found in sitemap file
// into reference Store for processing.


/**
 * <p>Represents a queue of sitemap URLs.  Adding a URL to this queue will
 * effectively mark it for processing.</p>
 * <p>
 * Default priority value and allowed change frequency values are taken from
 * <a href="http://www.sitemaps.org/protocol.html">
 * http://www.sitemaps.org/protocol.html</a></p>
 * @author Pascal Essiembre
 * @see ISitemapResolver
 */
public abstract class SitemapURLAdder implements Serializable {

    private static final long serialVersionUID = 1504818537850961659L;

    public enum ChangeFrequency {
        ALWAYS,
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY,
        NEVER
    }
    public static final Float DEFAULT_PRIORITY = 0.5f;
    
    public SitemapURLAdder() {
        super();
    }

    public void add(String url, Long lastmod, 
            ChangeFrequency changefreq, Float priority) {
        Float setPriority = priority;
        if (setPriority == null) {
            setPriority = DEFAULT_PRIORITY;
        }
        String setChangeFreq = null;
        if (changefreq != null) {
            setChangeFreq = changefreq.toString().toLowerCase();
        }
        HttpDocReference baseURL = new HttpDocReference(url, 0);
        baseURL.setSitemapChangeFreq(setChangeFreq);
        baseURL.setSitemapLastMod(lastmod);
        baseURL.setSitemapPriority(setPriority);
        add(baseURL);
    }
    
    public abstract void add(HttpDocReference baseURL);
}
