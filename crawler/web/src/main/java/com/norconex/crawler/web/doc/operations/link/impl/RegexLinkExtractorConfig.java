/* Copyright 2017-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.link.impl;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.CommonMatchers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link RegexLinkExtractor}.
 * </p>
 * @since 2.7.0
 */
@Data
@Accessors(chain = true)
public class RegexLinkExtractorConfig {
    //TODO add fieldMatcher and contentTypeMatcher?

    public static final String DEFAULT_CONTENT_TYPE_PATTERN = "text/.*";

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExtractionPattern {
        private String match;
        private String replace;
    }

    /**
     * The maximum supported URL length.
     * Default is {@value #DEFAULT_MAX_URL_LENGTH}.
     */
    private int maxUrlLength = DEFAULT_MAX_URL_LENGTH;

    /**
     * Gets the character set of pages on which link extraction is performed.
     * Default is <code>null</code> (charset detection will be attempted).
     */
    private Charset charset;

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default
     * matches all content types
     */
    private final TextMatcher contentTypeMatcher = CommonMatchers.all();

    private final List<ExtractionPattern> patterns = new ArrayList<>();

    private final PropertyMatchers restrictions = new PropertyMatchers();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    public List<ExtractionPattern> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    public RegexLinkExtractorConfig setPatterns(
            List<ExtractionPattern> patterns) {
        CollectionUtil.setAll(this.patterns, patterns);
        return this;
    }

    public RegexLinkExtractorConfig clearPatterns() {
        patterns.clear();
        return this;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default matches
     * all content types.
     * @param matcher content type matcher
     * @return this
     */
    public RegexLinkExtractorConfig setContentTypeMatcher(TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
        return this;
    }

    /**
     * Clears all restrictions.
     */
    public void clearRestrictions() {
        restrictions.clear();
    }

    /**
     * Gets all restrictions
     * @return the restrictions
     */
    public PropertyMatchers getRestrictions() {
        return restrictions;
    }
}
