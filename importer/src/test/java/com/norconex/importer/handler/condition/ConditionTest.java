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
package com.norconex.importer.handler.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConditionTest {

    @Test
    void testAnyOf() {
        assertThat(new AnyOf(List.of(
                doc -> false,
                doc -> true,
                doc -> false)).test(null)).isTrue();
        assertThat(new AnyOf(List.of(
                doc -> false,
                doc -> false,
                doc -> false)).test(null)).isFalse();
        assertThat(new AnyOf(List.of(
                doc -> true,
                doc -> true,
                doc -> true)).test(null)).isTrue();
    }

    @Test
    void testAllOf() {
        assertThat(new AllOf(List.of(
                doc -> false,
                doc -> true,
                doc -> false)).test(null)).isFalse();
        assertThat(new AllOf(List.of(
                doc -> false,
                doc -> false,
                doc -> false)).test(null)).isFalse();
        assertThat(new AllOf(List.of(
                doc -> true,
                doc -> true,
                doc -> true)).test(null)).isTrue();
    }

    @Test
    void testNoneOf() {
        assertThat(new NoneOf(List.of(
                doc -> false,
                doc -> true,
                doc -> false)).test(null)).isFalse();
        assertThat(new NoneOf(List.of(
                doc -> false,
                doc -> false,
                doc -> false)).test(null)).isTrue();
        assertThat(new NoneOf(List.of(
                doc -> true,
                doc -> true,
                doc -> true)).test(null)).isFalse();
    }
}
