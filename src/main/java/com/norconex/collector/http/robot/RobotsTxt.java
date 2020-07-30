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
package com.norconex.collector.http.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.importer.handler.filter.OnMatch;

public class RobotsTxt {

    public static final float UNSPECIFIED_CRAWL_DELAY = -1;

    //TODO delete this variable that contains all of them?
    private final List<IRobotsTxtFilter> filters = new ArrayList<>();
    private final List<IRobotsTxtFilter> disallowFilters = new ArrayList<>();
    private final List<IRobotsTxtFilter> allowFilters = new ArrayList<>();
    private final float crawlDelay;
    private final List<String> sitemapLocations = new ArrayList<>();

    /**
     * Creates a new robot txt object with the supplied filters.
     * @param filters filters
     */
    public RobotsTxt(IRobotsTxtFilter... filters) {
        this(CollectionUtil.asListOrEmpty(filters), UNSPECIFIED_CRAWL_DELAY);
    }
    /**
     * Creates a new robot txt object with the supplied filters.
     * @param filters filters
     * @since 3.0.0
     */
    public RobotsTxt(List<IRobotsTxtFilter> filters) {
        this(filters, UNSPECIFIED_CRAWL_DELAY);
    }
    public RobotsTxt(List<IRobotsTxtFilter> filters, float crawlDelay) {
        this(filters, null, crawlDelay);
    }
    public RobotsTxt(
            List<IRobotsTxtFilter> filters, List<String> sitemapLocations) {
        this(filters, sitemapLocations, UNSPECIFIED_CRAWL_DELAY);
    }
    public RobotsTxt(List<IRobotsTxtFilter> filters,
            List<String> sitemapLocations, float crawlDelay) {
        super();

        CollectionUtil.setAll(this.filters, filters);
        CollectionUtil.setAll(this.sitemapLocations, sitemapLocations);
        this.crawlDelay = crawlDelay;

        if (!this.filters.isEmpty()) {
            List<IRobotsTxtFilter> disallows = new ArrayList<>();
            List<IRobotsTxtFilter> allows = new ArrayList<>();
            for (IRobotsTxtFilter filter : this.filters) {
                if (filter.getOnMatch() == OnMatch.EXCLUDE) {
                    disallows.add(filter);
                } else {
                    allows.add(filter);
                }
            }
            CollectionUtil.setAll(this.disallowFilters, disallows);
            CollectionUtil.setAll(this.allowFilters, allows);
        }
    }

    //TODO deprecate?
    /**
     * Gets all filters.
     * @return filters (never <code>null</code>)
     */
    public List<IRobotsTxtFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }
    /**
     * Gets "Disallow" filters.
     * @return disallow filters (never <code>null</code>)
     * @since 2.4.0
     */
    public List<IRobotsTxtFilter> getDisallowFilters() {
        return Collections.unmodifiableList(disallowFilters);
    }
    /**
     * Gets "Allow" filters.
     * @return allow filters (never <code>null</code>)
     * @since 2.4.0
     */
    public List<IRobotsTxtFilter> getAllowFilters() {
        return Collections.unmodifiableList(allowFilters);
    }
    public List<String> getSitemapLocations() {
        return Collections.unmodifiableList(sitemapLocations);
    }
    public float getCrawlDelay() {
        return crawlDelay;
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
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
