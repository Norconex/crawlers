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
package com.norconex.crawler.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebTest {

    // --- trimAroundSubString ---

    @Test
    void testTrimAroundSubString_nullInputs() {
        assertThat(Web.trimAroundSubString(null, "x")).isNull();
        assertThat(Web.trimAroundSubString("hello world", null))
                .isEqualTo("hello world");
    }

    @Test
    void testTrimAroundSubString_removesWhitespaceAroundSubstring() {
        assertThat(Web.trimAroundSubString("hello  ,  world", ","))
                .isEqualTo("hello,world");
        assertThat(Web.trimAroundSubString("a  =  b", "=")).isEqualTo("a=b");
    }

    @Test
    void testTrimAroundSubString_noWhitespace() {
        assertThat(Web.trimAroundSubString("hello,world", ","))
                .isEqualTo("hello,world");
    }

    // --- trimBeforeSubString ---

    @Test
    void testTrimBeforeSubString_nullInputs() {
        assertThat(Web.trimBeforeSubString(null, "x")).isNull();
        assertThat(Web.trimBeforeSubString("hello world", null))
                .isEqualTo("hello world");
    }

    @Test
    void testTrimBeforeSubString_removesWhitespaceBefore() {
        assertThat(Web.trimBeforeSubString("hello  ,world", ","))
                .isEqualTo("hello,world");
        assertThat(Web.trimBeforeSubString("a  =b", "=")).isEqualTo("a=b");
    }

    @Test
    void testTrimBeforeSubString_preservesWhitespaceAfter() {
        // Whitespace after the substring should be kept
        assertThat(Web.trimBeforeSubString("hello  ,  world", ","))
                .isEqualTo("hello,  world");
    }

    // --- trimAfterSubString ---

    @Test
    void testTrimAfterSubString_nullInputs() {
        assertThat(Web.trimAfterSubString(null, "x")).isNull();
        assertThat(Web.trimAfterSubString("hello world", null))
                .isEqualTo("hello world");
    }

    @Test
    void testTrimAfterSubString_removesWhitespaceAfter() {
        // "hello,  world" → removes 2 spaces after comma → "hello,world"
        assertThat(Web.trimAfterSubString("hello,  world", ","))
                .isEqualTo("hello,world");
        assertThat(Web.trimAfterSubString("value=  result", "="))
                .isEqualTo("value=result");
    }

    @Test
    void testTrimAfterSubString_preservesWhitespaceBefore() {
        assertThat(Web.trimAfterSubString("hello  ,  world", ","))
                .isEqualTo("hello  ,world");
    }

    // --- parseDomAttributes ---

    @Test
    void testParseDomAttributes_nullOrBlankReturnsEmpty() {
        assertThat(Web.parseDomAttributes(null).isEmpty()).isTrue();
        assertThat(Web.parseDomAttributes("").isEmpty()).isTrue();
        assertThat(Web.parseDomAttributes("   ").isEmpty()).isTrue();
    }

    @Test
    void testParseDomAttributes_singleDoubleQuotedAttribute() {
        var attrs = Web.parseDomAttributes("href=\"http://example.com\"");
        assertThat(attrs.getString("href")).isEqualTo("http://example.com");
    }

    @Test
    void testParseDomAttributes_singleSingleQuotedAttribute() {
        var attrs = Web.parseDomAttributes("class='my-class'");
        assertThat(attrs.getString("class")).isEqualTo("my-class");
    }

    @Test
    void testParseDomAttributes_multipleAttributes() {
        var attrs = Web.parseDomAttributes(
                "href=\"http://example.com\" title=\"A Title\" class=\"link\"");
        assertThat(attrs.getString("href")).isEqualTo("http://example.com");
        assertThat(attrs.getString("title")).isEqualTo("A Title");
        assertThat(attrs.getString("class")).isEqualTo("link");
    }

    @Test
    void testParseDomAttributes_extractsFromMarkup() {
        // Simulate being passed actual markup; should pick up first element's attrs
        var attrs = Web.parseDomAttributes(
                "<a href=\"http://example.com\" rel=\"nofollow\">link text</a>");
        assertThat(attrs.getString("href")).isEqualTo("http://example.com");
        assertThat(attrs.getString("rel")).isEqualTo("nofollow");
    }

    @Test
    void testParseDomAttributes_caseInsensitiveOption() {
        var attrs = Web.parseDomAttributes("HREF=\"http://example.com\"", true);
        // Case-insensitive Properties should find "href" regardless of case
        assertThat(attrs.getString("href")).isEqualTo("http://example.com");
        assertThat(attrs.getString("HREF")).isEqualTo("http://example.com");
    }
}
