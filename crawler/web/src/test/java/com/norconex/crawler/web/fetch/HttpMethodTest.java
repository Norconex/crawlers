/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpMethodTest {

    @Test
    void testHttpMethod() {
        assertThat(HttpMethod.POST.is(HttpMethod.POST)).isTrue();
        assertThat(HttpMethod.GET.is(HttpMethod.HEAD)).isFalse();
        assertThat(
                HttpMethod.POST.isAny(
                        HttpMethod.POST, HttpMethod.HEAD)).isTrue();
        assertThat(
                HttpMethod.POST.isAny(
                        HttpMethod.GET, HttpMethod.HEAD)).isFalse();
        assertThat(HttpMethod.POST.isAny()).isFalse();
    }
}
