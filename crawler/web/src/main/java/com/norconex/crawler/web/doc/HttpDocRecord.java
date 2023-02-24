/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.doc;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.importer.doc.DocRecord;

import lombok.Data;
import lombok.ToString;

/**
 * A URL being crawled holding relevant crawl information.
 */
@Data
public class HttpDocRecord extends CrawlDocRecord {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private String urlRoot;

    /**
     * The document last modified date according to sitemap.
     * @param sitemapLastMod document last modified date
     * @return document last modified date
     */
    @SuppressWarnings("javadoc")
    private ZonedDateTime sitemapLastMod;

    /**
     * The document change frequency according to sitemap.
     * @param sitemapChangeFreq document change frequency
     * @return document change frequency
     */
    @SuppressWarnings("javadoc")
    private String sitemapChangeFreq;

    /**
     * The document priority according to sitemap.
     * @param sitemapPriority document priority
     * @return document priority
     */
    @SuppressWarnings("javadoc")
    private Float sitemapPriority;

    private String referrerReference;
    private String referrerLinkMetadata;

    /**
     * The HTTP ETag.
     * @return etag
     * @param etag the HTTP ETag
     * @since 3.0.0
     */
    @SuppressWarnings("javadoc")
    private String etag;

    private final List<String> referencedUrls = new ArrayList<>();
    private final List<String> redirectTrail = new ArrayList<>();

    public HttpDocRecord() {
    }

    public HttpDocRecord(String reference) {
        super(reference);
    }

    /**
     * Constructor.
     * @param url URL being crawled
     * @param depth URL depth
     */
    public HttpDocRecord(String url, int depth) {
        super(url);
        setDepth(depth);
    }
    /**
     * Copy constructor.
     * @param docDetails document details to copy
     */
    public HttpDocRecord(DocRecord docDetails) {
        super(docDetails);
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
}
