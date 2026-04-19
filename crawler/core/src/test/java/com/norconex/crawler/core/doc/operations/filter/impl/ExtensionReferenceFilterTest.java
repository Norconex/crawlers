/* Copyright 2025-2026 Norconex Inc.
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

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.doc.operations.filter.OnMatch;

@Timeout(30)
class ExtensionReferenceFilterTest {

    @Test
    void include_matchingExtension_returnsTrue() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("html", "htm"))
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isTrue();
    }

    @Test
    void include_nonMatchingExtension_returnsFalse() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("html"))
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("http://example.com/doc.pdf"))
                .isFalse();
    }

    @Test
    void exclude_matchingExtension_returnsFalse() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("pdf"))
                .setOnMatch(OnMatch.EXCLUDE);

        assertThat(filter.acceptReference("http://example.com/doc.pdf"))
                .isFalse();
    }

    @Test
    void exclude_nonMatchingExtension_returnsTrue() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("pdf"))
                .setOnMatch(OnMatch.EXCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isTrue();
    }

    @Test
    void ignoreCase_matchesCaseInsensitively() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("HTML"))
                .setIgnoreCase(true)
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isTrue();
    }

    @Test
    void caseSensitive_doesNotMatchDifferentCase() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("HTML"))
                .setIgnoreCase(false)
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isFalse();
    }

    @Test
    void emptyExtensions_withInclude_returnsTrue() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration().setOnMatch(OnMatch.INCLUDE);
        // no extensions set → INCLUDE default

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isTrue();
    }

    @Test
    void emptyExtensions_withExclude_returnsFalse() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration().setOnMatch(OnMatch.EXCLUDE);

        assertThat(filter.acceptReference("http://example.com/page.html"))
                .isFalse();
    }

    @Test
    void nonUrlReference_extractsExtensionFromPath() {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration()
                .setExtensions(Set.of("txt"))
                .setOnMatch(OnMatch.INCLUDE);

        assertThat(filter.acceptReference("/some/local/file.txt"))
                .isTrue();
    }
}
