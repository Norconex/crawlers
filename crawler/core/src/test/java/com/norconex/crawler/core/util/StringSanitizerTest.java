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
package com.norconex.crawler.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringSanitizerTest {

    @Test
    void default_keepsAlphaNumericUnderscoreHyphen() {
        assertThat(StringSanitizer.DEFAULT.sanitize("hello-world_123!@#"))
                .isEqualTo("hello-world_123");
    }

    @Test
    void alphaOnly_removesNonAlpha() {
        var sanitizer = StringSanitizer.builder().alpha().build();
        assertThat(sanitizer.sanitize("abc123-_!")).isEqualTo("abc");
    }

    @Test
    void numericOnly_removesNonNumeric() {
        var sanitizer = StringSanitizer.builder().numeric().build();
        assertThat(sanitizer.sanitize("abc123-_!")).isEqualTo("123");
    }

    @Test
    void underscoreOnly_removesNonUnderscore() {
        var sanitizer = StringSanitizer.builder().underscore().build();
        assertThat(sanitizer.sanitize("hello_world-123")).isEqualTo("_");
    }

    @Test
    void hyphenOnly_removesNonHyphen() {
        var sanitizer = StringSanitizer.builder().hypen().build();
        assertThat(sanitizer.sanitize("hello-world_123")).isEqualTo("-");
    }

    @Test
    void alphaAndNumeric_removeSpecialChars() {
        var sanitizer = StringSanitizer.builder().alpha().numeric().build();
        assertThat(sanitizer.sanitize("test-123_!")).isEqualTo("test123");
    }

    @Test
    void onlyUnderscore_removesNonUnderscore() {
        var sanitizer = StringSanitizer.builder().underscore().build();
        assertThat(sanitizer.sanitize("hello-world_123!")).isEqualTo("_");
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(StringSanitizer.DEFAULT.sanitize("")).isEmpty();
    }

    @Test
    void inputAlreadyClean_returnsUnchanged() {
        assertThat(StringSanitizer.DEFAULT.sanitize("valid-name_123"))
                .isEqualTo("valid-name_123");
    }
}
