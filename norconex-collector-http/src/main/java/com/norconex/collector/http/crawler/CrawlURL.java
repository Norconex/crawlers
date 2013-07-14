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
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;


public class CrawlURL implements Serializable {

    private static final long serialVersionUID = -2219206220476107409L;

    private int depth;
    private String url;
    private String urlRoot;
    private Long sitemapLastMod;
    private String sitemapChangeFreq;
    private Float sitemapPriority;
    
    private CrawlStatus status;
    private String headChecksum;
    private String docChecksum;
    

    public CrawlURL(String url, int depth) {
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
    /**
     * Gets the sitemap last modified date in milliseconds (EPOCH date).
     * @return date as long
     */
    public Long getSitemapLastMod() {
        return sitemapLastMod;
    }
    /**
     * Sets the sitemap last modified date in milliseconds (EPOCH date).
     * @param sitemapLastMod date as long
     */
    public void setSitemapLastMod(Long sitemapLastMod) {
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
    public CrawlStatus getStatus() {
        return status;
    }
    public void setStatus(CrawlStatus status) {
        this.status = status;
    }
    public String getHeadChecksum() {
        return headChecksum;
    }
    public void setHeadChecksum(String headChecksum) {
        this.headChecksum = headChecksum;
    }
    public String getDocChecksum() {
        return docChecksum;
    }
    public void setDocChecksum(String docChecksum) {
        this.docChecksum = docChecksum;
    }
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof CrawlURL))
            return false;
        CrawlURL castOther = (CrawlURL) other;
        return new EqualsBuilder().append(depth, castOther.depth)
                .append(url, castOther.url).append(urlRoot, castOther.urlRoot)
                .append(sitemapLastMod, castOther.sitemapLastMod)
                .append(sitemapChangeFreq, castOther.sitemapChangeFreq)
                .append(sitemapPriority, castOther.sitemapPriority)
                .append(status, castOther.status)
                .append(headChecksum, castOther.headChecksum)
                .append(docChecksum, castOther.docChecksum).isEquals();
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(depth).append(url).append(urlRoot)
                .append(sitemapLastMod).append(sitemapChangeFreq)
                .append(sitemapPriority).append(status).append(headChecksum)
                .append(docChecksum).toHashCode();
    }
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SIMPLE_STYLE)
                .append("depth", depth).append("url", url)
                .append("urlRoot", urlRoot)
                .append("sitemapLastMod", sitemapLastMod)
                .append("sitemapChangeFreq", sitemapChangeFreq)
                .append("sitemapPriority", sitemapPriority)
                .append("status", status).append("headChecksum", headChecksum)
                .append("docChecksum", docChecksum).toString();
    }
    
    
}
