/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.crawler.web.link.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.link.LinkExtractor;
import com.norconex.importer.handler.CommonMatchers;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Link extractor for extracting links out of
 * <a href="https://en.wikipedia.org/wiki/RSS">RSS</a> and
 * <a href="https://en.wikipedia.org/wiki/Atom_(standard)">Atom</a> XML feeds.
 * It extracts the content of &lt;link&gt; tags.  If you need more complex
 * extraction, consider using {@link RegexLinkExtractor} or creating your own
 * {@link LinkExtractor} implementation.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor only will be applied on documents matching
 * one of these content types:
 * </p>
 *
 * {@nx.include com.norconex.importer.handler.CommonMatchers#xmlFeedContentTypes}
 *
 * <h3>Referrer data</h3>
 * <p>
 * The following referrer information is stored as metadata in each document
 * represented by the extracted URLs:
 * </p>
 * <ul>
 *   <li><b>Referrer reference:</b> The reference (URL) of the page where the
 *   link to a document was found.  Metadata value is
 *   {@link WebDocMetadata#REFERRER_REFERENCE}.</li>
 * </ul>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.crawler.web.link.impl.XmlFeedLinkExtractor">
 *   {@nx.include com.norconex.crawler.web.link.AbstractTextLinkExtractor@nx.xml.usage}
 * </extractor>
 * }
 *
 * {@nx.xml.example
 * <extractor class="com.norconex.crawler.web.link.impl.XmlFeedLinkExtractor">
 *   <restrictTo field="document.reference" method="regex">.*rss$</restrictTo>
 * </extractor>
 * }
 * <p>
 * The above example specifies this extractor should only apply on documents
 * that have their URL ending with "rss" (in addition to the default
 * content types supported).
 * </p>
 *
 * @since 2.7.0
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class XmlFeedLinkExtractorConfig {
    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#XML_FEED_CONTENT_TYPES}.
     * @param contentTypeMatcher content type matcher
     * @return content type matcher
     */
    private final TextMatcher contentTypeMatcher =
            CommonMatchers.xmlFeedContentTypes();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     * @param fieldMatcher field matcher
     * @return field matcher
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    public XmlFeedLinkExtractorConfig setFieldMatcher(
            TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#XML_FEED_CONTENT_TYPES}.
     * @param contentTypeMatcher content type matcher
     * @return this
     */
    public XmlFeedLinkExtractorConfig setContentTypeMatcher(
            TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
        return this;
    }
}
