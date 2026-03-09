/* Copyright 2025 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;

@Timeout(30)
class GenericReferenceFilterTest {

    @Test
    void include_matchingReference_returnsTrue() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration()
                .setValueMatcher(TextMatcher.regex(".*\\.html"))
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isTrue();
    }

    @Test
    void include_nonMatchingReference_returnsFalse() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration()
                .setValueMatcher(TextMatcher.regex(".*\\.html"))
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.pdf"))
                .isFalse();
    }

    @Test
    void exclude_matchingReference_returnsFalse() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration()
                .setValueMatcher(TextMatcher.regex(".*\\.pdf"))
                .setOnMatch(OnMatch.EXCLUDE);

        assertThat(filter.acceptReference("http://example.com/doc.pdf"))
                .isFalse();
    }

    @Test
    void exclude_nonMatchingReference_returnsTrue() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration()
                .setValueMatcher(TextMatcher.regex(".*\\.pdf"))
                .setOnMatch(OnMatch.EXCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isTrue();
    }

    @Test
    void blankPattern_withInclude_returnsTrue() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration().setOnMatch(OnMatch.INCLUDE);
        // pattern is blank → isInclude == true

        assertThat(filter.acceptReference("http://example.com/anything"))
                .isTrue();
    }

    @Test
    void blankPattern_withExclude_returnsFalse() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration().setOnMatch(OnMatch.EXCLUDE);
        // pattern is blank → isInclude == false

        assertThat(filter.acceptReference("http://example.com/anything"))
                .isFalse();
    }

    @Test
    void defaultOnMatch_isInclude() {
        var filter = new GenericReferenceFilter();
        // no OnMatch set; should default to INCLUDE
        assertThat(filter.getOnMatch()).isEqualTo(OnMatch.INCLUDE);
    }

    @Test
    void acceptMetadata_delegatesToAcceptReference() {
        var filter = new GenericReferenceFilter();
        filter.getConfiguration()
                .setValueMatcher(TextMatcher.basic("http://example.com/page"))
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptMetadata(
                "http://example.com/page",
                new com.norconex.commons.lang.map.Properties()))
                        .isTrue();
    }
}
