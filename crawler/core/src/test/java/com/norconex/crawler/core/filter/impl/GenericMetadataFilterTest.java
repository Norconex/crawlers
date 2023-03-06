/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.core.filter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.CoreStubber;
import com.norconex.importer.handler.filter.OnMatch;

class GenericMetadataFilterTest {

    @Test
    void testGenericMetadataFilter() {
        var f = new GenericMetadataFilter(
                TextMatcher.basic("field1"), TextMatcher.basic("value1"));
        f.setOnMatch(OnMatch.INCLUDE);

        var doc1 = CoreStubber.crawlDoc("ref", "blah", "field1", "value1");
        assertThat(f.acceptDocument(doc1)).isTrue();
        assertThat(f.acceptMetadata(
                doc1.getReference(), doc1.getMetadata())).isTrue();

        var doc2 = CoreStubber.crawlDoc("ref", "blah", "field2", "value2");
        assertThat(f.acceptDocument(doc2)).isFalse();
        assertThat(f.acceptMetadata(
                doc2.getReference(), doc2.getMetadata())).isFalse();

        // null documents are considered a match
        assertThat(f.acceptDocument(null)).isTrue();
    }

    @Test
    void testWriteRead() {
        var f = new GenericMetadataFilter();
        f.setOnMatch(OnMatch.EXCLUDE);
        f.setFieldMatcher(TextMatcher.basic("title"));
        f.setValueMatcher(TextMatcher.regex(".*blah.*"));
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(f, "filter"));
    }
}
