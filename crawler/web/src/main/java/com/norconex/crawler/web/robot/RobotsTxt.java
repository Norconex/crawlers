/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.ListUtils;

import com.norconex.crawler.core.doc.operations.filter.OnMatch;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.ToString;

/**
 * Immutable holder of robots.txt rules.
 */
@ToString
@EqualsAndHashCode
public class RobotsTxt {

    public static final float UNSPECIFIED_CRAWL_DELAY = -1;

    private final List<RobotsTxtFilter> disallowFilters;
    private final List<RobotsTxtFilter> allowFilters;
    private final float crawlDelay;
    private final List<String> sitemapLocations;

    @Builder
    RobotsTxt(
            @Singular List<RobotsTxtFilter> filters,
            @Singular List<String> sitemapLocations,
            float crawlDelay
    ) {
        this.sitemapLocations = Collections.unmodifiableList(
                ListUtils.emptyIfNull(sitemapLocations)
        );
        this.crawlDelay = crawlDelay;

        List<RobotsTxtFilter> disallows = new ArrayList<>();
        List<RobotsTxtFilter> allows = new ArrayList<>();
        for (RobotsTxtFilter filter : ListUtils.emptyIfNull(filters)) {
            if (filter.getOnMatch() == OnMatch.EXCLUDE) {
                disallows.add(filter);
            } else {
                allows.add(filter);
            }
        }
        disallowFilters = Collections.unmodifiableList(disallows);
        allowFilters = Collections.unmodifiableList(allows);
    }

    /**
     * Gets "Disallow" filters.
     * @return disallow filters (never <code>null</code>)
     * @since 2.4.0
     */
    public List<RobotsTxtFilter> getDisallowFilters() {
        return disallowFilters;
    }

    /**
     * Gets "Allow" filters.
     * @return allow filters (never <code>null</code>)
     * @since 2.4.0
     */
    public List<RobotsTxtFilter> getAllowFilters() {
        return allowFilters;
    }

    public List<String> getSitemapLocations() {
        return sitemapLocations;
    }

    public float getCrawlDelay() {
        return crawlDelay;
    }

    public boolean isEmpty() {
        return disallowFilters.isEmpty()
                && allowFilters.isEmpty()
                && sitemapLocations.isEmpty()
                && crawlDelay < 0;
    }
}
