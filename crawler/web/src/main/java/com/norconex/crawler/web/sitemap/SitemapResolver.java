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
package com.norconex.crawler.web.sitemap;

import java.util.List;

import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.robot.RobotsTxtProvider;


/**
 * <p>
 * For each unique web site domain (scheme, host name, and port), resolve the
 * corresponding Sitemap(s), if any.  Sitemaps are resolved at most once per
 * domain within a crawling session.
 * </p>
 * <p>
 * Unless configured to do otherwise, the crawler will use the Sitemap resolver
 * to load and parse any Sitemaps defined as crawler start URLs (see
 * {@link WebCrawlerConfig#setStartSitemapURLs(List)} or defined
 * within a web site
 * <a href="http://www.robotstxt.org/robotstxt.html">robots.txt</a> file
 * (see {@link RobotsTxtProvider}.
 * </p>
 * <p>
 * Implementations are free to offer support for additional Sitemap sources.
 * </p>
 *
 */
public interface SitemapResolver {

    //TODO check if start URL sitemaps are define without a resolver
    //TODO add to robotstxtparser the option to ignore sitemaps.

    /**
     * Resolves the sitemap instructions for a URL "root" (e.g.
     * http://www.example.com).
     * @param sitemapResolutionContext context objects used to resolve Sitemaps
     */
    void resolveSitemaps(SitemapResolutionContext sitemapResolutionContext);
}
