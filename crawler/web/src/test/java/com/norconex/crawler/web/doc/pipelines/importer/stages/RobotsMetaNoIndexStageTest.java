/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.doc.WebCrawlEntry;
import com.norconex.crawler.web.doc.operations.robot.RobotsMeta;
import com.norconex.crawler.web.doc.pipelines.importer.WebImporterPipelineContext;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

@Timeout(30)
class RobotsMetaNoIndexStageTest {

    @Test
    void testExecuteStage(@TempDir Path tempDir) {
        var crawlSession = mock(CrawlSession.class);
        var entry = new WebCrawlEntry("someRef");
        var docCtx = CrawlDocContext.builder()
                .doc(CrawlDocStubs.crawlDoc("someRef"))
                .currentCrawlEntry(entry)
                .build();
        var pipeCtx = spy(new WebImporterPipelineContext(crawlSession, docCtx));
        var stage = new RobotsMetaNoIndexStage();

        when(pipeCtx.getRobotsMeta()).thenReturn(null);
        assertThat(stage.executeStage(pipeCtx)).isTrue();

        when(pipeCtx.getRobotsMeta()).thenReturn(new RobotsMeta(false, false));
        assertThat(stage.executeStage(pipeCtx)).isTrue();

        when(pipeCtx.getRobotsMeta()).thenReturn(new RobotsMeta(false, true));
        assertThat(stage.executeStage(pipeCtx)).isFalse();
    }
}
