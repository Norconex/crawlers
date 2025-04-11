/* Copyright 2010-2025 Norconex Inc.
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

import com.norconex.crawler.core.doc.operations.filter.OnMatch;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link SegmentCountUrlFilter}.
 * </p>
 * @since 1.2
 */
@Data
@Accessors(chain = true)
public class SegmentCountUrlFilterConfig {

    /** Default segment separator pattern. */
    public static final String DEFAULT_SEGMENT_SEPARATOR_PATTERN = "/";
    /** Default segment count. */
    public static final int DEFAULT_SEGMENT_COUNT = 10;

    /**
     * Number of segments after which this filter is considered a match.
     * Default is {@value #DEFAULT_SEGMENT_COUNT}
     */
    private int count = DEFAULT_SEGMENT_COUNT;
    /**
     * Whether the configured segment count represents the number of
     * duplicated segments for this filter to be considered a match.
     */
    private boolean duplicate;
    /**
     * Segment separator. Default is
     * {@value #DEFAULT_SEGMENT_SEPARATOR_PATTERN}.
     */
    private String separator = DEFAULT_SEGMENT_SEPARATOR_PATTERN;

    /**
     * Action to undertake when there is a match.
     */
    private OnMatch onMatch;
}
