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
package com.norconex.crawler.core.doc.operations.filter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class GenericReferenceFilterTest {

    @Test
    void testGenericReferenceFilter() {
        var f = new GenericReferenceFilter();
        f.getConfiguration().setValueMatcher(TextMatcher.regex(".*blah.*"));
        assertThat(f.getConfiguration().getValueMatcher())
                .isEqualTo(TextMatcher.regex(".*blah.*"));

        var doc1 = CrawlDocStubs.crawlDoc("http://blah.com", "content");
        assertThat(f.acceptDocument(doc1)).isTrue();
        assertThat(
                f.acceptMetadata(
                        doc1.getReference(), doc1.getMetadata())).isTrue();

        var doc2 = CrawlDocStubs.crawlDoc("http://asdf.com", "content");
        assertThat(f.acceptDocument(doc2)).isFalse();
        assertThat(
                f.acceptMetadata(
                        doc2.getReference(), doc2.getMetadata())).isFalse();

        // a blank expression means a match
        f = new GenericReferenceFilter();
        f.getConfiguration().setValueMatcher(TextMatcher.basic(""));
        assertThat(f.acceptReference(null)).isTrue();
    }

    @Test
    void testCaseSensitivity() {
        var f = new GenericReferenceFilter();
        f.getConfiguration().setOnMatch(OnMatch.INCLUDE);

        // must match any case:
        f.getConfiguration()
                .setValueMatcher(TextMatcher.regex("case").setIgnoreCase(true));
        assertTrue(f.acceptReference("case"));
        assertTrue(f.acceptReference("CASE"));

        // must match only matching case:
        f.getConfiguration()
                .setValueMatcher(
                        TextMatcher.regex("case").setIgnoreCase(false));
        assertTrue(f.acceptReference("case"));
        assertFalse(f.acceptReference("CASE"));
    }

    @Test
    void testWriteRead() {
        var f = new GenericReferenceFilter();
        f.getConfiguration()
                .setValueMatcher(TextMatcher.regex(".*blah.*"))
                .setOnMatch(OnMatch.EXCLUDE);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(f));
    }
}
