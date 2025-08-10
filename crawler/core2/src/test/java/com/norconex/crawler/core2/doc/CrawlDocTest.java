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
package com.norconex.crawler.core.doc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedInputStream;

class CrawlDocTest {

    @Test
    void testCrawlDoc() {
        var rec = new CrawlDocContext("ref");
        var doc = new CrawlDoc(rec);
        assertThat(doc.hasCache()).isFalse();

        var cachedRec = new CrawlDocContext("ref");
        doc = new CrawlDoc(rec, cachedRec, CachedInputStream.nullInputStream());
        assertThat(doc.hasCache()).isTrue();
    }
}
