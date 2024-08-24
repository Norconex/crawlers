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
package com.norconex.crawler.web.doc.operations.link.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.web.doc.operations.link.LinkExtractor;
import com.norconex.importer.handler.CommonMatchers;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Implementation of {@link LinkExtractor} using
 * <a href="http://tika.apache.org/">Apache Tika</a> to perform URL
 * extractions from HTML documents.
 * This is an alternative to the {@link HtmlLinkExtractor}.
 * </p>
 * <p>
 * The configuration of content-types, storing the referrer data, and ignoring
 * "nofollow" and ignoring link data are the same as in
 * {@link HtmlLinkExtractor}. For link data, this parser only keeps a
 * pre-defined set of link attributes, when available (title, type,
 * uri, text, rel).
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.crawler.web.doc.operations.link.impl.TikaLinkExtractor"
 *     ignoreNofollow="[false|true]" >
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </extractor>
 * }
 * @see HtmlLinkExtractor
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class TikaLinkExtractorConfig {

    private boolean ignoreNofollow;
    /**
     * Whether to ignore extra data associated with a link.
     * @param ignoreLinkData <code>true</code> to ignore.
     * @return <code>true</code> to ignore.
     * @since 3.0.0
     */
    private boolean ignoreLinkData;

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#HTML_CONTENT_TYPES}.
     * @param contentTypeMatcher content type matcher
     * @return content type matcher
     */
    private final TextMatcher contentTypeMatcher =
            CommonMatchers.htmlContentTypes();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     * @param fieldMatcher field matcher
     * @return field matcher
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    public TikaLinkExtractorConfig setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#HTML_CONTENT_TYPES}.
     * @param contentTypeMatcher content type matcher
     * @return this
     */
    public TikaLinkExtractorConfig setContentTypeMatcher(TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
        return this;
    }
}
