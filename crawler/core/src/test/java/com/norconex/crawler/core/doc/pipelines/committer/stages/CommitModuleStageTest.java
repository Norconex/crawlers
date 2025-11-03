/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.committer.stages;

import static org.assertj.core.api.Assertions.assertThatNoException;

import com.norconex.crawler.core._DELETE.CrawlTest;
import com.norconex.crawler.core._DELETE.CrawlTest.Focus;
import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stubs.CrawlDocContextStubber;

class CommitModuleStageTest {

    @CrawlTest(focus = Focus.SESSION)
    void testCommitModuleStage(CrawlSession session) {
        var ctx = new CommitterPipelineContext(
                session, CrawlDocContextStubber.fresh("ref"));
        assertThatNoException().isThrownBy(
                () -> new CommitModuleStage().test(ctx));
    }
}
