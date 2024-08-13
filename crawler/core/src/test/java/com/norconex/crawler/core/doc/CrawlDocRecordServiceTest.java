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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.TestUtil;
import com.norconex.crawler.core.doc.CrawlDocRecord.Stage;

class CrawlDocRecordServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void testCleanCrawl() {
        TestUtil.withinInitializedCrawler(tempDir, crawler -> {
            var service = crawler.getDocRecordService();
            service.reset(false);
            // forEachXXX returns true by default when there are no matches
            // we use this here to figure out emptiness for all stages
            assertThat(service.forEachActive((s, r) -> false)).isTrue();
            assertThat(service.forEachCached((s, r) -> false)).isTrue();
            assertThat(service.forEachProcessed((s, r) -> false)).isTrue();
            assertThat(service.forEachQueued((s, r) -> false)).isTrue();
            service.close();
        });
    }

    @Test
    void testIncrementalCrawl() {
        TestUtil.withinInitializedCrawler(tempDir, crawler -> {
            var service = crawler.getDocRecordService();
            service.reset(false);
            service.processed(CoreStubber.crawlDocRecord("ref1"));
            service.close();

            service.open();
            service.reset(false);

            assertThat(service.getActiveCount()).isZero();
            assertThat(service.getProcessedCount()).isZero();
            assertThat(service.getCached("ref1")).isPresent();
            assertThat(service.getProcessingStage("ref1")).isNull();
            service.close();
        });
    }

    @Test
    void testResumeCrawl() {
        TestUtil.withinInitializedCrawler(tempDir, crawler -> {
            var service = crawler.getDocRecordService();
            service.reset(false);
            service.queue(CoreStubber.crawlDocRecord("q-ref"));
            service.processed(CoreStubber.crawlDocRecord("p-ref"));
            service.close();

            service.open();
            service.reset(true);

            assertThat(service.getActiveCount()).isZero();
            assertThat(service.getProcessedCount()).isOne();
            assertThat(service.getProcessed("p-ref")).isPresent();
            assertThat(service.getProcessingStage("p-ref")).isSameAs(
                    Stage.PROCESSED);
            assertThat(service.isProcessedEmpty()).isFalse();
            assertThat(service.forEachCached((s, r) -> false)).isTrue();
            assertThat(service.getProcessingStage("q-ref")).isSameAs(
                    Stage.QUEUED);
            assertThat(service.getQueueCount()).isOne();
            assertThat(service.pollQueue()).isPresent();
            service.close();
        });
    }
}
