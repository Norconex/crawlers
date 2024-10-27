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
package com.norconex.crawler.core.tasks.crawl.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.mocks.crawler.MockCrawler;
import com.norconex.crawler.core.stubs.CrawlDocStubs;
import com.norconex.crawler.core.tasks.crawl.pipelines.committer.CommitterPipelineContext;

class CommitModuleStageTest {

    @Test
    void testCommitModuleStage(@TempDir Path tempDir) {
        var ctx = new CommitterPipelineContext(
                MockCrawler.memoryCrawler(tempDir).getContext(),
                CrawlDocStubs.crawlDoc("ref"));
        assertThatNoException().isThrownBy(
                () -> new CommitModuleStage().test(ctx));
    }
}
