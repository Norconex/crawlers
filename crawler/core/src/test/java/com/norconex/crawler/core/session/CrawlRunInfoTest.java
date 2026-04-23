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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for {@link CrawlRunInfo}.
 */
@Timeout(30)
class CrawlRunInfoTest {

    @Test
    void testBuilder_allFields() {
        var info = CrawlRunInfo.builder()
                .crawlerId("my-crawler")
                .crawlSessionId("cs-123")
                .crawlRunId("cr-456")
                .crawlMode(CrawlMode.FULL)
                .crawlResumeState(CrawlResumeState.NEW)
                .build();

        assertThat(info.getCrawlerId()).isEqualTo("my-crawler");
        assertThat(info.getCrawlSessionId()).isEqualTo("cs-123");
        assertThat(info.getCrawlRunId()).isEqualTo("cr-456");
        assertThat(info.getCrawlMode()).isEqualTo(CrawlMode.FULL);
        assertThat(info.getCrawlResumeState()).isEqualTo(CrawlResumeState.NEW);
    }

    @Test
    void testBuilder_incrementalResumed() {
        var info = CrawlRunInfo.builder()
                .crawlerId("c2")
                .crawlSessionId("cs-789")
                .crawlRunId("cr-abc")
                .crawlMode(CrawlMode.INCREMENTAL)
                .crawlResumeState(CrawlResumeState.RESUMED)
                .build();

        assertThat(info.getCrawlMode()).isEqualTo(CrawlMode.INCREMENTAL);
        assertThat(info.getCrawlResumeState())
                .isEqualTo(CrawlResumeState.RESUMED);
    }

    @Test
    void testValueEquality() {
        var a = CrawlRunInfo.builder()
                .crawlerId("c")
                .crawlSessionId("cs-1")
                .crawlRunId("cr-1")
                .crawlMode(CrawlMode.FULL)
                .crawlResumeState(CrawlResumeState.NEW)
                .build();
        var b = CrawlRunInfo.builder()
                .crawlerId("c")
                .crawlSessionId("cs-1")
                .crawlRunId("cr-1")
                .crawlMode(CrawlMode.FULL)
                .crawlResumeState(CrawlResumeState.NEW)
                .build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testToString_containsKeyFields() {
        var info = CrawlRunInfo.builder()
                .crawlerId("my-crawler")
                .crawlSessionId("cs-999")
                .crawlRunId("cr-888")
                .crawlMode(CrawlMode.FULL)
                .crawlResumeState(CrawlResumeState.NEW)
                .build();
        var s = info.toString();
        assertThat(s).contains("my-crawler");
        assertThat(s).contains("cs-999");
        assertThat(s).contains("cr-888");
    }

    @Test
    void testCrawlMode_enumValues() {
        assertThat(CrawlMode.values())
                .containsExactlyInAnyOrder(CrawlMode.FULL,
                        CrawlMode.INCREMENTAL);
    }

    @Test
    void testCrawlResumeState_enumValues() {
        assertThat(CrawlResumeState.values())
                .containsExactlyInAnyOrder(
                        CrawlResumeState.NEW, CrawlResumeState.RESUMED);
    }
}
