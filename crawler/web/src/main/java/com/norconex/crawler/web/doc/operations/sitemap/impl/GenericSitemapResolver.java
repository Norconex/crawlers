/* Copyright 2019-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.sitemap.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.hc.core5.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.CrawlerLifeCycleListener;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.sitemap.SitemapContext;
import com.norconex.crawler.web.doc.operations.sitemap.SitemapRecord;
import com.norconex.crawler.web.doc.operations.sitemap.SitemapResolver;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Slf4j
public class GenericSitemapResolver extends CrawlerLifeCycleListener
        implements SitemapResolver, Configurable<GenericSitemapResolverConfig> {

    @JsonIgnore
    static final String SITEMAP_STORE_NAME =
            SitemapRecord.class.getSimpleName();
    @JsonIgnore
    private GridMap<SitemapRecord> sitemapStore;
    @JsonIgnore
    private final MutableBoolean stopping = new MutableBoolean();

    @EqualsAndHashCode.Include
    @ToString.Include
    @Getter
    private final GenericSitemapResolverConfig configuration =
            new GenericSitemapResolverConfig();

    @Override
    public void resolve(SitemapContext ctx) {
        var location = ctx.getLocation();
        if (stopping.isTrue()) {
            LOG.debug(
                    "Skipping resolution of sitemap "
                            + "location (stop requested): {}",
                    location);
            return;
        }
        doResolve(ctx, new HashSet<>());
        //TODO log info about resolution?
    }

    private void doResolve(SitemapContext ctx, Set<String> resolvedIndices) {

        var location = ctx.getLocation();

        // TODO Delete stored sitemaps that were not updated in session (orphans)

        List<SitemapRecord> childSitemaps = new ArrayList<>();
        var sitemapDoc = new CrawlDoc(new WebCrawlDocContext(location));
        SitemapRecord sitemapRec = null;

        try {
            var fetcher = ctx.getFetcher();
            LOG.info("Resolving sitemap: {}", location);
            // Execute the method.
            var response = httpGet(fetcher, sitemapDoc);
            sitemapRec = SitemapUtil.toSitemapRecord(sitemapDoc);
            var statusCode = response.getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                childSitemaps.addAll(
                        processFetchedSitemap(
                                ctx, sitemapRec, sitemapDoc));
                LOG.info("         Resolved: {}", location);
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                LOG.debug("Sitemap not found : {}", location);
            } else {
                LOG.error(
                        "Could not obtain sitemap: {}. Expected status "
                                + "code {}, but got {}.",
                        location, HttpStatus.SC_OK, statusCode);
            }
        } catch (Exception e) {
            LOG.error(
                    "Cannot fetch sitemap: {} ({})",
                    location, e.getMessage(), e);
        } finally {
            if (sitemapRec != null) {
                sitemapStore.put(location, sitemapRec);
            }
            try {
                sitemapDoc.dispose();
            } catch (IOException e) {
                LOG.error(
                        "Could not dispose of sitemap file for: {}",
                        location, e);
            }
        }

        for (SitemapRecord child : childSitemaps) {
            if (!resolvedIndices.contains(child.getLocation())) {
                doResolve(
                        ctx.withLocation(child.getLocation()), resolvedIndices);
            } else {
                LOG.debug("Sitemap already processed: {}", child.getLocation());
            }
        }
    }

    private List<SitemapRecord> processFetchedSitemap(
            SitemapContext ctx, SitemapRecord sitemapRec, CrawlDoc sitemapDoc)
            throws IOException {
        var location = sitemapDoc.getReference();
        var cachedRec = sitemapStore.get(location);

        if (!SitemapUtil.shouldProcessSitemap(sitemapRec, cachedRec)) {
            LOG.info("Sitemap not modified since last crawl: {}", location);
            return Collections.emptyList();
        }

        //NOTE: To be safe, we force caching to prevent connection/socket
        // timeouts (github #150).
        sitemapDoc.getInputStream().enforceFullCaching();

        List<SitemapRecord> childSitemaps = new ArrayList<>();
        var parser = new SitemapParser(configuration.isLenient(), stopping);
        childSitemaps.addAll(parser.parse(sitemapDoc, ctx.getUrlConsumer()));

        return childSitemaps;
    }

    // Follow redirects
    private HttpFetchResponse httpGet(
            HttpFetcher fetcher,
            CrawlDoc doc) throws IOException {
        return httpGet(fetcher, doc, 0);
    }

    private HttpFetchResponse httpGet(
            HttpFetcher fetcher,
            CrawlDoc doc,
            int loop) throws IOException {

        var location = doc.getReference();
        var response = fetcher.fetch(new HttpFetchRequest(doc, HttpMethod.GET));
        var redirectUrl = response.getRedirectTarget();
        if (StringUtils.isNotBlank(redirectUrl)
                && !redirectUrl.equalsIgnoreCase(location)) {
            if (loop >= 100) {
                LOG.error(
                        "Sitemap redirect loop detected. "
                                + "Last redirect: {} --> {}",
                        location, redirectUrl);
                return response;
            }
            LOG.info("         Redirect: {} --> {}", location, redirectUrl);

            // fetch redirect target then store back original URL
            doc.getDocContext().setReference(redirectUrl);
            var redirectResponse = httpGet(fetcher, doc, loop + 1);
            doc.getDocContext().setReference(location);
            return redirectResponse;
        }
        return response;
    }

    //--- Life cycle events ----------------------------------------------------

    @Override
    protected void onCrawlerCleanBegin(CrawlerEvent event) {
        Optional.ofNullable(sitemapStore).ifPresent(GridMap::clear);
    }

    @Override
    protected void onCrawlerCrawlBegin(CrawlerEvent event) {
        sitemapStore = event.getSource()
                .getGrid().getStorage().getMap(
                        SITEMAP_STORE_NAME, SitemapRecord.class);
    }

    @Override
    protected void onCrawlerStopBegin(CrawlerEvent event) {
        stopping.setTrue();
    }
}
