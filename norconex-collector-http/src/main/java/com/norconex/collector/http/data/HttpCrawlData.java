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
import com.norconex.commons.lang.url.HttpURL;


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
    
    private String[] referencedUrls;
    
    /**
     * Constructor.
     */
    public HttpCrawlData() {
        super();
    }
    /**
     * Constructor
     * @param crawlData initialized this instance this data
     */
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
            this.urlRoot = HttpURL.getRoot(url);
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
     * Gets URLs referenced by this one.
     * @return URLs referenced by this one.
     * @since 2.6.0
     */
    public String[] getReferencedUrls() {
        return referencedUrls;
    }
    /**
     * Sets URLs referenced by this one.
     * @param referencedUrls referenced URLs
     * @since 2.6.0 URLs referenced by this one.
     */
    public void setReferencedUrls(String... referencedUrls) {
        this.referencedUrls = referencedUrls;
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
                .append(referencedUrls, castOther.referencedUrls)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode())
                .append(depth)
                .append(urlRoot)
                .append(sitemapLastMod)
                .append(sitemapChangeFreq)
                .append(sitemapPriority)
                .append(originalReference)
                .append(referrerLinkText)
                .append(referrerReference)
                .append(referrerLinkTag)
                .append(referrerLinkTitle)
                .append(referencedUrls)
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
                .append("referencedUrls", referencedUrls)
                .toString();
    }
}
