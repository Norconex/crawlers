/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.doc;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.builder.ToStringSummary;

import com.norconex.collector.core.doc.CrawlDocInfo;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.importer.doc.DocInfo;

/**
 * A URL being crawled holding relevant crawl information.
 * @author Pascal Essiembre
 */
public class HttpDocInfo extends CrawlDocInfo {


    private static final long serialVersionUID = -2219206220476107409L;

    private int depth;
    @ToStringExclude
    private String urlRoot;
    private ZonedDateTime sitemapLastMod;
    private String sitemapChangeFreq;
    private Float sitemapPriority;
    private String originalReference; //TODO keep the trail if it changes often?
    private String referrerReference;
    private String referrerLinkMetadata;

    @ToStringSummary
    private final List<String> referencedUrls = new ArrayList<>();
    @ToStringSummary
    private final List<String> redirectTrail = new ArrayList<>();

    public HttpDocInfo() {
        super();
    }

    public HttpDocInfo(String reference) {
        super(reference);
    }

    /**
     * Constructor.
     * @param url URL being crawled
     * @param depth URL depth
     */
    public HttpDocInfo(String url, int depth) {
        super(url);
        setDepth(depth);
    }
    /**
     * Copy constructor.
     * @param docDetails document details to copy
     */
    public HttpDocInfo(DocInfo docDetails) {
        super(docDetails);
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
     * Gets the sitemap last modified date.
     * @return last modified date
     */
    public ZonedDateTime getSitemapLastMod() {
        return sitemapLastMod;
    }
    /**
     * Sets the sitemap last modified date.
     * @param sitemapLastMod last modified date
     */
    public void setSitemapLastMod(ZonedDateTime sitemapLastMod) {
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

    public String getReferrerReference() {
        return referrerReference;
    }
    public void setReferrerReference(String referrerReference) {
        this.referrerReference = referrerReference;
    }

    public String getReferrerLinkMetadata() {
        return referrerLinkMetadata;
    }

    public void setReferrerLinkMetadata(String referrerLinkMetadata) {
        this.referrerLinkMetadata = referrerLinkMetadata;
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
        ReflectionToStringBuilder b = new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE);
        b.setExcludeNullValues(true);
        return b.toString();
    }
}
