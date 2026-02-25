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

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.importer.doc.Doc;

/**
 * Tests for {@link GenericMetadataFilter} and
 * {@link GenericMetadataFilterConfig}.
 */
class GenericMetadataFilterTest {

    // -----------------------------------------------------------------
    // acceptMetadata — blank pattern shortcuts
    // -----------------------------------------------------------------

    @Test
    void blankFieldMatcherPattern_returnsInclude() {
        var filter = new GenericMetadataFilter();
        // fieldMatcher has blank pattern → short-circuits to INCLUDE by default
        var result = filter.acceptMetadata("ref", new Properties());
        assertThat(result).isTrue(); // onMatch defaults to INCLUDE via includeIfNull
    }

    @Test
    void blankValueMatcherPattern_returnsInclude() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration().setFieldMatcher(TextMatcher.basic("myField"));
        // valueMatcher still blank → short-circuits to INCLUDE
        var result = filter.acceptMetadata("ref", new Properties());
        assertThat(result).isTrue();
    }

    // -----------------------------------------------------------------
    // acceptMetadata — property matching
    // -----------------------------------------------------------------

    @Test
    void propertyMatches_onMatchInclude_returnsTrue() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("color"))
                .setValueMatcher(TextMatcher.basic("blue"))
                .setOnMatch(OnMatch.INCLUDE);

        var meta = new Properties();
        meta.add("color", "blue");
        assertThat(filter.acceptMetadata("ref", meta)).isTrue();
    }

    @Test
    void propertyMatches_onMatchExclude_returnsFalse() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("color"))
                .setValueMatcher(TextMatcher.basic("blue"))
                .setOnMatch(OnMatch.EXCLUDE);

        var meta = new Properties();
        meta.add("color", "blue");
        assertThat(filter.acceptMetadata("ref", meta)).isFalse();
    }

    @Test
    void propertyDoesNotMatch_onMatchInclude_returnsFalse() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("color"))
                .setValueMatcher(TextMatcher.basic("blue"))
                .setOnMatch(OnMatch.INCLUDE);

        var meta = new Properties();
        meta.add("color", "red"); // doesn't match "blue"
        assertThat(filter.acceptMetadata("ref", meta)).isFalse();
    }

    @Test
    void propertyDoesNotMatch_onMatchExclude_returnsTrue() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("color"))
                .setValueMatcher(TextMatcher.basic("blue"))
                .setOnMatch(OnMatch.EXCLUDE);

        var meta = new Properties();
        meta.add("color", "red"); // doesn't match "blue"
        assertThat(filter.acceptMetadata("ref", meta)).isTrue();
    }

    // -----------------------------------------------------------------
    // acceptDocument
    // -----------------------------------------------------------------

    @Test
    void acceptDocument_nullDoc_returnsInclude() {
        var filter = new GenericMetadataFilter();
        assertThat(filter.acceptDocument(null)).isTrue();
    }

    @Test
    void acceptDocument_nullDoc_onMatchExclude_returnsFalse() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration().setOnMatch(OnMatch.EXCLUDE);
        assertThat(filter.acceptDocument(null)).isFalse();
    }

    @Test
    void acceptDocument_delegatesToAcceptMetadata() {
        var filter = new GenericMetadataFilter();
        filter.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("status"))
                .setValueMatcher(TextMatcher.basic("active"))
                .setOnMatch(OnMatch.INCLUDE);

        var doc = new Doc("http://example.com");
        doc.getMetadata().add("status", "active");
        assertThat(filter.acceptDocument(doc)).isTrue();
    }

    // -----------------------------------------------------------------
    // GenericMetadataFilterConfig - setters with copyFrom
    // -----------------------------------------------------------------

    @Test
    void config_chainedSetters_setsProperly() {
        var cfg = new GenericMetadataFilterConfig()
                .setFieldMatcher(TextMatcher.basic("field1"))
                .setValueMatcher(TextMatcher.basic("value1"))
                .setOnMatch(OnMatch.EXCLUDE);

        assertThat(cfg.getFieldMatcher().getPattern()).isEqualTo("field1");
        assertThat(cfg.getValueMatcher().getPattern()).isEqualTo("value1");
        assertThat(cfg.getOnMatch()).isEqualTo(OnMatch.EXCLUDE);
    }

    @Test
    void config_defaultOnMatch_isNull() {
        var cfg = new GenericMetadataFilterConfig();
        assertThat(cfg.getOnMatch()).isNull();
    }

    @Test
    void filter_defaultOnMatch_treatedAsInclude() {
        var filter = new GenericMetadataFilter();
        // includeIfNull returns INCLUDE when onMatch is null
        assertThat(filter.getOnMatch()).isEqualTo(OnMatch.INCLUDE);
    }
}
