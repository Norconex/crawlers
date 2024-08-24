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
package com.norconex.crawler.web.doc.operations.filter.impl;

import java.util.regex.Pattern;

import com.norconex.crawler.core.doc.operations.filter.OnMatch;

import lombok.Data;
import lombok.experimental.Accessors;
/**
 * <p>
 * Filters URL based based on the number of URL segments. A URL with
 * a number of segments equal or more than the specified count will either
 * be included or excluded, as specified.
 * </p>
 * <p>
 * By default
 * segments are obtained by breaking the URL text at each forward slashes
 * (/), starting after the host name.  You can define different or
 * additional segment separator characters.
 * </p>
 * <p>
 * When <code>duplicate</code> is <code>true</code>, it will count the maximum
 * number of duplicate segments found.
 * </p>
 *
 * {@nx.xml.usage
 *  <filter class="com.norconex.crawler.web.doc.operations.filter.impl.SegmentCountUrlFilter"
 *      onMatch="[include|exclude]"
 *      count="(numeric value)"
 *      duplicate="[false|true]">
 *    <separator>(a regex identifying segment separator)</separator>
 *  </filter>
 * }
 *
 * {@nx.xml.example
 *  <filter class="SegmentCountUrlFilter" onMatch="exclude" count="5" />
 * }
 * <p>
 * The above example will reject URLs with more than 5 forward slashes after
 * the domain.
 * </p>
 *
 * @since 1.2
 * @see Pattern
 */
@Data
@Accessors(chain = true)
public class SegmentCountUrlFilterConfig {

    /** Default segment separator pattern. */
    public static final String DEFAULT_SEGMENT_SEPARATOR_PATTERN = "/";
    /** Default segment count. */
    public static final int DEFAULT_SEGMENT_COUNT = 10;

    private int count = DEFAULT_SEGMENT_COUNT;
    private boolean duplicate;
    private String separator = DEFAULT_SEGMENT_SEPARATOR_PATTERN;
    private OnMatch onMatch;
}
