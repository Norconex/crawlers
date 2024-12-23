/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.operations.scope.impl;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.CrawlerLifeCycleListener;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.operations.scope.UrlScope;
import com.norconex.crawler.web.operations.scope.UrlScopeResolver;
import com.norconex.crawler.web.util.Web;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * By default a crawler will try to follow all links it discovers. You can
 * define your own filters to limit the scope of the pages being crawled.
 * When you have multiple URLs defined as start URLs, it can be tricky to
 * perform global filtering that apply to each URLs without causing
 * URL filtering conflicts.  This class offers an easy way to address
 * a frequent URL filtering need: to "stay on site". That is,
 * when following a page and extracting URLs found in it, make sure to
 * only keep URLs that are on the same site as the page URL we are on.
 * </p>
 * <p>
 * By default this class does not request to stay on a site.
 * </p>
 * @since 2.3.0
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class GenericUrlScopeResolver
        extends CrawlerLifeCycleListener
        implements UrlScopeResolver,
        Configurable<GenericUrlScopeResolverConfig> {

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private GridCache<SitemapPresence> resolvedSites;

    @Getter
    private GenericUrlScopeResolverConfig configuration =
            new GenericUrlScopeResolverConfig();

    //    @JsonIgnore
    //    @EqualsAndHashCode.Exclude
    //    @ToString.Exclude
    //    private WebCrawlerSessionAttributes crawlerContext;

    @Override
    protected void onCrawlerCrawlBegin(CrawlerEvent event) {
        resolvedSites = Web.gridCache(
                event.getSource(),
                RESOLVED_SITES_CACHE_NAME,
                SitemapPresence.class);
        LOG.debug(RESOLVED_SITES_CACHE_NAME + " cache initialized.");
        //        crawlerContext =
        //                (WebCrawlerSessionAttributes) event.getSource().getAttributes();
    }

    @Override
    public UrlScope resolve(
            String inScopeURL, WebCrawlDocContext candidateDocContext) {
        // if not specifying any scope, candidate URL is good
        if (!configuration.isStayOnProtocol()
                && !configuration.isStayOnDomain()
                && !configuration.isStayOnPort()
                && !configuration.isStayOnSitemap()) {
            return UrlScope.in();
        }

        String candidateURL = candidateDocContext.getReference();

        try {
            var inScope = new HttpURL(inScopeURL);
            HttpURL candidate;
            if (candidateURL.startsWith("//")) {
                candidate = new HttpURL(
                        inScope.getProtocol() + ':' + candidateURL);
            } else {
                candidate = new HttpURL(candidateURL);
            }
            if (configuration.isStayOnProtocol()
                    && !inScope.getProtocol().equalsIgnoreCase(
                            candidate.getProtocol())) {
                return UrlScope.out(
                        "Outbound protocol: %s".formatted(
                                candidate.getProtocol()));
            }
            if (configuration.isStayOnDomain()
                    && !isOnDomain(inScope.getHost(), candidate.getHost())) {
                return UrlScope.out(
                        "Outbound domain: %s".formatted(
                                candidate.getHost()));
            }
            if (configuration.isStayOnPort()
                    && inScope.getPort() != candidate.getPort()) {
                return UrlScope.out(
                        "Outbound port: %s".formatted(
                                candidate.getPort()));
            }

            // as this point if the document came from a sitemap, it
            // will have the isSitemap flag set to true.
            if (configuration.isStayOnSitemap()
                    && siteHasSitemap(inScopeURL)
                    && !candidateDocContext.isFromSitemap()) {
                return UrlScope.out("Not found on sitemap.");
            }

            return UrlScope.in();
        } catch (Exception e) {
            LOG.debug("Unsupported URL \"{}\".", candidateURL, e);
            return UrlScope.out("Error analysing URL. " + e.getMessage());
        }
    }

    private boolean isOnDomain(String inScope, String candidate) {
        // if domains are the same, we are good. Covers zero depth too.
        if (inScope.equalsIgnoreCase(candidate)) {
            return true;
        }

        // if accepting sub-domains, check if it ends the same.
        return configuration.isIncludeSubdomains()
                && StringUtils.endsWithIgnoreCase(candidate, "." + inScope);
    }

    private boolean siteHasSitemap(String inScope) {
        var urlRoot = HttpURL.getRoot(inScope);
        var sitemapPresence = resolvedSites.get(urlRoot);
        //        var sitemapPresence = crawlerContext.getResolvedWebsites().get(urlRoot);
        // At this point, the sitemap should never be "RESOLVING".
        return sitemapPresence == SitemapPresence.PRESENT;
    }
}
