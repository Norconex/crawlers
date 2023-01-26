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
package com.norconex.crawler.core.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.MockQueueInitializer;
import com.norconex.crawler.core.TestUtil;

class CrawlProgressLoggerTest {

    @TempDir
    private Path tempDir;

    @Test
    void testProgressLogger() {
        var mem = TestUtil.runSingleCrawler(
                tempDir,
                cfg -> {
                    cfg.setNumThreads(2);
                    cfg.setMinProgressLoggingInterval(Duration.ofMillis(10));
                },
                implBuilder -> {
                    var mqi = new MockQueueInitializer("ref1", "ref2", "ref3");
                    mqi.setAsync(true);
                    mqi.setDelay(500);
                    implBuilder.queueInitializer(mqi);
                });
        assertThat(mem.getUpsertCount()).isEqualTo(3);
    }
}
