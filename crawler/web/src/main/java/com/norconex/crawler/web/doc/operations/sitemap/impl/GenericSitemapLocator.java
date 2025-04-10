/* Copyright 2023-2024 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxtProvider;
import com.norconex.crawler.web.doc.operations.sitemap.SitemapLocator;
import com.norconex.crawler.web.util.Web;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * If there is a sitemap defined as a start reference for the same URL web site,
 * this locator is not used. Otherwise, it tells the crawler to
 * use the sitemap as defined in the web site "robots.txt" file (provided
 * the web site defines one and {@link RobotsTxtProvider} is enabled).
 * If no sitemap resolution was possible from "robots.txt", an attempt will
 * be made to retrieve a sitemap using the configured sitemap paths.
 * Default paths are: <code>/sitemap.xml</code> and
 * <code>/sitemap_index.xml</code>
 * </p>
 */
@EqualsAndHashCode
@ToString
public class GenericSitemapLocator implements
        SitemapLocator, Configurable<GenericSitemapLocatorConfig> {

    @Getter
    private final GenericSitemapLocatorConfig configuration =
            new GenericSitemapLocatorConfig();

    @Override
    public List<String> locations(String reference,
            CrawlerContext crawler) {
        List<String> resolvedpaths = new ArrayList<>(configuration.getPaths());
        if (!configuration.isRobotsTxtSitemapDisabled()) {
            var robotsTxt = Web.robotsTxt(crawler, reference);
            if (robotsTxt != null) {
                var locs = robotsTxt.getSitemapLocations();
                if (CollectionUtils.isNotEmpty(locs)) {
                    resolvedpaths.addAll(locs);
                }
            }
        }
        return resolvedpaths.stream()
                .map(p -> HttpURL.toAbsolute(reference, p))
                .toList();
    }
}
