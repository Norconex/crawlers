/* Copyright 2010-2016 Norconex Inc.
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
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.importer.handler.filter.OnMatch;

public class RobotsTxt {

    public static final float UNSPECIFIED_CRAWL_DELAY = -1;

    private static final IRobotsTxtFilter[] EMPTY_ROBOTSTXT_FILTERS =
            new IRobotsTxtFilter[] {};
    
    //TODO delete this variable that contains all of them?
    private final IRobotsTxtFilter[] filters;
    private final IRobotsTxtFilter[] disallowFilters;
    private final IRobotsTxtFilter[] allowFilters;
    private final float crawlDelay;
    private final String[] sitemapLocations;
    
    public RobotsTxt(IRobotsTxtFilter[] filters) {
        this(filters, UNSPECIFIED_CRAWL_DELAY);
    }
    public RobotsTxt(IRobotsTxtFilter[] filters, float crawlDelay) {
        this(filters, null, crawlDelay);
    }
    public RobotsTxt(IRobotsTxtFilter[] filters, String[] sitemapLocations) {
        this(filters, sitemapLocations, UNSPECIFIED_CRAWL_DELAY);
    }
    public RobotsTxt(IRobotsTxtFilter[] filters, String[] sitemapLocations, 
            float crawlDelay) {
        super();
        this.filters = ArrayUtils.clone(filters);
        this.sitemapLocations = ArrayUtils.clone(sitemapLocations);
        this.crawlDelay = crawlDelay;
        
        if (filters == null) {
            this.disallowFilters = null;
            this.allowFilters = null;
        } else {
            List<IRobotsTxtFilter> disallows = new ArrayList<>();
            List<IRobotsTxtFilter> allows = new ArrayList<>();
            for (IRobotsTxtFilter filter : this.filters) {
                if (filter.getOnMatch() == OnMatch.EXCLUDE) {
                    disallows.add(filter);
                } else {
                    allows.add(filter);
                }
            }
            this.disallowFilters = disallows.toArray(EMPTY_ROBOTSTXT_FILTERS);
            this.allowFilters = allows.toArray(EMPTY_ROBOTSTXT_FILTERS);
        }
    }

    //TODO deprecate?
    /**
     * Gets all filters.
     * @return filters
     */
    public IRobotsTxtFilter[] getFilters() {
        return ArrayUtils.clone(filters);
    }
    /**
     * Gets "Disallow" filters.
     * @return disallow filters
     * @since 2.4.0
     */
    public IRobotsTxtFilter[] getDisallowFilters() {
        return ArrayUtils.clone(disallowFilters);
    }
    /**
     * Gets "Allow" filters.
     * @return allow filters
     * @since 2.4.0
     */
    public IRobotsTxtFilter[] getAllowFilters() {
        return ArrayUtils.clone(allowFilters);
    }
    public String[] getSitemapLocations() {
        return ArrayUtils.clone(sitemapLocations);
    }
    public float getCrawlDelay() {
        return crawlDelay;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Float.floatToIntBits(crawlDelay);
        result = prime * result + Arrays.hashCode(filters);
        result = prime * result + Arrays.hashCode(sitemapLocations);
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RobotsTxt other = (RobotsTxt) obj;
        if (Float.floatToIntBits(crawlDelay) != Float
                .floatToIntBits(other.crawlDelay)) {
            return false;
        }
        if (!Arrays.equals(filters, other.filters)) {
            return false;
        }
        if (!Arrays.equals(sitemapLocations, other.sitemapLocations)) {
            return false;
        }
        return true;
    }
    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("filters", filters);
        builder.append("crawlDelay", crawlDelay);
        builder.append("sitemapLocations", sitemapLocations);
        return builder.toString();
    }
    
    
}
