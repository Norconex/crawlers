/* Copyright 2021 Norconex Inc.
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
package com.norconex.importer.handler;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.text.TextMatcher;

/**
 * <p>
 * Commonly used {@link TextMatcher} instances.
 * Each matcher returned are fresh instances that can safely be modified.
 * </p>
 *
 */
public final class CommonMatchers {

    /**
     * Base content types for XML: "application/xml" and "text/xml".
     */
    public static final String[] XML_BASE_CONTENT_TYPES = new String[] {
        "application/xml",
        "text/xml"
    };

    /**
     * ATOM, RDF, RSS, and strict XML content types.
     */
    public static final String[] XML_FEED_CONTENT_TYPES = ArrayUtils.addAll(
        XML_BASE_CONTENT_TYPES,
        "application/atom+xml",
        "application/rdf+xml",
        "application/rss+xml"
    );

    /**
     * HTML/XHTML content types.
     */
    public static final String[] HTML_CONTENT_TYPES = new String[] {
        "application/vnd.wap.xhtml+xml",
        "application/xhtml+xml",
        "text/html"
    };

    /**
     * HTML, XHTML, and XML-based content types.
     */
    public static final String[] XML_CONTENT_TYPES = ArrayUtils.addAll(
        XML_FEED_CONTENT_TYPES,
        "application/xhtml+xml",
        "application/xslt+xml",
        "image/svg+xml"
    );


    /**
     * Content types representing a document object model.
     */
    public static final String[] DOM_CONTENT_TYPES = ArrayUtils.addAll(
        ArrayUtils.addAll(
            XML_CONTENT_TYPES,
            HTML_CONTENT_TYPES
        ),
        "application/mathml+xml",
        "application/x-asp"
    );

    /**
     * Content types for natively supported Java ImageIO images.
     */
    public static final String[] IMAGE_IO_CONTENT_TYPES = new String[] {
        "image/bmp",
        "image/gif",
        "image/jpeg",
        "image/png",
        "image/vnd.wap.wbmp",
        "image/x-windows-bmp"
    };

    private CommonMatchers() {}

    /**
     * <p>
     * Matcher for common content-types defining a DOM document. That is,
     * documents that are HTML, XHTML, or XML-based.
     * The value has to be any of these to trigger a match (case-insensitive):
     * </p>
     * {@nx.block #domContentTypes
     * <ul>
     *   <li>application/atom+xml</li>
     *   <li>application/mathml+xml</li>
     *   <li>application/rss+xml</li>
     *   <li>application/vnd.wap.xhtml+xml</li>
     *   <li>application/x-asp</li>
     *   <li>application/xhtml+xml</li>
     *   <li>application/xml</li>
     *   <li>application/xslt+xml</li>
     *   <li>image/svg+xml</li>
     *   <li>text/html</li>
     *   <li>text/xml</li>
     * </ul>
     * }
     * @return text matcher
     */
    public static TextMatcher domContentTypes() {
        return csv(DOM_CONTENT_TYPES);
    }

    /**
     * <p>
     * Matcher for common content-types defining an XML feed (RSS, Atom).
     * Because XML feeds are not always declaring their content type properly,
     * this matcher will also match base XML content types.
     * The value has to be any of these to trigger a match (case-insensitive):
     * </p>
     * {@nx.block #xmlFeedContentTypes
     * <ul>
     *   <li>application/atom+xml</li>
     *   <li>application/rdf+xml</li>
     *   <li>application/rss+xml</li>
     *   <li>application/xml</li>
     *   <li>text/xml</li>
     * </ul>
     * }
     * @return text matcher
     */
    public static TextMatcher xmlFeedContentTypes() {
        return csv(XML_FEED_CONTENT_TYPES);
    }

    /**
     * <p>
     * Matcher for common content-types defining an HTML or XHTML document.
     * The value has to be any of these to trigger a match (case-insensitive):
     * </p>
     * {@nx.block #htmlContentTypes
     * <ul>
     *   <li>application/vnd.wap.xhtml+xml</li>
     *   <li>application/xhtml+xml</li>
     *   <li>text/html</li>
     * </ul>
     * }
     * @return text matcher
     */
    public static TextMatcher htmlContentTypes() {
        return csv(HTML_CONTENT_TYPES);
    }

    /**
     * <p>
     * Common content-types defining an XML document.
     * The value has to be any of these to trigger a match (case-insensitive):
     * </p>
     * {@nx.block #domContentTypes
     * <ul>
     *   <li>application/atom+xml</li>
     *   <li>application/mathml+xml</li>
     *   <li>application/rss+xml</li>
     *   <li>application/xhtml+xml</li>
     *   <li>application/xml</li>
     *   <li>application/xslt+xml</li>
     *   <li>image/svg+xml</li>
     *   <li>text/xml</li>
     * </ul>
     * }
     * @return text matcher
     */
    public static TextMatcher xmlContentTypes() {
        return csv(XML_CONTENT_TYPES);
    }

    /**
     * <p>
     * Content types of standard image format supported by all Java ImageIO
     * implementations: JPEG, PNG, GIF, BMP, WBMP.
     * The value has to be any of these to trigger a match (case-insensitive):
     * </p>
     * <ul>
     *   <li>image/bmp</li>
     *   <li>image/gif</li>
     *   <li>image/jpeg</li>
     *   <li>image/png</li>
     *   <li>image/vnd.wap.wbmp</li>
     *   <li>image/x-windows-bmp</li>
     * </ul>
     * @return text matcher
     */
    public static TextMatcher imageIOStandardContentTypes() {
        return csv(IMAGE_IO_CONTENT_TYPES);
    }

    private static TextMatcher csv(String[] values) {
        return TextMatcher
                .csv(StringUtils.join(values, ','))
                .ignoreCase();
    }
}
