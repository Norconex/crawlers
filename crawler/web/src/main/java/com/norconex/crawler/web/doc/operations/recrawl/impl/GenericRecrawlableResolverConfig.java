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
package com.norconex.crawler.web.doc.operations.recrawl.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.time.DurationParser;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>Relies on both sitemap directives and custom instructions for
 * establishing the minimum frequency between each document recrawl.
 * </p>
 *
 * <h3>Sitemap support:</h3>
 * <p>
 * Provided crawler support for sitemaps has not been disabled,
 * this class tries to honor last modified and frequency directives found
 * in sitemap files.
 * </p>
 * <p>
 * By default, existing sitemap directives take precedence over custom ones.
 * You chose to have sitemap directives be considered last or even disable
 * sitemap directives using the {@link #setSitemapSupport(SitemapSupport)}
 * method.
 * </p>
 *
 * <h3>Custom recrawl frequencies:</h3>
 * <p>
 * You can chose to have some of your crawled documents be re-crawled less
 * frequently than others by specifying custom minimum frequencies
 * ({@link #setMinFrequencies(Collection)}). Minimum frequencies are
 * processed in the order specified and must each have to following:
 * </p>
 * <ul>
 *   <li>applyTo: Either "reference" or "contentType"
 *       (defaults to "reference").</li>
 *   <li>pattern: A regular expression.</li>
 *   <li>value: one of "always", "hourly", "daily", "weekly", "monthly",
 *       "yearly", "never", or a numeric value in milliseconds.</li>
 * </ul>
 *
 * <p>
 * As of 2.7.0, XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.usage
 * <recrawlableResolver
 *     class="com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver"
 *     sitemapSupport="[first|last|never]" >
 *
 *   <minFrequency applyTo="[reference|contentType]"
 *       value="([always|hourly|daily|weekly|monthly|yearly|never] or milliseconds)">
 *     <matcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (Matcher for the reference or content type.)
 *     </matcher>
 *   </minFrequency>
 *   (... repeat frequency tag as needed ...)
 * </recrawlableResolver>
 * }
 *
 * {@nx.xml.example
 * <recrawlableResolver
 *     class="com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver"
 *     sitemapSupport="last" >
 *   <minFrequency applyTo="contentType" value="monthly">
 *     <matcher>application/pdf</matcher>
 *   </minFrequency>
 *   <minFrequency applyTo="reference" value="1800000">
 *     <matcher method="regex">.*latest-news.*\.html</matcher>
 *   </minFrequency>
 * </recrawlableResolver>
 * }
 * <p>
 * The above example ensures PDFs are re-crawled no more frequently than
 * once a month, while HTML news can be re-crawled as fast at every half hour.
 * For the rest, it relies on the website sitemap directives (if any).
 * </p>
 *
 * @since 2.5.0
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class GenericRecrawlableResolverConfig {

    public enum SitemapSupport {
        FIRST, LAST, NEVER;
        public static SitemapSupport getSitemapSupport(String sitemapSupport) {
            if (StringUtils.isBlank(sitemapSupport)) {
                return null;
            }
            for (SitemapSupport v : SitemapSupport.values()) {
                if (v.toString().equalsIgnoreCase(sitemapSupport)) {
                    return v;
                }
            }
            return null;
        }
    }

    /**
     * The sitemap support strategy. A <code>null</code> value
     * is equivalent to specifying the default {@link SitemapSupport#FIRST}.
     * @param sitemapSupport sitemap support strategy
     * @return sitemap support strategy
     */
    private SitemapSupport sitemapSupport = SitemapSupport.FIRST;

    private final List<MinFrequency> minFrequencies = new ArrayList<>();


    /**
     * Gets minimum frequencies.
     * @return minimum frequencies
     */
    public List<MinFrequency> getMinFrequencies() {
        return Collections.unmodifiableList(minFrequencies);
    }
    /**
     * Sets minimum frequencies.
     * @param minFrequencies minimum frequencies
     */
    public void setMinFrequencies(Collection<MinFrequency> minFrequencies) {
        CollectionUtil.setAll(this.minFrequencies, minFrequencies);
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class MinFrequency {
        private String applyTo;
        private String value;
        private final TextMatcher matcher = new TextMatcher();
        public MinFrequency(String applyTo, String value, TextMatcher matcher) {
            this.applyTo = applyTo;
            this.value = value;
            this.matcher.copyFrom(matcher);
        }
        public void setMatcher(TextMatcher matcher) {
            this.matcher.copyFrom(matcher);
        }
    }
}
