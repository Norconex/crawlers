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

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.joda.time.DateTime;

public class BaseURL implements Serializable {

    private static final long serialVersionUID = -3751624054556982365L;

    private int depth;
    private String url;
    private String urlRoot;
    private DateTime sitemapLastMod;
    private String sitemapChangeFreq;
    private Float sitemapPriority;
    
    public BaseURL(String url, int depth) {
        super();
        setUrl(url);
        setDepth(depth);
    }
    public int getDepth() {
        return depth;
    }
    public String getUrl() {
        return url;
    }
    public DateTime getSitemapLastMod() {
        return sitemapLastMod;
    }
    public void setSitemapLastMod(DateTime sitemapLastMod) {
        this.sitemapLastMod = sitemapLastMod;
    }
    public String getSitemapChangeFreq() {
        return sitemapChangeFreq;
    }
    public void setSitemapChangeFreq(String sitemapChangeFreq) {
        this.sitemapChangeFreq = sitemapChangeFreq;
    }
    public Float getSitemapPriority() {
        return sitemapPriority;
    }
    public void setSitemapPriority(Float sitemapPriority) {
        this.sitemapPriority = sitemapPriority;
    }
    public final void setDepth(int depth) {
        this.depth = depth;
    }
    public final void setUrl(String url) {
        this.url = url;
        if (url != null) {
            this.urlRoot = url.replaceFirst("(.*?://.*?)(/.*)", "$1");
        } else {
            this.urlRoot = null;
        }
    }
    public String getUrlRoot() {
        return urlRoot;
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(depth)
            .append(url)
            .append(sitemapPriority)
            .append(sitemapChangeFreq)
            .append(sitemapLastMod)
            .toHashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof BaseURL)) {
            return false;
        }
        BaseURL other = (BaseURL) obj;
        return new EqualsBuilder()
            .append(depth, other.depth)
            .append(url, other.url)
            .append(sitemapPriority, other.sitemapPriority)
            .append(sitemapChangeFreq, other.sitemapChangeFreq)
            .append(sitemapLastMod, other.sitemapLastMod)
            .isEquals();
    }
    @Override
    public String toString() {
        return "CrawlURL [depth=" + depth + ", url=" + url
                + ", sitemapLastMod=" + sitemapLastMod
                + ", sitemapChangeFreq=" + sitemapChangeFreq
                + ", sitemapPriority=" + sitemapPriority + "]";
    }
}
