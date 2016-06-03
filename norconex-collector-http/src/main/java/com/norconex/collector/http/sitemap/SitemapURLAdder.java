/* Copyright 2010-2016 Norconex Inc.
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

import com.norconex.collector.http.data.HttpCrawlData;


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
public abstract class SitemapURLAdder {

    public static final Float DEFAULT_PRIORITY = 0.5f;
    
    public SitemapURLAdder() {
        super();
    }

    //TODO This method is not used, it is really convenient? Keep it around?
    public void add(String url, Long lastmod, 
            SitemapChangeFrequency changefreq, Float priority) {
        Float setPriority = priority;
        if (setPriority == null) {
            setPriority = DEFAULT_PRIORITY;
        }
        String setChangeFreq = null;
        if (changefreq != null) {
            setChangeFreq = changefreq.toString().toLowerCase();
        }
        HttpCrawlData baseURL = new HttpCrawlData(url, 0);
        baseURL.setSitemapChangeFreq(setChangeFreq);
        baseURL.setSitemapLastMod(lastmod);
        baseURL.setSitemapPriority(setPriority);
        add(baseURL);
    }
    
    public abstract void add(HttpCrawlData baseURL);
}
