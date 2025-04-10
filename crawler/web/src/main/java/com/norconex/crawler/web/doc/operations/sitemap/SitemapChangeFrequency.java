/* Copyright 2016-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.sitemap;

import org.apache.commons.lang3.StringUtils;

/**
 * Sitemap change frequency unit, as defined on
 * <a href="http://www.sitemaps.org/protocol.html#xmlTagDefinitions">
 * http://www.sitemaps.org/protocol.html</a>
 * @since 2.5.0
 */
public enum SitemapChangeFrequency {
    ALWAYS,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    NEVER;

    /**
     * Gets the sitemap change frequency matching the supplied string.
     * Has the same effect as {@link #valueOf(String)} except that it will
     * return <code>null</code> when no matches are found.
     * @param frequency change frequency
     * @return the matching sitemap change frequency, or <code>null</code>
     */
    public static SitemapChangeFrequency of(String frequency) {
        if (StringUtils.isBlank(frequency)) {
            return null;
        }
        for (SitemapChangeFrequency v : SitemapChangeFrequency.values()) {
            if (v.toString().equalsIgnoreCase(frequency)) {
                return v;
            }
        }
        return null;
    }
}
