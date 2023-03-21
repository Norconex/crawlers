/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.fetch;

import static com.norconex.crawler.core.fetch.FetchDirectiveSupport.DISABLED;
import static com.norconex.crawler.core.fetch.FetchDirectiveSupport.OPTIONAL;
import static com.norconex.crawler.core.fetch.FetchDirectiveSupport.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FetchDirectiveSupportTest {

    @Test
    void testFetchDirectiveSupport() {
        assertThat(FetchDirectiveSupport.isEnabled(DISABLED)).isFalse();
        assertThat(FetchDirectiveSupport.isEnabled(OPTIONAL)).isTrue();
        assertThat(FetchDirectiveSupport.isEnabled(REQUIRED)).isTrue();
        assertThat(FetchDirectiveSupport.isEnabled(null)).isFalse();

        assertThat(DISABLED.is(DISABLED)).isTrue();
        assertThat(DISABLED.is(REQUIRED)).isFalse();

        assertThat(DISABLED.is(null)).isTrue();
        assertThat(OPTIONAL.is(null)).isFalse();
    }
}
