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
package com.norconex.crawler.core.doc;

import static com.norconex.crawler.core.doc.CrawlDocRecord.Stage.ACTIVE;
import static com.norconex.crawler.core.doc.CrawlDocRecord.Stage.PROCESSED;
import static com.norconex.crawler.core.doc.CrawlDocRecord.Stage.QUEUED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrawlDocRecordTest {

    @Test
    void testStages() {
        assertThat(ACTIVE.is(null)).isFalse();
        assertThat(ACTIVE.is(ACTIVE)).isTrue();
        assertThat(ACTIVE.is(PROCESSED)).isFalse();
        assertThat(ACTIVE.is(QUEUED)).isFalse();
    }
}
