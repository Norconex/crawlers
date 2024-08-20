/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.filter.impl;

import java.util.regex.Pattern;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;

import lombok.Data;
import lombok.experimental.Accessors;
/**
 * <p>
 * Filters URL based on a matching expression.
 * </p>
 *
 * {@nx.xml.usage
 * <filter class="com.norconex.crawler.core.filter.impl.GenericReferenceFilter"
 *     onMatch="[include|exclude]">
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Expression matching the document reference.)
 *   </valueMatcher>
 * </filter>
 * }
 *
 * {@nx.xml.example
 * <filter class="GenericReferenceFilter" onMatch="exclude">
 *   <valueMatcher method="regex">.*&#47;login/.*</valueMatcher>
 * </filter>
 * }
 * <p>
 * The above will reject documents having "/login/" in their reference.
 * </p>
 * @see Pattern
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class GenericReferenceFilterConfig {

    private OnMatch onMatch;
    private final TextMatcher valueMatcher = new TextMatcher();

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
    public GenericReferenceFilterConfig setValueMatcher(
            TextMatcher valueMatcher) {
        this.valueMatcher.copyFrom(valueMatcher);
        return this;
    }
}

