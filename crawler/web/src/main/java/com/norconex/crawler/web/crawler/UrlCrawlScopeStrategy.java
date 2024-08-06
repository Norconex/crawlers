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
package com.norconex.crawler.web.crawler;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerLifeCycleListener;
import com.norconex.crawler.web.crawler.WebCrawlerContext.SitemapPresence;
import com.norconex.crawler.web.util.Web;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>By default a crawler will try to follow all links it discovers. You can
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
//TODO make this an interface so developers can provide their own?
@EqualsAndHashCode
@ToString
@Slf4j
public class UrlCrawlScopeStrategy extends CrawlerLifeCycleListener implements
        Configurable<UrlCrawlScopeStrategyConfig> {


    @Getter
    private UrlCrawlScopeStrategyConfig configuration =
            new UrlCrawlScopeStrategyConfig();


    @JsonIgnore
    private WebCrawlerContext crawlerContext;

    @Override
    protected void onCrawlerRunBegin(CrawlerEvent event) {
        LOG.debug("UrlCrawlScopeStrategy initialized with crawler context.");
        crawlerContext = Web.crawlerContext(event.getSource());
    }

    public boolean isInScope(String inScopeURL, String candidateURL) {
        // if not specifying any scope, candidate URL is good
        if (!configuration.isStayOnProtocol()
                && !configuration.isStayOnDomain()
                && !configuration.isStayOnPort()
                && !configuration.isStayOnSitemap()) {
            return true;
        }

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
                LOG.debug("URL out-of-scope (protocol): {}", candidateURL);
                return false;
            }
            if (configuration.isStayOnDomain()
                    && !isOnDomain(inScope.getHost(), candidate.getHost())) {
                LOG.debug("URL out-of-scope (domain): {}", candidateURL);
                return false;
            }
            if (configuration.isStayOnPort()
                    && inScope.getPort() != candidate.getPort()) {
                LOG.debug("URL out-of-scope (port): {}", candidateURL);
                return false;
            }

            if (configuration.isStayOnSitemap() && siteHasSitemap(inScopeURL)) {
                LOG.debug("URL out-of-scope (sitemap): {}", candidateURL);
                return false;
            }

            return true;
        } catch (Exception e) {
            LOG.debug("Unsupported URL \"{}\".", candidateURL, e);
            return false;
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
        var sitemapPresence =
                crawlerContext.getResolvedWebsites().get(urlRoot);
        // At this point, the sitemap should never be "RESOLVING"
        // If there is a sitemap for the URL in scope, we always reject, since
        // having to determine the scope should not occur when the URL is
        // coming from the sitemap. Thus no URLs should be extracted/accepted.
        return sitemapPresence == SitemapPresence.PRESENT;

    }

    public WebCrawlerContext getCrawlerContext() {
        return crawlerContext;
    }

    public void setCrawlerContext(WebCrawlerContext crawlerContext) {
        this.crawlerContext = crawlerContext;
    }
}
