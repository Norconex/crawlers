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
package com.norconex.crawler.web.commands.crawl.task.operations.recrawl.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link GenericRecrawlableResolver}.
 * </p>
 * @since 2.5.0
 */
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
        public enum ApplyTo {
            CONTENT_TYPE, REFERENCE
        }

        /**
         * Whether to apply this minimum frequency to matching content type
         * or document reference. Default to {@link ApplyTo#REFERENCE}.
         */
        private ApplyTo applyTo = ApplyTo.REFERENCE;
        /**
         * String representation of a frequency. Can be one of "always",
         * "hourly", "daily", "weekly", "monthly", "yearly", "never", or a
         * numeric value in milliseconds.
         */
        private String value;

        /**
         * A matcher applied to either a document reference or content type,
         * based on {@link #getApplyTo()}.
         */
        private final TextMatcher matcher = new TextMatcher();

        public MinFrequency(
                ApplyTo applyTo, String value, TextMatcher matcher) {
            this.applyTo = applyTo;
            this.value = value;
            this.matcher.copyFrom(matcher);
        }

        public void setMatcher(TextMatcher matcher) {
            this.matcher.copyFrom(matcher);
        }
    }
}
