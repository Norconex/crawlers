/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.web.ledger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.ledger.CrawlEntry;

import lombok.Data;
import lombok.ToString;

/**
 * A URL being crawled holding relevant crawl information.
 */
@Data
public class WebCrawlEntry extends CrawlEntry {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private String urlRoot;

    /**
     * Whether this record was obtained from parsing a sitemap. Set
     * by the queue pipeline so implementors should not have to set
     * it themselves.
     */
    private boolean fromSitemap;

    /**
     * The document last modified date according to sitemap.
     */
    private ZonedDateTime sitemapLastMod;

    /**
     * The document change frequency according to sitemap.
     */
    private String sitemapChangeFreq;

    /**
     * The document priority according to sitemap.
     */
    private Float sitemapPriority;

    private String referrerReference;
    private String referrerLinkMetadata;

    /**
     * The HTTP ETag.
     * @since 3.0.0
     */
    private String etag;

    private final List<String> referencedUrls = new ArrayList<>();
    private final List<String> redirectTrail = new ArrayList<>();
    private String redirectTarget;

    /**
     * HTTP status code from the last fetch response.
     */
    private int httpStatusCode;
    /**
     * HTTP reason phrase from the last fetch response.
     */
    private String httpReasonPhrase;

    public WebCrawlEntry() {
    }

    /**
     * Copy constructor.
     * @param src the source to copy from
     */
    public WebCrawlEntry(WebCrawlEntry src) {
        setReference(src.getReference());
        setDepth(src.getDepth());
        setProcessingStatus(src.getProcessingStatus());
        setProcessingOutcome(src.getProcessingOutcome());
        setReferenceTrail(new ArrayList<>(src.getReferenceTrail()));
        setMetaChecksum(src.getMetaChecksum());
        setContentChecksum(src.getContentChecksum());
        setQueuedAt(src.getQueuedAt());
        setProcessingAt(src.getProcessingAt());
        setProcessedAt(src.getProcessedAt());
        setOrphan(src.isOrphan());
        setDeleted(src.isDeleted());
        fromSitemap = src.fromSitemap;
        sitemapLastMod = src.sitemapLastMod;
        sitemapChangeFreq = src.sitemapChangeFreq;
        sitemapPriority = src.sitemapPriority;
        referrerReference = src.referrerReference;
        referrerLinkMetadata = src.referrerLinkMetadata;
        etag = src.etag;
        httpStatusCode = src.httpStatusCode;
        httpReasonPhrase = src.httpReasonPhrase;
        setReferencedUrls(new ArrayList<>(src.referencedUrls));
        setRedirectTrail(new ArrayList<>(src.redirectTrail));
        redirectTarget = src.redirectTarget;
    }

    public WebCrawlEntry(String reference) {
        setReference(reference);
    }

    /**
     * Constructor.
     * @param url URL being crawled
     * @param depth URL depth
     */
    public WebCrawlEntry(String url, int depth) {
        setReference(url);
        setDepth(depth);
    }

    @Override
    public final void setReference(String url) {
        super.setReference(url);
        if (url != null) {
            urlRoot = HttpURL.getRoot(url);
        } else {
            urlRoot = null;
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
     * @return URLs referenced by this one (never {@code null}).
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
     * @return URL redirection trail to this one (never {@code null}).
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
}
