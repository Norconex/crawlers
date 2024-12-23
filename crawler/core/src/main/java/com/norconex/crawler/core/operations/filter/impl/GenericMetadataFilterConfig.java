/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.core.operations.filter.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.operations.filter.OnMatch;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Accepts or rejects a reference based on whether one or more
 * metadata field values are matching.
 * </p>
 */
@Data
@Accessors(chain = true)
public class GenericMetadataFilterConfig {

    private OnMatch onMatch;
    private final TextMatcher fieldMatcher = new TextMatcher();
    private final TextMatcher valueMatcher = new TextMatcher();

    /**
     * Gets the field matcher.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }

    /**
     * Sets the field matcher.
     * @param fieldMatcher field matcher
     * @return this instance
     */
    public GenericMetadataFilterConfig setFieldMatcher(
            TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * Gets the value matcher.
     * @return value matcher
     */
    public TextMatcher getValueMatcher() {
        return valueMatcher;
    }

    /**
     * Sets the value matcher.
     * @param valueMatcher value matcher
     * @return this instance
     */
    public GenericMetadataFilterConfig setValueMatcher(
            TextMatcher valueMatcher) {
        this.valueMatcher.copyFrom(valueMatcher);
        return this;
    }
}
