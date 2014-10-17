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
package com.norconex.collector.http.data;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.BaseCrawlData;
import com.norconex.collector.core.data.ICrawlData;


/**
 * A URL being crawled holding relevant crawl information.
 * @author Pascal Essiembre
 */
public class HttpCrawlData extends BaseCrawlData {

    private static final long serialVersionUID = -2219206220476107409L;

    private int depth;
    private String urlRoot;
    private Long sitemapLastMod;
    private String sitemapChangeFreq;
    private Float sitemapPriority;
    private String originalReference;
    private String referrerLinkText;
    private String referrerReference;
    private String referrerLinkTag;
    private String referrerLinkTitle;
    
    public HttpCrawlData() {
        super();
    }

    public HttpCrawlData(ICrawlData crawlData) {
        if (crawlData != null) {
            try {
                BeanUtils.copyProperties(this, crawlData);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new CollectorException(e);
            }
        }
    }
    
    /**
     * Constructor.
     * @param url URL being crawled
     * @param depth URL depth
     */
    public HttpCrawlData(String url, int depth) {
        super();
        setReference(url);
        setDepth(depth);
    }

    
    public String getOriginalReference() {
        return originalReference;
    }
    public void setOriginalReference(String originalReference) {
        this.originalReference = originalReference;
    }

    /**
     * Gets the URL depth.
     * @return URL depth
     */
    public int getDepth() {
        return depth;
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
    
    public String getReferrerLinkText() {
        return referrerLinkText;
    }
    public void setReferrerLinkText(String referrerLinkText) {
        this.referrerLinkText = referrerLinkText;
    }

    public String getReferrerReference() {
        return referrerReference;
    }
    public void setReferrerReference(String referrerReference) {
        this.referrerReference = referrerReference;
    }

    public String getReferrerLinkTag() {
        return referrerLinkTag;
    }
    public void setReferrerLinkTag(String referrerLinkTag) {
        this.referrerLinkTag = referrerLinkTag;
    }
    
    public String getReferrerLinkTitle() {
        return referrerLinkTitle;
    }
    public void setReferrerLinkTitle(String referrerLinkTitle) {
        this.referrerLinkTitle = referrerLinkTitle;
    }

    @Override
    public final void setReference(String url) {
        super.setReference(url);
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

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof HttpCrawlData)) {
            return false;
        }
        HttpCrawlData castOther = (HttpCrawlData) other;
        return new EqualsBuilder().appendSuper(super.equals(other))
                .append(depth, castOther.depth)
                .append(urlRoot, castOther.urlRoot)
                .append(sitemapLastMod, castOther.sitemapLastMod)
                .append(sitemapChangeFreq, castOther.sitemapChangeFreq)
                .append(sitemapPriority, castOther.sitemapPriority)
                .append(originalReference, castOther.originalReference)
                .append(referrerLinkText, castOther.referrerLinkText)
                .append(referrerReference, castOther.referrerReference)
                .append(referrerLinkTag, castOther.referrerLinkTag)
                .append(referrerLinkTitle, castOther.referrerLinkTitle)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode())
                .append(depth).append(urlRoot).append(sitemapLastMod)
                .append(sitemapChangeFreq).append(sitemapPriority)
                .append(originalReference).append(referrerLinkText)
                .append(referrerReference).append(referrerLinkTag)
                .append(referrerLinkTitle)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString()).append("depth", depth)
                .append("urlRoot", urlRoot)
                .append("sitemapLastMod", sitemapLastMod)
                .append("sitemapChangeFreq", sitemapChangeFreq)
                .append("sitemapPriority", sitemapPriority)
                .append("originalReference", originalReference)
                .append("referrerLinkText", referrerLinkText)
                .append("referrerReference", referrerReference)
                .append("referrerLinkTag", referrerLinkTag)
                .append("referrerLinkTitle", referrerLinkTitle)
                .toString();
    }
}
