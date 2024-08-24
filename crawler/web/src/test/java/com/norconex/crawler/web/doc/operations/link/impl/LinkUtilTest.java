/* Copyright 2024 Norconex Inc.
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


package com.norconex.crawler.web.doc.operations.link.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkUtilTest {

    @Test
    void extractHttpEquivRefreshContentUrl_noUrl_returnsEmptyString() {
        //setup
        var content = "3;url=https://www.norconex.com";

        //execute
        var actual = LinkUtil.extractHttpEquivRefreshContentUrl(content);

        //verify
        assertThat(actual).isEqualTo("https://www.norconex.com");

    }
}