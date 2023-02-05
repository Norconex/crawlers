/* Copyright 2015-2022 Norconex Inc.
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

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.text.TextMatcher;

/**
 * Commonly encountered restrictions that can be applied to {@link Properties}
 * instances. Each method return a newly created
 * list that can safely be modified without impacting subsequent calls.
 *
 */
public final class CommonRestrictions {

    private CommonRestrictions() {
        super();
    }

    /**
     * <p>
     * Common content-types defining an XML feed (RSS, Atom).
     * The field has to contain one of these for the restriction to apply:
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
     * @param field name of Properties field
     * @return list of restrictions
         */
    public static PropertyMatchers xmlFeedContentTypes(String field) {
        return basicMatcherIgnoreCase(field,
                "application/atom+xml",
                "application/mathml+xml",
                "application/rss+xml",
                "application/vnd.wap.xhtml+xml",
                "application/x-asp",
                "application/xhtml+xml",
                "application/xml",
                "application/xslt+xml",
                "image/svg+xml",
                "text/html",
                "text/xml");
    }

    /**
     * <p>
     * Common content-types defining a DOM document. That is, documents that
     * are HTML, XHTML, or XML-based.
     * The field has to contain one of these for the restriction to apply:
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
     * @param field name of Properties field
     * @return list of restrictions
     */
    public static PropertyMatchers domContentTypes(String field) {
        return basicMatcherIgnoreCase(field,
                "application/atom+xml",
                "application/mathml+xml",
                "application/rss+xml",
                "application/vnd.wap.xhtml+xml",
                "application/x-asp",
                "application/xhtml+xml",
                "application/xml",
                "application/xslt+xml",
                "image/svg+xml",
                "text/html",
                "text/xml");
    }

    /**
     * <p>
     * Default content-types defining an HTML or XHTML document.
     * The field has to contain one of these for the restriction to apply:
     * </p>
     * {@nx.block #htmlContentTypes
     * <ul>
     *   <li>application/vnd.wap.xhtml+xml</li>
     *   <li>application/xhtml+xml</li>
     *   <li>text/html</li>
     * </ul>
     * }
     * @param field name of Properties field
     * @return list of restrictions
         */
    public static PropertyMatchers htmlContentTypes(String field) {
        return basicMatcherIgnoreCase(field,
                "application/vnd.wap.xhtml+xml",
                "application/xhtml+xml",
                "text/html");
    }

    /**
     * <p>
     * Common content-types defining an XML document.
     * The field has to contain one of these for the restriction to apply:
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
     * @param field name of Properties field
     * @return list of restrictions
     */
    public static PropertyMatchers xmlContentTypes(String field) {
        return basicMatcherIgnoreCase(field,
                "application/atom+xml",
                "application/mathml+xml",
                "application/rss+xml",
                "application/xhtml+xml",
                "application/xml",
                "application/xslt+xml",
                "image/svg+xml",
                "text/xml");
    }

    /**
     * <p>
     * Content types of standard image format supported by all Java ImageIO
     * implementations: JPEG, PNG, GIF, BMP, WBMP.
     * The field has to contain one of these for the restriction to apply:
     * </p>
     * <ul>
     *   <li>image/bmp</li>
     *   <li>image/gif</li>
     *   <li>image/jpeg</li>
     *   <li>image/png</li>
     *   <li>image/vnd.wap.wbmp</li>
     *   <li>image/x-windows-bmp</li>
     * </ul>
     * @param field name of Properties field
     * @return list of restrictions
         */
    public static PropertyMatchers imageIOStandardContentTypes(String field) {
        return basicMatcherIgnoreCase(field,
                "image/bmp",
                "image/gif",
                "image/jpeg",
                "image/png",
                "image/vnd.wap.wbmp",
                "image/x-windows-bmp");
    }

    private static PropertyMatchers basicMatcherIgnoreCase(
            String key, String... regexes) {
        PropertyMatchers matchers = new PropertyMatchers();
        for (String regex : regexes) {
            matchers.add(new PropertyMatcher(
                    TextMatcher.basic(key).setIgnoreCase(true),
                    TextMatcher.basic(regex).setIgnoreCase(true)));
        }
        return matchers;
    }
}
