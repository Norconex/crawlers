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

import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.CommonMatchers;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link XmlFeedLinkExtractor}.
 * </p>
 * @since 2.7.0
 */
@Data
@Accessors(chain = true)
public class XmlFeedLinkExtractorConfig {
    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#XML_FEED_CONTENT_TYPES}.
     */
    private final TextMatcher contentTypeMatcher =
            CommonMatchers.xmlFeedContentTypes();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    private final PropertyMatchers restrictions = new PropertyMatchers();

    public XmlFeedLinkExtractorConfig setFieldMatcher(
            TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#XML_FEED_CONTENT_TYPES}.
     * @param matcher content type matcher
     * @return this
     */
    public XmlFeedLinkExtractorConfig setContentTypeMatcher(
            TextMatcher matcher) {
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
