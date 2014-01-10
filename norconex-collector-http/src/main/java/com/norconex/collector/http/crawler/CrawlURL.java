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


/**
 * A URL being crawled holding relevant crawl information.
 * @author Pascal Essiembre
 */
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
    

    /**
     * Constructor.
     * @param url URL being crawled
     * @param depth URL depth
     */
    public CrawlURL(String url, int depth) {
        super();
        setUrl(url);
        setDepth(depth);
    }

    /**
     * Gets the URL depth.
     * @return URL depth
     */
    public int getDepth() {
        return depth;
    }
    /**
     * Gets the URL.
     * @return url
     */
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
    
    /**
     * Gets the sitemap change frequency.
     * @return sitemap change frequency
     */
    public String getSitemapChangeFreq() {
        return sitemapChangeFreq;
    }
    /**
     * Sets the sitemap change frequency.
     * @param sitemapChangeFreq sitemap change frequency
     */
    public void setSitemapChangeFreq(String sitemapChangeFreq) {
        this.sitemapChangeFreq = sitemapChangeFreq;
    }
    
    /**
     * Gets the sitemap priority.
     * @return sitemap priority
     */
    public Float getSitemapPriority() {
        return sitemapPriority;
    }
    /**
     * Sets the sitemap priority.
     * @param sitemapPriority sitemap priority
     */
    public void setSitemapPriority(Float sitemapPriority) {
        this.sitemapPriority = sitemapPriority;
    }

    /**
     * Sets the URL depth.
     * @param depth URL depth
     */
    public final void setDepth(int depth) {
        this.depth = depth;
    }
    /**
     * Sets the URL.
     * @param url the url
     */
    public final void setUrl(String url) {
        this.url = url;
        if (url != null) {
            this.urlRoot = url.replaceFirst("(.*?://.*?)(/.*)", "$1");
        } else {
            this.urlRoot = null;
        }
    }
    /**
     * Gets the URL root (protocol + domain, e.g. http://www.host.com).
     * @return URL root
     */
    public String getUrlRoot() {
        return urlRoot;
    }
    
    /**
     * Gets the current crawl status.
     * @return crawl status
     */
    public CrawlStatus getStatus() {
        return status;
    }
    /**
     * Sets the current crawl status.
     * @param status crawl status
     */
    public void setStatus(CrawlStatus status) {
        this.status = status;
    }
    
    /**
     * Gets the HTTP header checksum.
     * @return the HTTP header checksum
     */
    public String getHeadChecksum() {
        return headChecksum;
    }
    /**
     * Sets the HTTP header checksum.
     * @param headChecksum the HTTP header checksum
     */
    public void setHeadChecksum(String headChecksum) {
        this.headChecksum = headChecksum;
    }

    /**
     * Gets the document checksum.
     * @return document checksum
     */
    public String getDocChecksum() {
        return docChecksum;
    }
    /**
     * Sets the document checksum.
     * @param docChecksum document checksum
     */
    public void setDocChecksum(String docChecksum) {
        this.docChecksum = docChecksum;
    }
    
    /**
     * Clones this instance, without any risk of exceptions being thrown.
     * @return a clone of this instance
     */
    public CrawlURL safeClone() {
        CrawlURL c = new CrawlURL(url, depth);
        c.setDocChecksum(docChecksum);
        c.setHeadChecksum(headChecksum);
        c.setSitemapChangeFreq(sitemapChangeFreq);
        c.setSitemapLastMod(sitemapLastMod);
        c.setSitemapPriority(sitemapPriority);
        c.setStatus(status);
        return c;
    }
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof CrawlURL)) {
            return false;
        }
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
