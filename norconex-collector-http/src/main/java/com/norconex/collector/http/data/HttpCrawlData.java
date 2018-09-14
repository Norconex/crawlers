/* Copyright 2010-2018 Norconex Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.data.BaseCrawlData;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
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

    private final List<String> referencedUrls = new ArrayList<>();
    private final List<String> redirectTrail = new ArrayList<>();

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
            BeanUtil.copyProperties(this, crawlData);
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
     * @return URLs referenced by this one (never <code>null</code>).
     * @since 2.6.0
     */
    public List<String> getReferencedUrls() {
        return Collections.unmodifiableList(referencedUrls);
    }
    /**
     * Sets URLs referenced by this one.
     * @param referencedUrls referenced URLs
     * @since 2.6.0
     */
    public void setReferencedUrls(String... referencedUrls) {
        CollectionUtil.setAll(this.referencedUrls, referencedUrls);
    }
    /**
     * Sets URLs referenced by this one.
     * @param referencedUrls referenced URLs
     * @since 3.0.0
     */
    public void setReferencedUrls(List<String> referencedUrls) {
        CollectionUtil.setAll(this.referencedUrls, referencedUrls);
    }

    /**
     * Gets the trail of URLs that were redirected up to this one.
     * @return URL redirection trail to this one (never <code>null</code>).
     * @since 2.8.0
     */
    public List<String> getRedirectTrail() {
        return Collections.unmodifiableList(redirectTrail);
    }
    /**
     * Sets the trail of URLs that were redirected up to this one.
     * @param redirectTrail URL redirection trail to this one
     * @since 2.8.0
     */
    public void setRedirectTrail(String... redirectTrail) {
        CollectionUtil.setAll(this.redirectTrail, redirectTrail);
    }
    /**
     * Sets the trail of URLs that were redirected up to this one.
     * @param redirectTrail URL redirection trail to this one
     * @since 3.0.0
     */
    public void setRedirectTrail(List<String> redirectTrail) {
        CollectionUtil.setAll(this.redirectTrail, redirectTrail);
    }
    /**
     * Adds a redirect URL to the trail of URLs that were redirected so far.
     * @param url URL to add
     * @since 3.0.0
     */
    public void addRedirectURL(String url) {
        redirectTrail.add(url);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
